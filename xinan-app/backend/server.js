// ============================================================
// 「心安」后端 API 服务 (Node.js + Express)
// 功能: 手机号注册/登录 + 情绪数据统一收纳 + 深度分析
// 运行: npm install express mysql2 cors crypto
//       node server.js
// ============================================================
const express = require('express');
const mysql = require('mysql2/promise');
const crypto = require('crypto');
const cors = require('cors');

const app = express();
app.use(express.json({ limit: '2mb' }));
app.use(cors());

// ---------- 配置 ----------
const CFG = {
  port: 3000,
  db: { host: 'localhost', user: 'xinan', password: 'xinan123', database: 'xinan' },
  jwtSecret: 'xinan-secret-change-me',
  smsSecret: 'sms-secret', // 短信服务配置(阿里云/腾讯云)
};

// ---------- 工具 ----------
// 手机号哈希 (隐私: 不放明文)
function hashPhone(phone, salt = 'xinan-salt') {
  return crypto.createHash('sha256').update(phone + salt).digest('hex');
}
// 简单Token
function makeToken(userId) {
  return crypto.createHmac('sha256', CFG.jwtSecret)
    .update(userId + Date.now()).digest('hex');
}

let db;
async function initDb() {
  db = await mysql.createPool(CFG.db);
  console.log('✅ 数据库连接成功');
}

// ---------- API: 注册 ----------
// POST /api/register {phone, code, nickname}
app.post('/api/register', async (req, res) => {
  try {
    const { phone, code, nickname } = req.body;
    // TODO: 校验短信验证码 (对接短信服务, 此处简化: 123456)
    if (code !== '123456') return res.status(400).json({ ok: false, msg: '验证码错误' });
    const userId = crypto.randomUUID();
    const phoneHash = hashPhone(phone);
    await db.query(
      'INSERT INTO users (user_id, phone_hash, nickname) VALUES (?,?,?)',
      [userId, phoneHash, nickname || '心安用户']
    );
    res.json({ ok: true, token: makeToken(userId), userId });
  } catch (e) {
    if (e.code === 'ER_DUP_ENTRY') return res.status(400).json({ ok: false, msg: '手机号已注册' });
    res.status(500).json({ ok: false, msg: e.message });
  }
});

// ---------- API: 登录 ----------
// POST /api/login {phone, code}
app.post('/api/login', async (req, res) => {
  const { phone, code } = req.body;
  if (code !== '123456') return res.status(400).json({ ok: false, msg: '验证码错误' });
  const [rows] = await db.query('SELECT user_id FROM users WHERE phone_hash=?', [hashPhone(phone)]);
  if (!rows.length) return res.status(404).json({ ok: false, msg: '用户不存在, 请注册' });
  await db.query('UPDATE users SET last_active=NOW() WHERE user_id=?', [rows[0].user_id]);
  res.json({ ok: true, token: makeToken(rows[0].user_id), userId: rows[0].user_id });
});

// ---------- API: 情绪事件上报 (核心) ----------
// POST /api/emotion {userId, mode, emotionType, anxietyScore, microExpr, triggerKw, strategyUsed, strategyEffect}
app.post('/api/emotion', async (req, res) => {
  const {
    userId, mode, emotionType, anxietyScore,
    microExpr, triggerKw, strategyUsed, strategyEffect,
    srcVisual, srcText, srcAudio, confidence,
  } = req.body;
  await db.query(
    `INSERT INTO emotion_events
     (user_id, event_time, mode, emotion_type, anxiety_score, confidence,
      micro_expr, trigger_kw, strategy_used, strategy_effect,
      src_visual, src_text, src_audio)
     VALUES (?, NOW(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    [userId, mode, emotionType, anxietyScore, confidence,
     JSON.stringify(microExpr), triggerKw, strategyUsed, strategyEffect,
     srcVisual || 0, srcText || 0, srcAudio || 0]
  );
  res.json({ ok: true });
});

// ---------- API: 会话开始/结束 ----------
// POST /api/session/start {userId, mode, anxietyStart}
app.post('/api/session/start', async (req, res) => {
  const { userId, mode, anxietyStart } = req.body;
  const [r] = await db.query(
    'INSERT INTO sessions (user_id, start_time, mode, anxiety_start) VALUES (?, NOW(), ?, ?)',
    [userId, mode, anxietyStart]
  );
  res.json({ ok: true, sessionId: r.insertId });
});

// POST /api/session/end {sessionId, anxietyEnd, anxietyTrend, rating}
app.post('/api/session/end', async (req, res) => {
  const { sessionId, anxietyEnd, anxietyTrend, rating } = req.body;
  await db.query(
    `UPDATE sessions SET end_time=NOW(), anxiety_end=?, anxiety_trend=?, rating=?
     WHERE session_id=?`,
    [anxietyEnd, JSON.stringify(anxietyTrend), rating, sessionId]
  );
  res.json({ ok: true });
});

// ---------- API: 个人进度/趋势 ----------
// GET /api/user/:id/progress
app.get('/api/user/:id/progress', async (req, res) => {
  const [rows] = await db.query(
    `SELECT DATE(event_time) AS d, AVG(anxiety_score) AS avg_a, COUNT(*) AS cnt
     FROM emotion_events WHERE user_id=? AND event_time >= DATE_SUB(NOW(), INTERVAL 30 DAY)
     GROUP BY DATE(event_time) ORDER BY d`, [req.params.id]);
  res.json({ ok: true, data: rows });
});

// ---------- API: 匿名聚合分析 (调研) ----------
// GET /api/analysis/trends?days=30
app.get('/api/analysis/trends', async (req, res) => {
  const days = parseInt(req.query.days) || 30;
  const [hourDist] = await db.query(
    `SELECT HOUR(event_time) AS h, COUNT(*) AS cnt, AVG(anxiety_score) AS avg_a
     FROM emotion_events WHERE event_time >= DATE_SUB(NOW(), INTERVAL ? DAY)
     GROUP BY h ORDER BY cnt DESC`, [days]);
  const [emotionDist] = await db.query(
    `SELECT emotion_type, COUNT(*) AS cnt, AVG(anxiety_score) AS avg_a
     FROM emotion_events WHERE event_time >= DATE_SUB(NOW(), INTERVAL ? DAY)
     GROUP BY emotion_type`, [days]);
  const [strategies] = await db.query(
    `SELECT strategy_used, COUNT(*) AS cnt, AVG(strategy_effect) AS avg_effect
     FROM emotion_events WHERE strategy_used IS NOT NULL
     AND event_time >= DATE_SUB(NOW(), INTERVAL ? DAY)
     GROUP BY strategy_used`, [days]);
  res.json({ ok: true, days, hourDist, emotionDist, strategies });
});

// ---------- API: 疗效研究 (群体) ----------
// GET /api/analysis/efficacy
app.get('/api/analysis/efficacy', async (req, res) => {
  const [rows] = await db.query(
    `SELECT week_no, AVG(avg_anxiety) AS avg_a, AVG(gscore) AS avg_g,
     COUNT(*) AS users FROM user_progress GROUP BY week_no ORDER BY week_no`);
  res.json({ ok: true, data: rows });
});

// ---------- 启动 ----------
initDb().then(() => {
  app.listen(CFG.port, () => console.log(`🚀 心安后端运行: http://0.0.0.0:${CFG.port}`));
}).catch(e => {
  console.error('❌ 启动失败:', e.message);
  process.exit(1);
});