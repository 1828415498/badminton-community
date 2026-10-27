package com.badmintoncommunity.support;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * 集成测试基类：接好测试库、维护夹具数据。
 *
 * <h2>为什么单独抽一个基类</h2>
 * <p>这些条件每一个用例都需要，写错任何一个都会得到"看起来在测、其实没测对"的结果：
 * 忘了 @ActiveProfiles 会连到开发库并启用定时任务；
 * 忘了清夹具会让第二个用例看到第一个用例留下的行。</p>
 *
 * <h2>关于定时任务（ActivityStatusScheduler）</h2>
 * <p>测试使用 {@code test} profile，活动定时任务通过 {@code @Profile("!test")}
 * 在上下文创建前排除，避免它在 TestSchemaBootstrap 建表期间查询数据库。
 * 到 {@code @BeforeEach} 才取消定时任务已经太晚。</p>
 *
 * <h2>关于事务</h2>
 * <p>本基类<b>刻意不加</b> {@code @Transactional}：并发用例必须让被测方法提交真实事务，
 * 否则多线程互相看不见对方的数据，测的就成了空气。需要自动回滚的纯逻辑用例，
 * 在自己类上加 {@code @Transactional} 即可。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
public abstract class IntegrationTestBase {

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected Fixtures fx;

    @BeforeEach
    void prepareFixtureData() {
        fx.cleanAll();
    }

    @AfterEach
    void clearFixtureData() {
        fx.cleanAll();
    }
}
