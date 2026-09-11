// 约束扫描 + 随机性筛选 (真正的 build key 是随机 hex, 高熵)
const fs = require('fs');
const so = fs.readFileSync('/storage/emulated/0/MT2/lynkco_extracted/assets/vwwwwwvwww/arm64/lib3f73cd8b395af3ae.so');
const HEX = new Set('0123456789abcdef'.split('').map(c=>c.charCodeAt(0)));
const LEN = so.length;

let results = [];
for (let i = 0; i <= LEN - 9; i++) {
  for (let L = 9; L <= 17; L++) {
    if (i + L > LEN) break;
    const nullPhase = (L - 1) % 8;
    const kAtNull = so[i + L - 1];
    if ((kAtNull & 1) !== 1) continue;
    const keyCands = Array.from({length: 8}, () => null);
    keyCands[nullPhase] = [kAtNull];
    let feasible = true;
    for (let k = 0; k < 8; k++) {
      if (keyCands[k]) continue;
      let cands = null;
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
    const total = keyCands.reduce((a,c)=>a*c.length,1);
    if (total > 50000) continue;
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
      // 随机性筛选: 去重字符数
      const uniq = new Set(bk).size;
      results.push({ offset: i, len: L-1, key: Buffer.from(found.keys).toString('hex'), buildKey: bk, uniq });
    }
  }
}
// 按随机性排序, 显示高熵候选
results.sort((a,b) => b.uniq - a.uniq);
console.log('总候选:', results.length);
console.log('高随机性候选 (uniq>=12):');
for (const r of results.filter(r => r.uniq >= 12).slice(0, 30)) {
  console.log(`  0x${r.offset.toString(16)}  len=${r.len}  uniq=${r.uniq}  key=${r.key}  buildKey="${r.buildKey}"`);
}
// 保存所有候选供后续解密验证
const fs2 = require('fs');
fs2.writeFileSync('/storage/emulated/0/MT2/apks/dexray/buildkey_candidates.json', JSON.stringify(results, null, 1));
console.log('全部候选已保存到 buildkey_candidates.json');
