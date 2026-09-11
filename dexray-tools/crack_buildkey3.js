// 通用扫描: build key = 1..16 位 hex + null, XOR 8字节循环, key每字节bit0=1
// 对每个位置/长度, 用 null 约束 + hex 约束求 key, 完整验证
const fs = require('fs');
const soPath = process.argv[2] || '/storage/emulated/0/MT2/lynkco_extracted/assets/vwwwwwvwww/arm64/lib3f73cd8b395af3ae.so';
const so = fs.readFileSync(soPath);
const HEX = new Set('0123456789abcdef'.split('').map(c=>c.charCodeAt(0)));
const LEN = so.length;

let results = [];
// 尝试长度 9..17 (含null), 常见 build key 长度 8-16 hex
for (let i = 0; i <= LEN - 9; i++) {
  for (let L = 9; L <= 17; L++) {
    if (i + L > LEN) break;
    // null 约束: plain[L-1]=0 → key[(L-1)%8] = so[i+L-1], 且 bit0=1
    const nullPhase = (L - 1) % 8;
    const kAtNull = so[i + L - 1];
    if ((kAtNull & 1) !== 1) continue;
    // 对每个 key 相位 k, 求候选 = 所有位置(同相位, ≤L-2)的 b^c 交集, c∈hex, bit0=1
    const keyCands = Array.from({length: 8}, () => null);
    keyCands[nullPhase] = [kAtNull];
    let feasible = true;
    for (let k = 0; k < 8; k++) {
      if (keyCands[k]) continue;
      let cands = null; // null = 未初始化
      for (let pos = k; pos <= L - 2; pos += 8) {
        const set = new Set();
        for (const c of HEX) {
          const kb = so[i + pos] ^ c;
          if ((kb & 1) === 1) set.add(kb);
        }
        cands = cands === null ? set : new Set([...cands].filter(x => set.has(x)));
        if (!cands.size) break;
      }
      if (!cands || !cands.size) { feasible = false; break; }
      keyCands[k] = [...cands];
    }
    if (!feasible) continue;
    // 枚举组合验证完整
    const total = keyCands.reduce((a,c)=>a*c.length,1);
    if (total > 200000) continue;
    let found = null;
    function dfs(keys) {
      if (found) return;
      if (keys.length === 8) {
        for (let pos = 0; pos <= L - 2; pos++) {
          if (!HEX.has(so[i+pos] ^ keys[pos%8])) return;
        }
        if ((so[i+L-1] ^ keys[(L-1)%8]) !== 0) return;
        found = { keys: keys.slice(), len: L };
        return;
      }
      for (const c of keyCands[keys.length]) dfs([...keys, c]);
    }
    dfs([]);
    if (found) {
      const bk = Buffer.from(so.subarray(i, i + L - 1)).map((b,k)=>b ^ found.keys[k%8]).toString('ascii');
      results.push({ offset: i, len: L-1, key: Buffer.from(found.keys).toString('hex'), buildKey: bk });
    }
  }
}
console.log('找到候选 (' + results.length + '):');
for (const r of results.slice(0, 20)) {
  console.log(`  0x${r.offset.toString(16)}  len=${r.len}  key=${r.key}  buildKey="${r.buildKey}"`);
}
