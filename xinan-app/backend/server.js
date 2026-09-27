// ============================================================
// 「心安」后端 API 服务 (Node.js + Express) — 精简版, 无账号
// 功能: 情绪数据统一收纳 + 深度分析 (匿名设备标识, 无需注册登录)
// 运行: npm install express mysql2 cors crypto
//       node server.js
// ============================================================
const express = require('express');
const mysql = require('mysql2/promise');
const cors = require('cors');

const app = express();
app.use(express.json({ limit: '2mb' }));
app.use(cors());

// ---------- 配置 ----------
const CFG = {
  port: 3000,                 // HTTP 端口 (无证书时回退 / 301 重定向到 HTTPS)
  httpsPort: 3443,            // HTTPS 端口
  db: { host: 'localhost', user: 'xinan', password: 'xinan123', database: 'xinan' },
  https: {
    // 证书路径 (跑 backend/gen-cert.sh 生成). 存在则启 HTTPS, 否则回退 HTTP
    cert: './cert/cert.pem',
    key:  './cert/key.pem',
  },
};

let db;
async function initDb() {
  db = await mysql.createPool(CFG.db);
  console.log('✅ 数据库连接成功');
}

// ---------- API: 情绪事件上报 (核心, 匿名设备ID) ----------
// POST /api/emotion {deviceId, mode, emotionType, anxietyScore, microExpr, triggerKw, strategyUsed, strategyEffect}
app.post('/api/emotion', async (req, res) => {
  const {
    deviceId, mode, emotionType, anxietyScore,
    microExpr, triggerKw, strategyUsed, strategyEffect,
    srcVisual, srcText, srcAudio, confidence,
  } = req.body;
  await db.query(
    `INSERT INTO emotion_events
     (device_id, event_time, mode, emotion_type, anxiety_score, confidence,
      micro_expr, trigger_kw, strategy_used, strategy_effect,
      src_visual, src_text, src_audio)
     VALUES (?, NOW(), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
    [deviceId, mode, emotionType, anxietyScore, confidence,
     JSON.stringify(microExpr), triggerKw, strategyUsed, strategyEffect,
     srcVisual || 0, srcText || 0, srcAudio || 0]
  );
  res.json({ ok: true });
});

// ---------- API: 会话开始/结束 ----------
// POST /api/session/start {deviceId, mode, anxietyStart}
app.post('/api/session/start', async (req, res) => {
  const { deviceId, mode, anxietyStart } = req.body;
  const [r] = await db.query(
    'INSERT INTO sessions (device_id, start_time, mode, anxiety_start) VALUES (?, NOW(), ?, ?)',
    [deviceId, mode, anxietyStart]
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

// ---------- API: 单设备进度/趋势 ----------
// GET /api/device/:id/progress
app.get('/api/device/:id/progress', async (req, res) => {
  const [rows] = await db.query(
    `SELECT DATE(event_time) AS d, AVG(anxiety_score) AS avg_a, COUNT(*) AS cnt
     FROM emotion_events WHERE device_id=? AND event_time >= DATE_SUB(NOW(), INTERVAL 30 DAY)
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
     COUNT(*) AS users FROM device_progress GROUP BY week_no ORDER BY week_no`);
  res.json({ ok: true, data: rows });
});

// ---------- 启动 (HTTPS 优先, 无证书回退 HTTP) ----------
const fs = require('fs');
const http = require('http');
const https = require('https');

initDb().then(() => {
  const hasCert = fs.existsSync(CFG.https.cert) && fs.existsSync(CFG.https.key);
  if (hasCert) {
    const creds = { cert: fs.readFileSync(CFG.https.cert), key: fs.readFileSync(CFG.https.key) };
    https.createServer(creds, app).listen(CFG.httpsPort, '0.0.0.0', () =>
      console.log(`🔒 HTTPS: https://0.0.0.0:${CFG.httpsPort}`));
    // HTTP → HTTPS 301 重定向 (http 访问自动跳转)
    http.createServer((req, res) => {
      const host = (req.headers.host || '').split(':')[0];
      res.writeHead(301, { Location: `https://${host}:${CFG.httpsPort}${req.url}` });
      res.end();
    }).listen(CFG.port, '0.0.0.0', () =>
      console.log(`↪️ HTTP→HTTPS 重定向: http://0.0.0.0:${CFG.port} → :${CFG.httpsPort}`));
  } else {
    console.warn('⚠️  未找到证书 (cert/cert.pem + key.pem), 回退明文 HTTP');
    console.warn('    请跑 backend/gen-cert.sh 生成自签证书后重启启用 HTTPS');
    app.listen(CFG.port, () => console.log(`🚀 HTTP(回退): http://0.0.0.0:${CFG.port}`));
  }
}).catch(e => {
  console.error('❌ 启动失败:', e.message);
  process.exit(1);
});
