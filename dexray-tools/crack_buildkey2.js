// 破解 DPT_BUILD_KEY: 16位hex + null, XOR key 循环8字节, key 每字节bit0=1
// 扫描 .so 中 17 字节窗口, 约束求解 key, 用第二组(8-15)验证
const fs = require('fs');
const soPath = process.argv[2] || '/storage/emulated/0/MT2/lynkco_extracted/assets/vwwwwwvwww/arm64/lib3f73cd8b395af3ae.so';
const so = fs.readFileSync(soPath);
const HEX = '0123456789abcdef';
const hexSet = new Set([...HEX].map(c => c.charCodeAt(0)));
const LEN = so.length;
const WINDOW = 17;

let count = 0;
for (let i = 0; i <= LEN - WINDOW; i++) {
  const b = so;
  // key[0] = b[i+16] (plain[16]=0), 且 bit0=1
  const k0 = b[i + 16];
  if ((k0 & 1) !== 1) continue;
  // 对相位 1..7: key[k] 候选 = {b[i+k]^c | c∈hex, bit0(key[k])=1}
  // 用第一组(0..7)和第二组(8..15)联合验证
  // 先验证: 对每个 k, 存在 hex 字符使 b[i+k]^key[k]∈hex 且 b[i+8+k]^key[k]∈hex
  const keyCands = [[k0]];
  let feasible = true;
  for (let k = 1; k < 8; k++) {
    const cands = [];
    for (const c of hexSet) {
      const keyB = b[i + k] ^ c;
      if ((keyB & 1) !== 1) continue;
      // 第二组验证: plain[8+k] = b[i+8+k] ^ keyB ∈ hex
      if (hexSet.has(b[i + 8 + k] ^ keyB)) cands.push(keyB);
    }
    if (!cands.length) { feasible = false; break; }
    keyCands.push(cands);
  }
  if (!feasible) continue;
  // 枚举候选组合, 完整验证
  const total = keyCands.reduce((a, c) => a * c.length, 1);
  if (total > 5000) continue; // 剪枝: 候选太多跳过(不是build key)
  let foundKey = null;
  function dfs(keys) {
    if (foundKey) return;
    if (keys.length === 8) {
      // 完整验证 17 字节
      for (let k = 0; k < 16; k++) {
        if (!hexSet.has(b[i + k] ^ keys[k % 8])) return;
      }
      if ((b[i + 16] ^ keys[0]) !== 0) return;
      foundKey = keys.slice();
      return;
    }
    for (const c of keyCands[keys.length]) {
      dfs([...keys, c]);
    }
  }
  dfs([]);
  if (foundKey) {
    const buildKey = Buffer.from(so.subarray(i, i + 16)).map((bb, k) => bb ^ foundKey[k % 8]).toString('ascii');
    console.log(`🎯 候选: offset=0x${i.toString(16)}  key=${Buffer.from(foundKey).toString('hex')}  buildKey="${buildKey}"`);
    count++;
  }
}
console.log('总计候选:', count);
