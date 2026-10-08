-- ============================================================
-- 羽毛球社区管理系统 —— 视图定义
-- 依据：docs/database.md（第 4/5/6 节）、sql/schema.sql
-- 环境：MySQL 8.0.16+
-- 用法：先执行 schema.sql 建表，再执行本文件；本文件可重复执行
-- 说明：视图不存数据，只保存查询定义；本文件【不修改任何表结构】
--       请在 badminton_community 库中执行（或先 USE badminton_community;）
-- ============================================================

-- ------------------------------------------------------------
-- 字符集：显式设置连接字符集（不要删这一行）
-- ------------------------------------------------------------
-- 背景（真实踩过的坑）：
--   CREATE VIEW 定义里的字符串字面量（如 '个人预约'、'有效'）会按
--   【执行时的连接字符集】登记成列的字符集。若客户端未指定字符集，
--   Windows 上的 mysql 客户端默认常是 gbk —— 那这两列就被登记成
--   gbk_chinese_ci，而实际存的字节是 UTF-8。此后任何一次字符集转换
--   （gbk → utf8mb4）都会把值转成乱码。
--   典型症状：基表数据（场馆名、昵称）全部正常，只有视图里用 CASE 拼出来的
--             来源 / 状态两列是乱码 —— 因为它们是字面量，不走基表。
--   排查手法：SELECT column_name, character_set_name FROM
--             information_schema.columns
--             WHERE table_name='v_reservation_detail';
--   写成 gbk 就是这个问题。
--   所以这里显式 SET NAMES，让本文件在"任何客户端、任何默认字符集下"
--   执行结果都一致。
-- ------------------------------------------------------------
SET NAMES utf8mb4;

-- ------------------------------------------------------------
-- 视图 1：v_reservation_detail　场地占用台账明细
-- ------------------------------------------------------------
-- 为什么需要它：
--   reservation 是「场地占用账本」，三端全部是外键
--   （court_id / user_id / activity_id），直接查出来只有编号，
--   读不出「谁、在哪块场、什么时段、是个人预约还是活动占场」。
-- 这个视图解决两件事：
--   ① 把三个外键 JOIN 成可读的名称（场馆 / 球场 / 占用方）
--   ② 把 user_id 与 activity_id 的「二选一」语义翻译成人话 source_type
-- 注：duration_hours 与 status_text 是【导出项】——
--     它们只在这个视图里实时算，表里不建字段（见 PPT「二、数据表设计」）。
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_reservation_detail AS
SELECT
    r.id                                        AS reservation_id,   -- 占用编号
    r.court_id                                  AS court_id,
    c.name                                      AS court_name,       -- 球场名，如「3 号场」
    v.id                                        AS venue_id,
    v.name                                      AS venue_name,       -- 场馆名
    r.user_id                                   AS user_id,
    u.nickname                                  AS user_nickname,    -- 个人预约时有值
    r.activity_id                               AS activity_id,
    a.title                                     AS activity_title,   -- 活动占场时有值
    COALESCE(u.nickname, a.title)               AS owner_display,    -- 占用方（二选一后的统一展示名）
    CASE WHEN r.activity_id IS NULL
         THEN '个人预约'
         ELSE '活动占场' END                     AS source_type,      -- 占用来源
    r.start_time                                AS start_time,
    r.end_time                                  AS end_time,
    TIMESTAMPDIFF(HOUR, r.start_time, r.end_time) AS duration_hours, -- 占用时长（导出项）
    r.status                                    AS status,
    CASE r.status WHEN 1 THEN '有效'
                  WHEN 2 THEN '已取消' END      AS status_text,      -- 状态文本（导出项）
    r.cancel_time                               AS cancel_time,
    r.create_time                               AS create_time
FROM reservation r
JOIN      court    c ON c.id = r.court_id
JOIN      venue    v ON v.id = c.venue_id
LEFT JOIN user     u ON u.id = r.user_id
LEFT JOIN activity a ON a.id = r.activity_id;

