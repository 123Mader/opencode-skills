#!/usr/bin/env bash
# ============================================================
# 「心安」后端自签 HTTPS 证书生成 (内网, mkcert 风格)
# 生成: 根 CA (装手机) + 签 10.0.0.1 leaf 证书 (server.js 用)
# 用法: cd backend && bash gen-cert.sh [IP或域名]
#   默认 IP=10.0.0.1, 可传参改: bash gen-cert.sh 192.168.1.5
# 前置: openssl (Linux/Mac 自带; Windows 装 Git Bash 或 OpenSSL)
# ============================================================
set -e
IP="${1:-${XINAN_IP:-10.0.0.1}}"
CERT_DIR="$(dirname "$0")/cert"
mkdir -p "$CERT_DIR"
cd "$CERT_DIR"

echo "🔍 目标: $IP"

# 1. 根 CA (自签, 10 年)
if [ ! -f rootCA.key ]; then
  echo "1️⃣  生成根 CA..."
  openssl genrsa -out rootCA.key 2048 2>/dev/null
  openssl req -x509 -new -nodes -key rootCA.key -sha256 -days 3650 \
    -out rootCA.crt -subj "/CN=Xinan Root CA" 2>/dev/null
else
  echo "1️⃣  根 CA 已存在, 复用"
fi

# 2. leaf 私钥 + CSR (含 SAN: IP + localhost)
echo "2️⃣  生成 leaf 证书 (SAN: IP:$IP, DNS:localhost)..."
openssl genrsa -out key.pem 2048 2>/dev/null
cat > san.cnf <<EOF
[req]
distinguished_name = req_dn
[req_dn]
[san]
subjectAltName = @alt_names
[alt_names]
IP.1 = $IP
IP.2 = 127.0.0.1
DNS.1 = localhost
EOF
openssl req -new -key key.pem -out server.csr -subj "/CN=$IP" 2>/dev/null
openssl x509 -req -in server.csr -CA rootCA.crt -CAkey rootCA.key -CAcreateserial \
  -out cert.pem -days 825 -sha256 -extensions san -extfile san.cnf 2>/dev/null

echo ""
echo "✅ 完成!"
echo "   服务端证书: $CERT_DIR/cert.pem + key.pem  (server.js 自动用)"
echo "   根 CA:      $CERT_DIR/rootCA.crt          (装到手机)"
echo ""
echo "📱 手机装 CA 步骤 (一加/ColorOS):"
echo "   1. 把 rootCA.crt 传到手机 (如 Download 目录)"
echo "   2. 设置 → 密码与安全 → 系统与凭据 → 加密与凭据 → 安装证书 → CA 证书"
echo "   3. 选 rootCA.crt → 命名 'Xinan CA' → 确定 (需锁屏密码验证)"
echo "   4. 心安 app 会自动信任 (network_security_config 已配 src=user)"
echo ""
echo "🚀 启动后端: cd backend && node server.js  (输出 🔒 HTTPS: :3443)"
