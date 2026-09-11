// 已知明文攻击: 在 .so 中找已知字符串的混淆版本, 反推 8 字节 XOR key
// 混淆: m_data[i] = plain[i] ^ key[i%8], plain 含 null 终止符
const fs = require('fs');
const soPath = process.argv[2] || '/storage/emulated/0/MT2/lynkco_extracted/assets/vwwwwwvwww/arm64/lib3f73cd8b395af3ae.so';
const so = fs.readFileSync(soPath);

// dpt-shell 源码中 AY_OBFUSCATE 的已知明文 (来自 dpt.cpp / dpt_macro.h)
const KNOWN = [
  'assets/OoooooOooo',
  'assets/d_shell_data_001',
  'app_name',
  'acf_name',
  'jni_cls_name',
  'app_sign_sha256',
  'dex_sign',
  'junk_cls_name',
  'risk_check_flags',
  'i11111i111.zip',
  'classes.dex',
  'cn.navitool',
  'com.luoye.dpt',
];

function tryPlain(plain) {
  const P = Buffer.concat([Buffer.from(plain, 'ascii'), Buffer.from([0])]);
  const L = P.length;
  if (L < 9) return null; // 至少需要 8+1 字节验证
  const results = [];
  for (let i = 0; i <= so.length - L; i++) {
    // 候选 key 前 8 字节
    const kb = Buffer.alloc(8);
    let ok = true;
    for (let k = 0; k < 8; k++) kb[k] = so[i + k] ^ P[k];
    // 验证剩余字节 (k=8..L-1)
    for (let k = 8; k < L; k++) {
      if ((so[i + k] ^ kb[k % 8]) !== P[k]) { ok = false; break; }
    }
    if (ok) results.push({ offset: i, key: kb.toString('hex') });
  }
  return results;
}

let foundAny = false;
for (const p of KNOWN) {
  const r = tryPlain(p);
  if (r.length) {
    foundAny = true;
    console.log(`✅ 明文 "${p}" 在 .so 中找到混淆数组!`);
    for (const hit of r.slice(0, 5)) {
      console.log(`   offset=0x${hit.offset.toString(16)}  key(LE)=${hit.key}`);
    }
  }
}
if (!foundAny) console.log('未找到已知明文混淆数组');
