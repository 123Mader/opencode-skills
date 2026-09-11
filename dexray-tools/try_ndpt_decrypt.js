// 尝试组合: DPT_UNKNOWN_DATA 候选 × build key 候选 → HMAC派生 → AES-256-CBC 解密 NDPT config
const fs = require('fs');
const crypto = require('crypto');
const path = require('path');

const cfgPath = '/storage/emulated/0/MT2/lynkco_extracted/assets/d_shell_data_001';
const cfgBlob = fs.readFileSync(cfgPath);
const pkgName = 'cn.navitool';

// NDPT 头部
console.log('config 头16字节:', cfgBlob.subarray(0,16).toString('hex'));
console.log('NDPT头后数据长度:', cfgBlob.length - 0x18);

// DPT_UNKNOWN_DATA 候选
const candidates = [];
// 1. NDPT 头部 16 字节 (offset 0x08-0x17)
candidates.push({ name: 'NDPT头', key: cfgBlob.subarray(0x08, 0x18) });
// 2. NDPT 头 0x00-0x0f
candidates.push({ name: 'NDPT前16', key: cfgBlob.subarray(0, 16) });
// 3. OoooooOooo 头部 (两文件相同)
const storeBlob = fs.readFileSync('/storage/emulated/0/MT2/lynkco_extracted/assets/OoooooOooo');
candidates.push({ name: 'Store头', key: storeBlob.subarray(0x08, 0x18) });

// build key 候选: 从 crack_buildkey3 结果 (随机性筛选: 去重字符>=10)
const bkCands = ['b3f73cd8b395af3a']; // soName 也是 hex 候选

function tryDecrypt(uname, ukey, buildKey, iv, data) {
  try {
    const material = pkgName + '_' + buildKey;
    const aesKey = crypto.createHmac('sha256', ukey).update(material).digest();
    const d = crypto.createDecipheriv('aes-256-cbc', aesKey, iv);
    let out = Buffer.concat([d.update(data), d.final()]);
    const pad = out[out.length-1];
    if (pad>=1 && pad<=16 && out.subarray(out.length-pad).every(b=>b===pad)) out = out.subarray(0, out.length-pad);
    const t = out.toString('utf8');
    if (t.startsWith('{') && (t.includes('app_name') || t.includes('dex_sign') || t.includes('insns'))) {
      return t;
    }
  } catch {}
  return null;
}

let tested = 0;
for (const c of candidates) {
  // IV 变体: 头部16字节, generateIV(ukey)=ukey+覆盖
  const ivHdr = cfgBlob.subarray(0x08, 0x18);
  const ivGen = Buffer.from(c.key); ivGen[3]=0x2f; ivGen[9]=0x76;
  const ivGen2 = Buffer.from(c.key); ivGen2[3]=0x20; ivGen2[9]=0x74;
  const dataFull = cfgBlob.subarray(0x18); // 跳过 NDPT 头
  const dataAll = cfgBlob;
  for (const bk of bkCands) {
    tested++;
    for (const [ivn, iv] of [['IV=头', ivHdr], ['IV=gen(2f76)', ivGen], ['IV=gen(2074)', ivGen2]]) {
      for (const [dn, data] of [['data=0x18后', dataFull], ['data=全部', dataAll]]) {
        const r = tryDecrypt(c.name, c.key, bk, iv, data);
        if (r) console.log(`🎯 ${c.name} + buildKey=${bk} + ${ivn} + ${dn}: ${r.slice(0,120)}`);
      }
    }
  }
}
console.log('测试组合数:', tested, '× 6 变体');
