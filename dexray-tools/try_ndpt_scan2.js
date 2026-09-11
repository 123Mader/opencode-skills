// 用 63 个 build key 候选 × UNKNOWN 候选 × IV × 起点 扫描 NDPT 解密
const fs = require('fs');
const crypto = require('crypto');

const cfgBlob = fs.readFileSync('/storage/emulated/0/MT2/lynkco_extracted/assets/d_shell_data_001');
const storeBlob = fs.readFileSync('/storage/emulated/0/MT2/lynkco_extracted/assets/OoooooOooo');
const pkgName = 'cn.navitool';

// build key 候选
const cands = JSON.parse(fs.readFileSync('buildkey_candidates.json'));
const bkSet = new Set(cands.map(c => c.buildKey));
bkSet.add('b3f73cd8b395af3a'); // soName
console.log('build key 候选:', bkSet.size);

// UNKNOWN 候选: 从 .so 数据段扫描所有 16 字节窗口 (先只查 .data 段 offset 0xb7190-0xbeac8)
const so = fs.readFileSync('/storage/emulated/0/MT2/lynkco_extracted/assets/vwwwwwvwww/arm64/lib3f73cd8b395af3ae.so');
const unCands = [
  { name: 'NDPT头08', key: cfgBlob.subarray(0x08, 0x18) },
  { name: 'store头08', key: storeBlob.subarray(0x08, 0x18) },
];
// 加入 .data 段所有 16 字节窗口 (bit0=1 特征)
for (let i = 0xb7190; i <= so.length - 16; i += 1) {
  const k = so.subarray(i, i+16);
  let bit0ok = true;
  for (let b = 0; b < 16; b++) if ((k[b] & 1) !== 1) { bit0ok = false; break; }
  if (bit0ok) unCands.push({ name: 'data@'+i.toString(16), key: Buffer.from(k) });
}
console.log('UNKNOWN 候选:', unCands.length);

function tryDecrypt(ukey, buildKey, iv, data) {
  try {
    const material = pkgName + '_' + buildKey;
    const aesKey = crypto.createHmac('sha256', ukey).update(material).digest();
    const d = crypto.createDecipheriv('aes-256-cbc', aesKey, iv);
    let out = Buffer.concat([d.update(data), d.final()]);
    const pad = out[out.length-1];
    if (pad>=1 && pad<=16 && out.subarray(out.length-pad).every(b=>b===pad)) out = out.subarray(0, out.length-pad);
    const t = out.toString('utf8');
    if (t.startsWith('{') && t.length > 30) return t;
  } catch {}
  return null;
}

let hits = 0, tested = 0;
for (let start = 0; start <= cfgBlob.length - 32; start++) {
  const data = cfgBlob.subarray(start);
  if (data.length % 16 !== 0) continue;
  for (const uc of unCands) {
    const ivs = [
      ['IV=head08', cfgBlob.subarray(0x08, 0x18)],
      ['IV=key', uc.key],
      ['IV=key+2f76', (()=>{const v=Buffer.from(uc.key); v[3]=0x2f; v[9]=0x76; return v;})()],
    ];
    for (const bk of bkSet) {
      for (const [ivn, iv] of ivs) {
        tested++;
        const r = tryDecrypt(uc.key, bk, iv, data);
        if (r) {
          console.log(`🎯 start=0x${start.toString(16)} ${uc.name} buildKey="${bk}" ${ivn}: ${r.slice(0,200)}`);
          hits++;
          if (hits > 3) { console.log('足够命中, 停止'); process.exit(0); }
        }
      }
    }
  }
}
console.log('测试组合:', tested, ' 命中:', hits);
