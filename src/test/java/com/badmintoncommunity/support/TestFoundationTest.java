package com.badmintoncommunity.support;

import com.badmintoncommunity.module.activity.scheduler.ActivityStatusScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测试地基自检。
 *
 * <p>这一组用例不测业务，它测的是<b>"测试本身可不可信"</b>。整套测试的价值都建立在这些条件上，
 * 一旦其中一条悄悄失效，后面所有绿都会变成假绿：</p>
 * <ol>
 *   <li>连的确实是独立测试库（否则在建表第一步就已经把开发库删了）；</li>
 *   <li>表与视图都是按 {@code sql/schema.sql} + {@code sql/views.sql} 建的（否则测的是一个和线上不一样的库）；</li>
 *   <li>夹具造得出、也删得掉（否则用例互相污染，失败原因会指向错误的地方）。</li>
 *   <li>活动定时任务不在测试启动时注册（否则可能在建表期间查询数据库）。</li>
 * </ol>
 */
@DisplayName("测试地基自检")
class TestFoundationTest extends IntegrationTestBase {

    @Autowired
    private ApplicationContext applicationContext;

    private static final Set<String> EXPECTED_TABLES = Set.of(
            "user", "venue", "court", "activity", "reservation",
            "registration", "post", "comment", "post_like");

    /**
     * {@code sql/views.sql} 定义的 4 个视图。
     *
     * <p>视图不是表，但同样必须与开发库一致 —— 生产代码会查它们
     * （例如预约凭证 {@code ReservationMapper.findReceiptById} 查 v_reservation_detail）。
     * 视图缺失时开发环境正常、只有测试报错，属于最难定位的一类问题，所以在这里钉死。</p>
     */
    private static final Set<String> EXPECTED_VIEWS = Set.of(
            "v_reservation_detail", "v_activity_signup_detail",
            "v_post_with_stats", "v_activity_signup_stat");

    @Test
    @DisplayName("测试上下文启动时不注册活动定时任务")
    void activitySchedulerIsAbsentInTestProfile() {
        assertTrue(applicationContext.getBeansOfType(ActivityStatusScheduler.class).isEmpty(),
                "测试环境必须在启动阶段禁用定时任务，@BeforeEach 再取消已太晚");
    }

    @Test
    @DisplayName("连的是独立测试库，不是开发库")
    void runsAgainstDedicatedTestDatabase() {
        String database = jdbc.queryForObject("SELECT DATABASE()", String.class);
        assertTrue(database != null && database.endsWith("_test"),
                "测试库名必须以 _test 结尾，实际为：" + database);
    }

    @Test
    @DisplayName("9 张表都已按 sql/schema.sql 建好")
    void schemaIsApplied() {
        // 只数基表：information_schema.tables 同时也包含视图，
        // 不加 table_type 过滤会把 sql/views.sql 建的 4 个视图也数进来
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables "
                        + "WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE'",
                String.class);
        assertEquals(EXPECTED_TABLES, new HashSet<>(tables),
                "测试库表集合与 database.md 的 9 张表不一致");
    }

    @Test
    @DisplayName("4 个视图都已按 sql/views.sql 建好")
    void viewsAreApplied() {
        List<String> views = jdbc.queryForList(
                "SELECT table_name FROM information_schema.views WHERE table_schema = DATABASE()",
                String.class);
        assertEquals(EXPECTED_VIEWS, new HashSet<>(views),
                "测试库视图集合与 sql/views.sql 不一致；"
                        + "查视图的代码（如预约凭证 findReceiptById）在测试里会因此报错");
    }

    @Test
    @DisplayName("夹具造得出数据，清理后不留残留")
    void fixturesCreateAndClean() {
        long venueId = fx.venue("smoke-venue");
        long courtId = fx.court(venueId, "smoke-court", 1);
        long userId = fx.user("smoke-user");

        assertEquals(1, countVenue("smoke-venue"));
        assertEquals(1, countCourt("smoke-court"));
        assertEquals(1, countUser("smoke-user"));
        assertTrue(courtId > 0 && userId > 0 && venueId > 0);

        fx.cleanAll();

        assertEquals(0, countVenue("smoke-venue"), "清理后不该留下场馆");
        assertEquals(0, countCourt("smoke-court"), "清理后不该留下球场");
        assertEquals(0, countUser("smoke-user"), "清理后不该留下用户");
    }

    private int countVenue(String suffix) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM venue WHERE name = ?", Integer.class,
                Fixtures.MARK + suffix);
        return n == null ? 0 : n;
    }

    private int countCourt(String suffix) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM court WHERE name = ?", Integer.class,
                Fixtures.MARK + suffix);
        return n == null ? 0 : n;
    }

    private int countUser(String suffix) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM `user` WHERE username = ?", Integer.class,
                Fixtures.MARK + suffix);
        return n == null ? 0 : n;
    }
}
