-- ============================================================
-- 「心安」焦虑情绪助手 - 数据库 Schema v2.0 (无账号版)
-- 数据库: MySQL 8.0+ / PostgreSQL 15+
-- 用途: 情绪数据统一归纳 + 深度分析调研 (匿名设备标识, 无需注册)
-- ============================================================

CREATE DATABASE IF NOT EXISTS xinan CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE xinan;

-- ------------------------------------------------------------
-- 1. 情绪事件表 (核心! 每次微表情/文本识别的情绪记录)
--    以匿名 device_id 标识来源设备, 无用户账号体系
-- ------------------------------------------------------------
CREATE TABLE emotion_events (
  event_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
  device_id      VARCHAR(64) NOT NULL,              -- 匿名设备标识
  event_time     DATETIME NOT NULL,
  mode           ENUM('chat','video') NOT NULL,     -- 聊天/视频模式
  emotion_type   VARCHAR(20) NOT NULL,              -- 焦虑/平静/快乐/悲伤/愤怒/惊讶/厌恶
  anxiety_score  TINYINT NOT NULL,                  -- 焦虑指数 0-100
  confidence     DECIMAL(3,2),                      -- 多模态置信度
  -- 微表情信号 (FACS AU 强度, JSON 数组)
  micro_expr     JSON,
  -- 融合来源
  src_visual     TINYINT DEFAULT 0,                 -- 视觉贡献
  src_text       TINYINT DEFAULT 0,                 -- 文本贡献
  src_audio      TINYINT DEFAULT 0,                 -- 语音贡献(可选)
  -- 触发上下文 (匿名化处理后的诱因关键词)
  trigger_kw     VARCHAR(500),
  -- 疏导记录
  strategy_used  VARCHAR(30),
  strategy_effect TINYINT,                          -- 用户反馈效果 0-10
  INDEX idx_device_time (device_id, event_time),
  INDEX idx_time (event_time),
  INDEX idx_emotion (emotion_type)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 2. 会话表 (聊天/视频会话)
-- ------------------------------------------------------------
CREATE TABLE sessions (
  session_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
  device_id      VARCHAR(64) NOT NULL,
  start_time     DATETIME NOT NULL,
  end_time       DATETIME,
  mode           ENUM('chat','video'),
  message_count  INT DEFAULT 0,
  anxiety_start  TINYINT,                           -- 会话开始焦虑
  anxiety_end    TINYINT,                           -- 会话结束焦虑
  anxiety_trend  JSON,                              -- 会话内焦虑曲线
  rating         TINYINT,                           -- 用户评价 1-5
  INDEX idx_device (device_id, start_time)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 3. 对话消息表
-- ------------------------------------------------------------
CREATE TABLE messages (
  message_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
  session_id     BIGINT NOT NULL,
  device_id      VARCHAR(64) NOT NULL,
  role           ENUM('user','ai') NOT NULL,
  content        TEXT,
  emotion_detect VARCHAR(20),                       -- 本条消息附带情绪
  anxiety_delta  TINYINT,                           -- 附加焦虑变化
  created_at     DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_session (session_id)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 4. 微表情聚合统计表 (深度分析调研用)
-- ------------------------------------------------------------
CREATE TABLE micro_expr_stats (
  stat_date      DATE NOT NULL,
  device_id      VARCHAR(64) NOT NULL,
  au4_count      INT DEFAULT 0,                     -- 皱眉
  au23_count     INT DEFAULT 0,                     -- 嘴唇收紧
  au45_count     INT DEFAULT 0,                     -- 眨眼加快
  au15_count     INT DEFAULT 0,                     -- 嘴角下压
  au12_count     INT DEFAULT 0,                     -- 微笑
  dominant_emotion VARCHAR(20),
  avg_anxiety    DECIMAL(5,1),
  PRIMARY KEY (stat_date, device_id)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 5. 设备疏导效果表 (疗效研究, 匿名设备)
-- ------------------------------------------------------------
CREATE TABLE device_progress (
  progress_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
  device_id      VARCHAR(64) NOT NULL,
  week_no        INT,                               -- 使用第几周
  avg_anxiety    DECIMAL(5,1),                      -- 周均焦虑
  anxiety_trend  DECIMAL(5,2),                      -- 环比变化率
  sessions_count INT,
  most_effective_strategy VARCHAR(30),              -- 最有效策略
  gscore         TINYINT,                           -- 用户自评 0-100
  updated_at     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_device_week (device_id, week_no)
) ENGINE=InnoDB;

-- ------------------------------------------------------------
-- 6. 匿名聚合调研视图 (不暴露个人身份)
-- ------------------------------------------------------------
CREATE VIEW v_anonymous_trend AS
SELECT
  DATE(event_time) AS stat_date,
  emotion_type,
  COUNT(*) AS cnt,
  AVG(anxiety_score) AS avg_anxiety,
  AVG(strategy_effect) AS avg_effect
FROM emotion_events
GROUP BY DATE(event_time), emotion_type;

-- 查询示例: 焦虑高峰期
-- SELECT HOUR(event_time) AS h, COUNT(*) FROM emotion_events
-- WHERE emotion_type='焦虑' GROUP BY h ORDER BY cnt DESC;
