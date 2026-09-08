-- ============================================================
-- 羽毛球社区管理系统 - 建表脚本（MVP 定稿版）
-- 依据 docs/database.md（第 4/5/6 节）
-- 环境：MySQL 8.0.16+（CHECK 约束生效依赖 8.0.16+）
-- 约定：库 badminton_community；InnoDB；utf8mb4；snake_case
-- 注：本脚本只建结构，初始化数据见 data.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS badminton_community
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE badminton_community;

-- ------------------------------------------------------------
-- 1. user 用户
-- ------------------------------------------------------------
CREATE TABLE user (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  username      VARCHAR(64)  NOT NULL COMMENT '登录名（唯一）',
  password_hash VARCHAR(255) NOT NULL COMMENT '密码哈希（永不存明文）',
  nickname      VARCHAR(64)  NOT NULL COMMENT '展示昵称',
  avatar_url    VARCHAR(255) NULL COMMENT '头像图片路径',
  role          TINYINT      NOT NULL DEFAULT 0 COMMENT '0=普通用户，1=管理员',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_username (username),
  CONSTRAINT chk_user_role CHECK (role IN (0, 1))
) ENGINE = InnoDB COMMENT ='用户';

-- ------------------------------------------------------------
-- 2. venue 场馆（V1 只有一行）
-- ------------------------------------------------------------
CREATE TABLE venue (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  name        VARCHAR(64)  NOT NULL COMMENT '场馆名称',
  address     VARCHAR(255) NOT NULL COMMENT '地址',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id)
) ENGINE = InnoDB COMMENT ='场馆';

-- ------------------------------------------------------------
-- 3. court 球场
-- ------------------------------------------------------------
CREATE TABLE court (
  id          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  venue_id    BIGINT      NOT NULL COMMENT '所属场馆',
  name        VARCHAR(32) NOT NULL COMMENT '球场名，如 1 号场',
  status      TINYINT     NOT NULL DEFAULT 1 COMMENT '1=可预约，2=不可预约',
  create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_court_venue_name (venue_id, name),
  KEY idx_court_venue_status (venue_id, status),
  CONSTRAINT fk_court_venue FOREIGN KEY (venue_id) REFERENCES venue (id),
  CONSTRAINT chk_court_status CHECK (status IN (1, 2))
) ENGINE = InnoDB COMMENT ='球场';

-- ------------------------------------------------------------
-- 4. activity 活动
-- ------------------------------------------------------------
CREATE TABLE activity (
  id            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  creator_id    BIGINT       NOT NULL COMMENT '创建者（组织者）',
  title         VARCHAR(100) NOT NULL COMMENT '活动标题',
  description   VARCHAR(2000) NULL COMMENT '活动描述',
  start_time    DATETIME     NOT NULL COMMENT '开始时间（整点）',
  end_time      DATETIME     NOT NULL COMMENT '结束时间（整点，与 start 同一天）',
  max_members   INT          NOT NULL COMMENT '报名人数上限（≥1，创建者自动占 1 名）',
  status        TINYINT      NOT NULL DEFAULT 0 COMMENT '0=待审核，1=已发布，2=已驳回，3=已取消，4=已结束',
  reviewer_id   BIGINT       NULL COMMENT '审核人',
  review_time   DATETIME     NULL COMMENT '审核时间',
  reject_reason VARCHAR(255) NULL COMMENT '驳回原因',
  create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_activity_status_start (status, start_time),
  KEY idx_activity_creator (creator_id),
  KEY idx_activity_reviewer (reviewer_id),
  CONSTRAINT fk_activity_creator FOREIGN KEY (creator_id) REFERENCES user (id),
  CONSTRAINT fk_activity_reviewer FOREIGN KEY (reviewer_id) REFERENCES user (id),
  CONSTRAINT chk_activity_status CHECK (status IN (0, 1, 2, 3, 4)),
  CONSTRAINT chk_activity_members CHECK (max_members >= 1),
  CONSTRAINT chk_activity_time CHECK (end_time > start_time)
) ENGINE = InnoDB COMMENT ='活动';

