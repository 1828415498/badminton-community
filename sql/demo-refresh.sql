-- ============================================================
-- demo-refresh.sql —— 答辩演示数据刷新
-- ============================================================
-- 用途：把业务数据（活动 / 占用 / 报名 / 帖子）重置为一组
--        "相对今天"的可用数据，让现场演示每一步都有东西可点。
--
-- 为什么需要它：
--   原始种子数据里的活动与预约日期都是 2026-09 的固定值，
--   一旦当天日期超过它们，就会出现「今天没有球局」「时段全是已过」
--   「没有可报名的活动」——演示当场很难看。
--   本脚本用 CURDATE() 相对生成，所以【任何一天执行都是新的】。
--
-- 建议：答辩当天早上执行一次（或前一天晚上）。
--
-- 保留不动：user / venue / court（基础数据）、以及 views.sql 建的视图。
-- 幂等：可重复执行（每次先清空业务数据再重建）。
--
-- 用法：
--   mysql -uroot -p123456 --default-character-set=utf8mb4 badminton_community < sql/demo-refresh.sql
-- （脚本内已带 SET NAMES utf8mb4，用别的客户端执行也不会串字符集）
-- ============================================================

SET NAMES utf8mb4;
USE badminton_community;

-- ------------------------------------------------------------
-- 0. 清空业务数据（按外键依赖顺序）
--    保留：user / venue / court
-- ------------------------------------------------------------
DELETE FROM post_like;
DELETE FROM comment;
DELETE FROM post;
DELETE FROM registration;
DELETE FROM reservation;
DELETE FROM activity;

-- ------------------------------------------------------------
-- 0.5 管理员昵称（纯观感调整；库里原本存的是"哈哈"）
-- ------------------------------------------------------------
UPDATE user SET nickname = '管理员' WHERE id = 1;

-- ------------------------------------------------------------
-- 1. 活动
--    状态码：0=待审核 1=已发布 2=已驳回 3=已取消 4=已结束
--    演示覆盖：已发布 3 场（可报名）、待审核 1 场（可审核）、已结束 1 场
-- ------------------------------------------------------------
INSERT INTO activity
  (id, creator_id, title, description, start_time, end_time, max_members, status, reviewer_id, review_time, create_time) VALUES
  (1, 2, '今晚双打，还差 3 人',
      '水平不限，场地费 AA。没拍子可以现场借。',
      TIMESTAMP(CURDATE(), '19:00:00'), TIMESTAMP(CURDATE(), '21:00:00'),
      6, 1, 1, NOW() - INTERVAL 2 DAY, NOW() - INTERVAL 2 DAY),

  (2, 1, '明天傍晚固定局',
      '每周固定，老带新，欢迎新手。',
      TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '18:00:00'), TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '20:00:00'),
      4, 1, 1, NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),

  (3, 2, '后天周末双打',
      '周末人多，占两块场，最多 8 人。',
      TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '20:00:00'), TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '22:00:00'),
      8, 1, 1, NOW() - INTERVAL 1 DAY, NOW() - INTERVAL 1 DAY),

  (4, 3, '今晚临时加一场',
      '刚提交，等管理员审核。',
      TIMESTAMP(CURDATE(), '21:00:00'), TIMESTAMP(CURDATE(), '22:00:00'),
      4, 0, NULL, NULL, NOW()),

  (5, 3, '上周五晚固定局',
      '这场已经打完了，用于展示「已结束」分组。',
      TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '19:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '21:00:00'),
      6, 4, 1, NOW() - INTERVAL 3 DAY, NOW() - INTERVAL 3 DAY);

-- ------------------------------------------------------------
-- 2. 场地占用账本
--    活动占场：activity_id 有值、user_id 为 NULL
--    个人预约：user_id 有值、activity_id 为 NULL
--    （chk_reservation_owner 强制二选一）
--    重要：同一球场同一时段不得有两条有效占用，本脚本已错开。
-- ------------------------------------------------------------
INSERT INTO reservation
  (id, court_id, user_id, activity_id, start_time, end_time, status, create_time) VALUES
  -- 活动 1 占 1、2 号场（今晚 19-21）
  (1,  1, NULL, 1, TIMESTAMP(CURDATE(), '19:00:00'), TIMESTAMP(CURDATE(), '21:00:00'), 1, NOW() - INTERVAL 2 DAY),
  (2,  2, NULL, 1, TIMESTAMP(CURDATE(), '19:00:00'), TIMESTAMP(CURDATE(), '21:00:00'), 1, NOW() - INTERVAL 2 DAY),
  -- 活动 2 占 3 号场（明天 18-20）
  (3,  3, NULL, 2, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '18:00:00'), TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '20:00:00'), 1, NOW() - INTERVAL 1 DAY),
  -- 活动 3 占 4、5 号场（后天 20-22）
  (4,  4, NULL, 3, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '20:00:00'), TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '22:00:00'), 1, NOW() - INTERVAL 1 DAY),
  (5,  5, NULL, 3, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '20:00:00'), TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '22:00:00'), 1, NOW() - INTERVAL 1 DAY),
  -- 活动 4（待审核）占 6 号场（今晚 21-22）
  (6,  6, NULL, 4, TIMESTAMP(CURDATE(), '21:00:00'), TIMESTAMP(CURDATE(), '22:00:00'), 1, NOW()),
  -- 活动 5（已结束）占 1、2 号场（昨天 19-21）
  (7,  1, NULL, 5, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '19:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '21:00:00'), 1, NOW() - INTERVAL 3 DAY),
  (8,  2, NULL, 5, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '19:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '21:00:00'), 1, NOW() - INTERVAL 3 DAY),
  -- 个人预约：都在 4 小时之外，可现场演示「取消预约」
  (9,  1, 2, NULL, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '19:00:00'), TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '20:00:00'), 1, NOW() - INTERVAL 2 HOUR),
  (10, 4, 3, NULL, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '20:00:00'), TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '21:00:00'), 1, NOW() - INTERVAL 3 HOUR),
  (11, 5, 2, NULL, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '18:00:00'), TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 2 DAY), '19:00:00'), 1, NOW() - INTERVAL 1 HOUR),
  (12, 6, 4, NULL, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '21:00:00'), TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 1 DAY), '22:00:00'), 1, NOW() - INTERVAL 5 HOUR),
  (13, 3, 3, NULL, TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '19:00:00'), TIMESTAMP(DATE_ADD(CURDATE(), INTERVAL 3 DAY), '20:00:00'), 1, NOW() - INTERVAL 30 MINUTE),
  -- 历史个人预约：用于演示「打印凭证」（已过期，显示"已过取消时限"是正确行为）
  (14, 2, 2, NULL, TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '18:00:00'), TIMESTAMP(DATE_SUB(CURDATE(), INTERVAL 1 DAY), '19:00:00'), 1, NOW() - INTERVAL 3 DAY);

