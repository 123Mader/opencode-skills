// 破解 dpt-shell DPT_BUILD_KEY: 
// build key = Long.toHexString(nextLong()) = 16 位小写 hex
// 混淆: m_data[i] = plain[i] ^ key[i%8], key = generate_key(__LINE__)
// 方法: 枚举行号 seed → generate_key → 扫描 .so 中 17 字节窗口验证 XOR 后为 16 hex + null
const fs = require('fs');

const soPath = process.argv[2] || '/storage/emulated/0/MT2/lynkco_extracted/assets/vwwwwwvwww/arm64/lib3f73cd8b395af3ae.so';
const MAX_SEED = Number(process.argv[3] || 3000);
const so = fs.readFileSync(soPath);

function generateKey(seed) {
  let key = BigInt(seed);
  key ^= key >> 33n;
  key = (key * 0xff51afd7ed558ccdn) & 0xffffffffffffffffn;
  key ^= key >> 33n;
  key = (key * 0xc4ceb9fe1a85ec53n) & 0xffffffffffffffffn;
  key ^= key >> 33n;
  key |= 0x0101010101010101n;
  return key;
}

const HEX = new Set('0123456789abcdef'.split('').map(c => c.charCodeAt(0)));
const LEN = so.length;
const WINDOW = 17; // 16 hex chars + null

// 构建 posByByte: 所有 i 使 so[i+16] == byte
console.log('构建位置索引...');
const posByByte = Array.from({length: 256}, () => []);
for (let i = 0; i <= LEN - WINDOW; i++) {
  posByByte[so[i + 16]].push(i);
}
console.log('索引完成, 开始枚举行号 seed 1..' + MAX_SEED);

let found = 0;
for (let seed = 1; seed <= MAX_SEED && found < 10; seed++) {
  const key = generateKey(seed);
  const kb = Buffer.alloc(8);
  kb.writeBigUInt64LE(key);
  const key0 = kb[0];
  for (const i of posByByte[key0]) {
    let valid = true;
    for (let k = 0; k < 16; k++) {
      const plain = so[i + k] ^ kb[k % 8];
      if (!HEX.has(plain)) { valid = false; break; }
    }
    if (valid && so[i + 16] === key0) { // null 已由 posByByte 保证
      const buildKey = Buffer.from(so.subarray(i, i + 16)).map((b, k) => b ^ kb[k % 8]).toString('ascii');
      console.log(`\n🎯 找到! seed=${seed} offset=0x${i.toString(16)}`);
      console.log(`key(LE): ${kb.toString('hex')}`);
      console.log(`DPT_BUILD_KEY = "${buildKey}"`);
      found++;
    }
  }
}
if (!found) console.log('未找到 (可能行号 > ' + MAX_SEED + ' 或定制版改了编码)');