-- ------------------------------------------------------------
-- 5. reservation 场地占用账本（个人预约 + 活动占场统一）
-- ------------------------------------------------------------
CREATE TABLE reservation (
  id          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  court_id    BIGINT   NOT NULL COMMENT '被占用的球场',
  user_id     BIGINT   NULL COMMENT '个人预约有值；活动占场必须为 NULL',
  activity_id BIGINT   NULL COMMENT '活动占场有值；个人预约必须为 NULL',
  start_time  DATETIME NOT NULL COMMENT '占用开始（整点）',
  end_time    DATETIME NOT NULL COMMENT '占用结束（整点）',
  status      TINYINT  NOT NULL DEFAULT 1 COMMENT '1=有效，2=已取消',
  cancel_time DATETIME NULL COMMENT '取消时刻',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建（预约/占场）时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后一次变更时间',
  PRIMARY KEY (id),
  KEY idx_reservation_court_time (court_id, start_time, end_time),
  KEY idx_reservation_user_time (user_id, start_time),
  KEY idx_reservation_activity (activity_id),
  CONSTRAINT fk_reservation_court FOREIGN KEY (court_id) REFERENCES court (id),
  CONSTRAINT fk_reservation_user FOREIGN KEY (user_id) REFERENCES user (id),
  CONSTRAINT fk_reservation_activity FOREIGN KEY (activity_id) REFERENCES activity (id),
  CONSTRAINT chk_reservation_status CHECK (status IN (1, 2)),
  CONSTRAINT chk_reservation_time CHECK (end_time > start_time),
  -- 归属不变量：user_id 与 activity_id 至多一者非空（个人行或活动行，二选一）
  CONSTRAINT chk_reservation_owner CHECK ((user_id IS NULL) <> (activity_id IS NULL))
) ENGINE = InnoDB COMMENT ='场地占用账本';

-- ------------------------------------------------------------
-- 6. registration 报名（人册：一人一活动一条记录）
-- ------------------------------------------------------------
CREATE TABLE registration (
  id          BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id     BIGINT   NOT NULL COMMENT '报名用户',
  activity_id BIGINT   NOT NULL COMMENT '报名活动',
  status      TINYINT  NOT NULL DEFAULT 1 COMMENT '1=已报名，2=已取消',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '报名时间',
  update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  UNIQUE KEY uk_registration_user_activity (user_id, activity_id),
  KEY idx_registration_activity_status (activity_id, status),
  CONSTRAINT fk_registration_user FOREIGN KEY (user_id) REFERENCES user (id),
  CONSTRAINT fk_registration_activity FOREIGN KEY (activity_id) REFERENCES activity (id),
  CONSTRAINT chk_registration_status CHECK (status IN (1, 2))
) ENGINE = InnoDB COMMENT ='活动报名';

-- ------------------------------------------------------------
-- 7. post 帖子
-- ------------------------------------------------------------
CREATE TABLE post (
  id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  user_id     BIGINT       NOT NULL COMMENT '作者',
  title       VARCHAR(200) NOT NULL COMMENT '标题',
  content     TEXT         NOT NULL COMMENT '正文',
  create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (id),
  KEY idx_post_create_time (create_time),
  KEY idx_post_user (user_id),
  CONSTRAINT fk_post_user FOREIGN KEY (user_id) REFERENCES user (id)
) ENGINE = InnoDB COMMENT ='帖子';

-- ------------------------------------------------------------
-- 8. comment 评论（一级评论，不支持编辑）
-- ------------------------------------------------------------
CREATE TABLE comment (
  id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  post_id     BIGINT        NOT NULL COMMENT '所属帖子',
  user_id     BIGINT        NOT NULL COMMENT '评论者',
  content     VARCHAR(1000) NOT NULL COMMENT '评论内容',
  create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '评论时间',
  PRIMARY KEY (id),
  KEY idx_comment_post_time (post_id, create_time),
  CONSTRAINT fk_comment_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE CASCADE,
  CONSTRAINT fk_comment_user FOREIGN KEY (user_id) REFERENCES user (id)
) ENGINE = InnoDB COMMENT ='评论';

-- ------------------------------------------------------------
-- 9. post_like 点赞（复合主键 = 一人一帖一赞）
-- ------------------------------------------------------------
CREATE TABLE post_like (
  post_id     BIGINT   NOT NULL COMMENT '被点赞帖子',
  user_id     BIGINT   NOT NULL COMMENT '点赞用户',
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '点赞时间',
  PRIMARY KEY (post_id, user_id),
  KEY idx_post_like_user (user_id),
  CONSTRAINT fk_post_like_post FOREIGN KEY (post_id) REFERENCES post (id) ON DELETE CASCADE,
  CONSTRAINT fk_post_like_user FOREIGN KEY (user_id) REFERENCES user (id)
) ENGINE = InnoDB COMMENT ='帖子点赞';
