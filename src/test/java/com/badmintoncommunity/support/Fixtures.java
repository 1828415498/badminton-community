package com.badmintoncommunity.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * 测试夹具：往测试库造最小可用的数据，并在用例之间清干净。
 *
 * <h2>为什么自己造数据，而不是用开发库那批种子数据</h2>
 * <p>种子数据的日期是写死的，跑两天就全过期 —— 断言会从"验证业务规则"退化成
 * "取决于今天几号"，也就是这个项目已经踩过两次的<b>假红/假绿</b>：
 * 脚本自己红了，看起来像产品坏了，其实只是数据旧了。</p>
 * <p>这里造出来的每一行都<b>只依赖「现在」</b>（明天 + 整点），所以每天跑都是同样的结果。</p>
 *
 * <h2>清理策略：前缀标记，不记 id</h2>
 * <p>所有造出来的行都带 {@value #MARK} 前缀（用户名 / 场馆名 / 球场名 / 活动标题），
 * 清理时按前缀删。<b>不需要在用例里记 id</b>，也就不会因为某个用例中途抛异常而漏删。</p>
 */
@Component
public class Fixtures {

    /** 夹具标记前缀。刻意不含下划线 —— LIKE 里 {@code _} 是单字符通配符，会误伤。 */
    public static final String MARK = "tjtest";

    private final JdbcTemplate jdbc;

    public Fixtures(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ==================== 造数据 ====================

    /** 造一个用户（普通角色）。返回主键。 */
    public long user(String suffix) {
        return insert("INSERT INTO `user` (username, password_hash, nickname, role) VALUES (?, ?, ?, 0)",
                MARK + suffix, "not-a-real-hash", MARK + suffix);
    }

    /** 造一个场馆。 */
    public long venue(String suffix) {
        return insert("INSERT INTO venue (name, address) VALUES (?, ?)",
                MARK + suffix, MARK + " 路 1 号");
    }

    /** 造一块球场。status：1=可预约，2=不可预约。 */
    public long court(long venueId, String suffix, int status) {
        return insert("INSERT INTO court (venue_id, name, status) VALUES (?, ?, ?)",
                venueId, MARK + suffix, status);
    }

    /**
     * 造一个活动。status：0=待审核，1=已发布，2=已驳回，3=已取消，4=已结束。
     *
     * <p>⚠️ 若造 status=1 的活动，调用方必须保证 {@code endTime} 晚于当前时刻，否则
     * ActivityStatusScheduler 会把它的状态刷成 4（该任务按 fixedDelay=60s 跑，
     * 测试基类虽已尝试取消它，但夹具自身也保持这个不变量更稳妥）。</p>
     */
    public long activity(long creatorId, String suffix, LocalDateTime start, LocalDateTime end,
                         int maxMembers, int status) {
        return insert("INSERT INTO activity (creator_id, title, start_time, end_time, max_members, status) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                creatorId, MARK + suffix, start, end, maxMembers, status);
    }

    /** 造一条<b>个人</b>占用行（activity_id 为 NULL）。 */
    public long personalReservation(long courtId, long userId, LocalDateTime start, LocalDateTime end,
                                    int status) {
        return insert("INSERT INTO reservation (court_id, user_id, activity_id, start_time, end_time, status) "
                        + "VALUES (?, ?, NULL, ?, ?, ?)",
                courtId, userId, start, end, status);
    }

    /** 造一条<b>活动</b>占用行（user_id 为 NULL），固定为有效状态。用于验证"统一账本"：活动锁过的场，用户不能再约。 */
    public long activityReservation(long courtId, long activityId, LocalDateTime start,
                                    LocalDateTime end) {
        return insert("INSERT INTO reservation (court_id, user_id, activity_id, start_time, end_time, status) "
                        + "VALUES (?, NULL, ?, ?, ?, 1)",
                courtId, activityId, start, end);
    }

    /** 造一条报名行。status：1=已报名，2=已取消。 */
    public long registration(long userId, long activityId, int status) {
        return insert("INSERT INTO registration (user_id, activity_id, status) VALUES (?, ?, ?)",
                userId, activityId, status);
    }

    // ==================== 查数据（断言用） ====================

    /** 某球场在 [start, end) 内的有效占用行数（无论个人还是活动）。 */
    public long countActiveReservations(long courtId, LocalDateTime start, LocalDateTime end) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM reservation WHERE court_id = ? AND status = 1 "
                        + "AND start_time < ? AND end_time > ?",
                Long.class, courtId, end, start);
        return n == null ? 0 : n;
    }

    /** 某活动的有效报名数。 */
    public long countActiveRegistrations(long activityId) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM registration WHERE activity_id = ? AND status = 1",
                Long.class, activityId);
        return n == null ? 0 : n;
    }

    /** 某用户在某活动下的报名行数（含已取消的历史行）—— 验证"一人一行"用。 */
    public long countRegistrationRows(long userId, long activityId) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM registration WHERE user_id = ? AND activity_id = ?",
                Long.class, userId, activityId);
        return n == null ? 0 : n;
    }

    // ==================== 清理 ====================

    /**
     * 删掉本类造出来的全部数据。
     *
     * <p>删除顺序按外键依赖倒着来：先收拾引用方（报名、占用），再删被引用方
     * （活动、球场、场馆、用户）。占用行有两种归属，所以要从"用户侧"和"活动侧"各删一次。</p>
     */
    public void cleanAll() {
        String like = MARK + "%";
        // 占用行：按归属用户删
        jdbc.update("DELETE r FROM reservation r JOIN `user` u ON u.id = r.user_id WHERE u.username LIKE ?", like);
        // 占用行：按归属活动删
        jdbc.update("DELETE r FROM reservation r JOIN activity a ON a.id = r.activity_id WHERE a.title LIKE ?", like);
        // 占用行：按球场删（个人行与活动行都可能落在测试球场上）
        jdbc.update("DELETE r FROM reservation r JOIN court c ON c.id = r.court_id WHERE c.name LIKE ?", like);
        // 报名行：按活动 / 按用户各删一次
        jdbc.update("DELETE g FROM registration g JOIN activity a ON a.id = g.activity_id WHERE a.title LIKE ?", like);
        jdbc.update("DELETE g FROM registration g JOIN `user` u ON u.id = g.user_id WHERE u.username LIKE ?", like);

        jdbc.update("DELETE FROM activity WHERE title LIKE ?", like);
        jdbc.update("DELETE FROM court WHERE name LIKE ?", like);
        jdbc.update("DELETE FROM venue WHERE name LIKE ?", like);
        jdbc.update("DELETE FROM `user` WHERE username LIKE ?", like);
    }

    // ==================== 内部工具 ====================

    /**
     * 插入并回填自增主键。
     *
     * <p>用 {@link KeyHolder} 而不是"插完再按名称查回来"：测试里同时存在多块球场、
     * 多个用户，靠名称反查一旦有重名就静默拿到别人的 id，排查起来非常费劲。</p>
     */
    private long insert(String sql, Object... args) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                if (arg instanceof LocalDateTime dateTime) {
                    // 显式转 Timestamp：不依赖驱动对 setObject(LocalDateTime) 的支持
                    ps.setTimestamp(i + 1, Timestamp.valueOf(dateTime));
                } else {
                    ps.setObject(i + 1, arg);
                }
            }
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("插入后没拿到自增主键：" + sql);
        }
        return key.longValue();
    }
}