-- ------------------------------------------------------------
-- 3. 报名
--    业务规则：创建者提交活动时自动占 1 个名额。
--    所以每场「已发布 / 已结束」的活动都包含创建者的一条报名。
--    待审核的活动（id=4）不开放报名。
-- ------------------------------------------------------------
INSERT INTO registration (id, user_id, activity_id, status, create_time) VALUES
  (1, 2, 1, 1, NOW() - INTERVAL 2 DAY),    -- 活动1 创建者
  (2, 3, 1, 1, NOW() - INTERVAL 1 DAY),
  (3, 4, 1, 1, NOW() - INTERVAL 5 HOUR),
  (4, 1, 2, 1, NOW() - INTERVAL 1 DAY),    -- 活动2 创建者
  (5, 2, 3, 1, NOW() - INTERVAL 1 DAY),    -- 活动3 创建者
  (6, 3, 5, 1, NOW() - INTERVAL 3 DAY),    -- 活动5 创建者
  (7, 4, 5, 1, NOW() - INTERVAL 2 DAY);

-- ------------------------------------------------------------
-- 4. 社区：帖子 / 评论 / 点赞
-- ------------------------------------------------------------
INSERT INTO post (id, user_id, title, content, create_time) VALUES
  (1, 2, '第一次来星河球馆，球拍能租吗？',
      '刚搬到附近，还没买拍子。前台是不是可以借？要押金吗？',
      NOW() - INTERVAL 2 DAY),
  (2, 3, '周三晚固定局长长期招人',
      '每周三晚 19:00-21:00，3 号场，水平中等。想一起打的跟帖。',
      NOW() - INTERVAL 1 DAY),
  (3, 4, '打完球膝盖有点酸，怎么放松',
      '最近打得有点猛，第二天膝盖发酸，大家有什么拉伸建议？',
      NOW() - INTERVAL 6 DAY),
  (4, 5, '求推荐入门球拍，预算 500 以内',
      '纯新手，不打比赛，主要是想找一支耐打的。',
      NOW() - INTERVAL 12 HOUR);

INSERT INTO comment (post_id, user_id, content, create_time) VALUES
  (1, 3, '可以的，前台借拍押金 50，走的时候退。', NOW() - INTERVAL 1 DAY),
  (1, 4, '同问，我也刚搬过来。', NOW() - INTERVAL 20 HOUR),
  (2, 5, '我可以吗？水平中等偏下。', NOW() - INTERVAL 12 HOUR),
  (3, 2, '打球前后都要拉伸，尤其是小腿和股四头肌。', NOW() - INTERVAL 5 DAY);

INSERT INTO post_like (post_id, user_id, create_time) VALUES
  (1, 3, NOW() - INTERVAL 1 DAY),
  (2, 2, NOW() - INTERVAL 20 HOUR),
  (2, 4, NOW() - INTERVAL 10 HOUR),
  (4, 2, NOW() - INTERVAL 6 HOUR);

-- ------------------------------------------------------------
-- 5. 自查（执行后应看到下面的结果）
-- ------------------------------------------------------------
SELECT '--- 活动状态分布（0=待审 1=已发布 4=已结束）---' AS chk;
SELECT status, COUNT(*) AS cnt FROM activity GROUP BY status ORDER BY status;

SELECT '--- 占用账本：活动占场 vs 个人预约 ---' AS chk;
SELECT CASE WHEN activity_id IS NOT NULL THEN '活动占场' ELSE '个人预约' END AS src, COUNT(*) AS cnt
FROM reservation GROUP BY src;

SELECT '--- 时段冲突检测（必须返回 0 行）---' AS chk;
SELECT a.id AS a_id, b.id AS b_id, a.court_id, a.start_time
FROM reservation a
JOIN reservation b
  ON a.court_id = b.court_id
 AND a.id < b.id
 AND a.status = 1 AND b.status = 1
 AND a.start_time < b.end_time AND b.start_time < a.end_time;

SELECT '--- 视图能查到未来数据（凭证/台账）---' AS chk;
SELECT reservation_id, court_name, owner_display, source_type, start_time, status_text
FROM v_reservation_detail
ORDER BY reservation_id DESC LIMIT 6;