-- ------------------------------------------------------------
-- 视图 2：v_activity_signup_detail　活动报名明细
-- ------------------------------------------------------------
-- 为什么需要它：
--   registration 是「报名」这个多对多联系落成的表，
--   本身只有 user_id / activity_id 两个外键。
--   这个视图把「谁报了什么活动、活动什么时间、报名是否还有效」拼成一行，
--   给「我的报名」页和管理端报名名单共用。
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_activity_signup_detail AS
SELECT
    g.id                                        AS registration_id,  -- 报名记录编号
    g.activity_id                               AS activity_id,
    a.title                                     AS activity_title,   -- 活动标题
    a.start_time                                AS activity_start_time,
    a.end_time                                  AS activity_end_time,
    a.max_members                               AS max_members,
    a.status                                    AS activity_status,
    CASE a.status WHEN 0 THEN '待审核' WHEN 1 THEN '已发布'
                  WHEN 2 THEN '已驳回' WHEN 3 THEN '已取消'
                  WHEN 4 THEN '已结束' END      AS activity_status_text,
    g.user_id                                   AS user_id,
    u.username                                  AS username,
    u.nickname                                  AS nickname,         -- 报名人昵称
    u.avatar_url                                AS avatar_url,
    u.role                                      AS role,
    g.status                                    AS signup_status,
    CASE g.status WHEN 1 THEN '已报名'
                  WHEN 2 THEN '已取消' END      AS signup_status_text,
    g.create_time                               AS signup_time,      -- 报名时间
    g.update_time                               AS update_time
FROM registration g
JOIN activity a ON a.id = g.activity_id
JOIN user     u ON u.id = g.user_id;

-- ------------------------------------------------------------
-- 视图 3：v_post_with_stats　帖子 + 作者 + 评论数 + 点赞数
-- ------------------------------------------------------------
-- 为什么需要它：
--   帖子列表页要同时显示「作者昵称、评论数、点赞数」。
--   注意 comment_count 与 like_count 都是【导出项】——
--   它们由 COUNT 实时算出来，post 表里【不建这两个字段】。
--   （如果当初真的给 post 加了 like_count 列，就会出现
--     「点赞表有新记录、计数字段忘了更新」的不一致。）
--   这正是把导出项放进视图、而不是放进表的正确做法。
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_post_with_stats AS
SELECT
    p.id                                        AS post_id,
    p.title                                     AS title,
    SUBSTRING(p.content, 1, 120)                AS content_preview,  -- 列表页用的正文摘要
    LENGTH(p.content)                           AS content_length,
    p.user_id                                   AS user_id,
    u.nickname                                  AS author_nickname,  -- 作者昵称
    u.avatar_url                                AS author_avatar,
    p.create_time                               AS create_time,
    p.update_time                               AS update_time,
    (SELECT COUNT(*) FROM comment   c WHERE c.post_id = p.id) AS comment_count, -- 导出项
    (SELECT COUNT(*) FROM post_like l WHERE l.post_id = p.id) AS like_count     -- 导出项
FROM post p
JOIN user u ON u.id = p.user_id;

-- ------------------------------------------------------------
-- 视图 4：v_activity_signup_stat　活动报名进度（统计视图）
-- ------------------------------------------------------------
-- 为什么需要它：
--   活动列表要显示「已报名 8 / 上限 20」。
--   已报名人数是【导出项】，绝不能写成 activity 表的一个字段，
--   否则并发报名时「计数字段」与「报名表」会不一致
--   （详见 docs/known-issues.md 0001 号缺陷的同类问题）。
--   放在视图里由 COUNT 实时算，永远和报名表一致。
-- ------------------------------------------------------------
CREATE OR REPLACE VIEW v_activity_signup_stat AS
SELECT
    a.id                                        AS activity_id,
    a.title                                     AS title,
    a.start_time                                AS start_time,
    a.end_time                                  AS end_time,
    a.status                                    AS status,
    a.max_members                               AS max_members,
    COUNT(CASE WHEN g.status = 1 THEN 1 END)    AS active_count,     -- 有效报名人数（导出项）
    a.max_members
        - COUNT(CASE WHEN g.status = 1 THEN 1 END) AS seats_left,    -- 剩余名额（导出项）
    COUNT(CASE WHEN g.status = 2 THEN 1 END)    AS cancelled_count   -- 已取消人数（导出项）
