# 后端 HTTPS 部署 — 自签证书方案

> 把后端从 `http://10.0.0.1:3000` 升级为 `https://10.0.0.1:3443`,配置 SSL 证书。
> 内网 IP 无公网 CA 证书, 采用**自签根 CA + 签发 leaf 证书** 方案 (mkcert 风格)。
> 说明: App 已无账号体系、打开即用; 后端为可选的情绪数据分析服务, 客户端如需对接可自行在 App 内加 HTTP 客户端并信任该 CA。

## 架构

```
后端机 (10.0.0.1)
┌────────────────────────────┐
│ backend/gen-cert.sh        │
│  → cert/                   │
│     rootCA.crt (CA)        │──传输→ 客户端(手机/工具)
│     cert.pem   (leaf)      │
│     key.pem    (leaf key)  │
│                            │
│ server.js (https :3443)    │←─HTTPS── 客户端
│   https.createServer(cert) │   https://10.0.0.1:3443
└────────────────────────────┘
```

## 步骤 (后端机)

```bash
cd backend
bash gen-cert.sh              # 默认签 10.0.0.1; 改 IP: bash gen-cert.sh 192.168.1.5
node server.js                # 输出 🔒 HTTPS: https://0.0.0.0:3443
```
- 有 cert/ → 自动 HTTPS(:3443) + HTTP→HTTPS 重定向(:3000)
- 无 cert → 回退 HTTP(:3000) 并警告

## 步骤 (客户端信任 CA)

1. 把 `backend/cert/rootCA.crt` 传到客户端(如手机 Download 目录)
2. 手机: 设置 → 密码与安全 → 系统与凭据 → 加密与凭据 → **安装证书** → **CA 证书** → 选 rootCA.crt
3. (需锁屏密码验证) 命名 "Xinan CA" → 确定
4. 客户端请求时携带该 CA 完成 SSL 握手验证

## 验证

- `curl https://10.0.0.1:3443/api/analysis/trends`(后端机本机,带 --cacert rootCA.crt 验证)

## 改动清单

| 文件 | 改动 |
|------|------|
| backend/server.js | CFG 加 httpsPort(3443)+证书路径; 启动段 https 优先 + HTTP 重定向 |
| backend/gen-cert.sh | ★ 新增 openssl 生成 CA + leaf(含 SAN IP) |

## 若有公网域名

把自签换 Let's Encrypt 更佳:
- `npm i greenlock` 或 Caddy 反代 `domain.com { reverse_proxy localhost:3000 }`
- 客户端请求改 `https://domain.com`
- 客户端无需装 CA(LE 证书受系统信任)
