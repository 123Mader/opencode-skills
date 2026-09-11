// 综合扫描 NDPT config 解密: 密文起点 × build key 候选 × IV 变体
const fs = require('fs');
const crypto = require('crypto');

const cfgBlob = fs.readFileSync('/storage/emulated/0/MT2/lynkco_extracted/assets/d_shell_data_001');
const pkgName = 'cn.navitool';
const TL = cfgBlob.length;
console.log('config 总长:', TL, '  %16=', TL % 16);

// DPT_UNKNOWN_DATA 候选
const unCands = [
  { name: 'NDPT头08', key: cfgBlob.subarray(0x08, 0x18) },
  { name: 'NDPT前16', key: cfgBlob.subarray(0x00, 0x10) },
  { name: 'NDPT中10', key: cfgBlob.subarray(0x10, 0x20) },
];

// build key 候选: 穷举常见格式 + 从 .so 提取的 hex 候选(筛选)
const bkSet = new Set(['b3f73cd8b395af3a']);
// 加入 crack_buildkey3 的候选 (随机性筛选: 去重字符>=12, 且字符集合理)
const so = fs.readFileSync('/storage/emulated/0/MT2/lynkco_extracted/assets/vwwwwwvwww/arm64/lib3f73cd8b395af3ae.so');
// 扫描 .so 找 8-16 位 hex 子串
const re = /[0-9a-f]{8,16}/g;
const txt = so.toString('latin1');
let m;
while ((m = re.exec(txt)) !== null) {
  const s = m[0];
  if (s.length >= 12 && new Set(s).size >= 12) bkSet.add(s);
}
// 也加 16 字节窗口转 hex (可能有 build key 以 hex 文本存)
console.log('build key 候选数:', bkSet.size);

function tryDecrypt(ukey, buildKey, iv, data) {
  try {
    const material = pkgName + '_' + buildKey;
    const aesKey = crypto.createHmac('sha256', ukey).update(material).digest();
    const d = crypto.createDecipheriv('aes-256-cbc', aesKey, iv);
    let out = Buffer.concat([d.update(data), d.final()]);
    const pad = out[out.length-1];
    if (pad>=1 && pad<=16 && out.subarray(out.length-pad).every(b=>b===pad)) out = out.subarray(0, out.length-pad);
    const t = out.toString('utf8');
    if (t.startsWith('{') && t.length > 20) return t;
  } catch {}
  return null;
}

let hits = 0;
// 密文起点: 0..TL (尝试对齐到 16 倍数边界附近)
for (let start = 0; start <= TL - 32; start++) {
  const data = cfgBlob.subarray(start);
  if (data.length % 16 !== 0) continue;
  for (const uc of unCands) {
    const ivs = [
      ['IV=head08', cfgBlob.subarray(0x08, 0x18)],
      ['IV=key', Buffer.from(uc.key)],
      ['IV=key+2f76', (()=>{const v=Buffer.from(uc.key); v[3]=0x2f; v[9]=0x76; return v;})()],
      ['IV=key+2074', (()=>{const v=Buffer.from(uc.key); v[3]=0x20; v[9]=0x74; return v;})()],
    ];
    for (const bk of bkSet) {
      for (const [ivn, iv] of ivs) {
        const r = tryDecrypt(uc.key, bk, iv, data);
        if (r) {
          console.log(`🎯 start=0x${start.toString(16)} ${uc.name} buildKey="${bk}" ${ivn}: ${r.slice(0,150)}`);
          hits++;
        }
      }
    }
  }
}
console.log('命中数:', hits);