FROM activity a
LEFT JOIN registration g ON g.activity_id = a.id
GROUP BY a.id, a.title, a.start_time, a.end_time, a.status, a.max_members;


-- ============================================================
-- 验证查询（人工执行用，不属于视图定义，需要时逐条取消注释跑）
-- ============================================================

-- 【视图 1】占用台账明细：应能看到球场名、占用方、来源三列都是人话
-- SELECT reservation_id, venue_name, court_name, owner_display, source_type,
--        start_time, end_time, duration_hours, status_text
-- FROM v_reservation_detail
-- ORDER BY start_time DESC
-- LIMIT 20;

-- 【视图 1】只筛「活动占场」的行，验证 source_type 的判定是否正确
-- SELECT reservation_id, court_name, activity_title, start_time, end_time
-- FROM v_reservation_detail
-- WHERE source_type = '活动占场'
-- ORDER BY start_time DESC;

-- 【视图 1】只筛「个人预约」的行，user_nickname 不应为空、activity_title 应为 NULL
-- SELECT reservation_id, court_name, user_nickname, activity_title, start_time
-- FROM v_reservation_detail
-- WHERE source_type = '个人预约';

-- 【视图 2】报名明细：查某个活动的报名名单
-- SELECT nickname, username, signup_time, signup_status_text
-- FROM v_activity_signup_detail
-- WHERE activity_id = 1
-- ORDER BY signup_time;

-- 【视图 3】帖子列表：点赞数应从高到低排得出来
-- SELECT post_id, title, author_nickname, comment_count, like_count, create_time
-- FROM v_post_with_stats
-- ORDER BY like_count DESC, create_time DESC;

-- 【视图 4】活动报名进度：seats_left 不应为负数
-- SELECT activity_id, title, active_count, max_members, seats_left, cancelled_count
-- FROM v_activity_signup_stat
-- ORDER BY start_time DESC;

-- 【视图 4】一致性校验：下面这条查询应该返回 0 行。
-- 如果有行返回，说明「视图算出来的有效报名数」与「报名表实际有效行数」不一致，
-- 属于数据异常，需要排查。
-- SELECT s.activity_id, s.active_count AS 视图值, x.real_count AS 表内实际值
-- FROM v_activity_signup_stat s
-- JOIN (
--     SELECT activity_id, COUNT(*) AS real_count
--     FROM registration WHERE status = 1 GROUP BY activity_id
-- ) x ON x.activity_id = s.activity_id
-- WHERE s.active_count <> x.real_count;

-- 【视图 4】边界校验：seats_left 小于 0 说明超卖了
-- SELECT activity_id, title, active_count, max_members, seats_left
-- FROM v_activity_signup_stat
-- WHERE seats_left < 0;


-- ============================================================
-- 清理（需要重建视图时用，平时不要跑）
-- ============================================================
-- DROP VIEW IF EXISTS v_reservation_detail;
-- DROP VIEW IF EXISTS v_activity_signup_detail;
-- DROP VIEW IF EXISTS v_post_with_stats;
-- DROP VIEW IF EXISTS v_activity_signup_stat;

-- ============================================================
-- 四个视图与考核点的对应关系（备查，不是 SQL）
-- ------------------------------------------------------------
--  v_reservation_detail      → PPT「三、数据库操作--(2)联系表 · 查：视图的完整查询」
--                              联系表直查只有外键编号，视图才给出完整信息
--  v_activity_signup_detail  → 同上；联系表的另一种展示形态
--  v_post_with_stats         → PPT「二、数据表设计 · 导出项（小计、合计）」
--                              评论数/点赞数是导出项，放视图不放表
--  v_activity_signup_stat    → 同上；已报名人数是导出项，放视图保证与报名表一致
-- ============================================================
