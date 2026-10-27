package com.badmintoncommunity.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * 测试库建表引导：每次测试上下文启动时，把 {@code sql/schema.sql} 应用到测试库。
 *
 * <h2>为什么不是「手工建一个测试库、再手工同步一份 DDL」</h2>
 * <p>那种做法会立刻产生<b>两份 DDL</b>：开发库一份、测试库一份。改了一边忘另一边，
 * 测试就会跑在一个"和线上长得不一样"的表结构上 —— 这是比没有测试更糟的状态（假绿）。
 * 所以这里<b>直接读生产用的那份 schema.sql</b>，只有一处事实源，永远不会漂移。</p>
 *
 * <h2>为什么不用 JPA/Hibernate 自动建表（ddl-auto）</h2>
 * <p>本项目是原生 MyBatis（ADR-0008），没有实体映射层可以反推 DDL。而且自动建表出来的
 * 索引、CHECK 约束、外键与 {@code database.md} 定稿的结构并不等价，测了不算数。</p>
 *
 * <h2>⚠️ 与开发库的隔离（本类存在的最主要原因）</h2>
 * <p>schema.sql 开头有 {@code CREATE DATABASE / USE badminton_community}，直接整段执行会
 * <b>把开发库删了重建</b>。所以本类做两件事：</p>
 * <ol>
 *   <li>执行前把 {@code CREATE DATABASE ...;} 与 {@code USE ...;} 两条库级语句剥掉；</li>
 *   <li>校验当前连接指向的库名必须以 {@code _test} 结尾，不满足直接抛异常中止。</li>
 * </ol>
 * <p>第 2 条是最后一道保险：即使有人把 application-test.yml 的 url 改错，也只是测试报错，
 * 不会静默毁掉开发库。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TestSchemaBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(TestSchemaBootstrap.class);

    /** 允许执行 DDL 的库名后缀。不满足就拒绝运行。 */
    private static final String REQUIRED_DATABASE_SUFFIX = "_test";

    /**
     * 生产 SQL 的唯一事实源（相对项目根目录；Maven Surefire 的工作目录即项目根）。
     *
     * <p><b>顺序有意义</b>：先 schema.sql 建表，再 views.sql 建依赖这些表的视图。
     * 测试库必须和开发库有一样的视图 —— 否则任何查视图的生产代码（例如预约凭证
     * {@code ReservationMapper.findReceiptById}）在测试里都会因为"表不存在"而报错，
     * 而开发环境却是好的，那是最难查的一类问题。</p>
     */
    private static final List<String> SCHEMA_FILES = List.of("sql/schema.sql", "sql/views.sql");

    /**
     * 建表前先删掉的表。
     *
     * <p>顺序不重要 —— 执行时会临时关掉外键检查。{@code user} 加了反引号，
     * 因为它在 MySQL 里是关键字（函数名），不加引号在某些上下文会解析失败。</p>
     */
    private static final List<String> TABLES = List.of(
            "`user`", "venue", "court", "activity", "reservation",
            "registration", "post", "comment", "post_like");

    private final DataSource dataSource;
    private final Environment environment;

    public TestSchemaBootstrap(DataSource dataSource, Environment environment) {
        this.dataSource = dataSource;
        this.environment = environment;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        requireTestDatabaseCredentials();
        try (Connection conn = dataSource.getConnection()) {
            guardAgainstWrongDatabase(conn);

            // schema.sql 先建表，views.sql 再建视图；两份合并成一个执行列表
            List<String> statements = new ArrayList<>();
            for (String file : SCHEMA_FILES) {
                statements.addAll(splitStatements(readScript(file)));
            }

            try (Statement st = conn.createStatement()) {
                // 先清空：schema.sql 用的是裸 CREATE TABLE，表已存在会直接报错。
                // 关掉外键检查才能不按依赖顺序删表。
                // 视图不必单独 DROP：views.sql 用的是 CREATE OR REPLACE VIEW，
                // 而 MySQL 允许删除被视图引用的表（视图会暂时失效，重建表后即可恢复）。
                st.execute("SET FOREIGN_KEY_CHECKS = 0");
                for (String table : TABLES) {
                    st.execute("DROP TABLE IF EXISTS " + table);
                }
                st.execute("SET FOREIGN_KEY_CHECKS = 1");

                for (String sql : statements) {
                    st.execute(sql);
                }
            }
            log.info("测试库已按 {} 重建完成，共执行 {} 条 DDL（库：{}）",
                    String.join(" + ", SCHEMA_FILES), statements.size(), conn.getCatalog());
        }
    }

    /** 缺少测试库凭据时明确失败，避免把未解析的占位符当作 MySQL 用户名去连接。 */
    private void requireTestDatabaseCredentials() {
        String user = environment.getProperty("TEST_DB_USER");
        String password = environment.getProperty("TEST_DB_PASSWORD");
        if (user == null || user.isBlank() || password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "Missing TEST_DB_USER or TEST_DB_PASSWORD. Set both to non-empty values before tests; "
                            + "schema bootstrap recreates tables and views in badminton_community_test.");
        }
    }

    /**
     * 最后一道保险：只在库名以 {@code _test} 结尾时动手。
     *
     * @throws IllegalStateException 库名不满足后缀要求时抛出，宁可测试全红也不能删错库
     */
    private void guardAgainstWrongDatabase(Connection conn) throws SQLException {
        String database = conn.getCatalog();
        if (database == null || !database.endsWith(REQUIRED_DATABASE_SUFFIX)) {
            throw new IllegalStateException(
                    "拒绝在库 [" + database + "] 上执行测试建表（会先 DROP 全部表）。"
                            + "测试库名必须以 " + REQUIRED_DATABASE_SUFFIX + " 结尾。"
                            + "当前 JDBC URL = " + conn.getMetaData().getURL());
        }
    }

    /** 读一份生产 SQL 脚本。找不到就报错并提示当前工作目录，方便定位 Surefire 的 cwd 问题。 */
    private String readScript(String relativePath) throws IOException {
        Path path = Path.of(relativePath);
        if (!Files.exists(path)) {
            throw new IllegalStateException("找不到 " + relativePath
                    + "（当前工作目录：" + Path.of("").toAbsolutePath() + "）");
        }
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    /**
     * 把整段脚本拆成单条语句。
     *
     * <p>两步：先逐行丢掉 {@code --} 注释与空行，再按 {@code ;} 切分。
     * 之所以可以放心按分号切：schema.sql 里分号只出现在语句结尾，字符串字面量只出现在
     * {@code COMMENT '...'} 里且不含分号。这样做比用 {@code allowMultiQueries} 一次发整段
     * 更可控 —— 后者在多语句下要靠循环消费 {@code getMoreResults()}，容易漏执行。</p>
     */
    private List<String> splitStatements(String script) {
        // 剥掉库级语句：这正是"会误伤开发库"的那两条
        String withoutDatabaseStatements = script
                .replaceAll("(?im)^\\s*CREATE\\s+DATABASE[^;]*;", "")
                .replaceAll("(?im)^\\s*USE\\s+[^;]*;", "");

        StringBuilder withoutComments = new StringBuilder();
        for (String line : withoutDatabaseStatements.split("\n")) {
            String trimmed = line.strip();
            if (trimmed.isEmpty() || trimmed.startsWith("--")) {
                continue;
            }
            withoutComments.append(line).append('\n');
        }

        List<String> statements = new ArrayList<>();
        for (String part : withoutComments.toString().split(";")) {
            if (!part.isBlank()) {
                statements.add(part.strip());
            }
        }
        return statements;
    }
}
