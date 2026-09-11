// dpt_unpack.js 核心逻辑自测
const assert = require('assert');
const crypto = require('crypto');
const { parseCodeStore, patchDex, ulebEncode, adler32 } = require('./dpt_unpack.js');

let pass = 0, fail = 0;
function t(name, fn) {
  try { fn(); console.log('✅ ' + name); pass++; }
  catch (e) { console.log('❌ ' + name + ': ' + e.message); fail++; }
}

// 1. ULEB128 编解码
t('ULEB128 编码', () => {
  assert.deepStrictEqual(ulebEncode(0), Buffer.from([0]));
  assert.deepStrictEqual(ulebEncode(127), Buffer.from([127]));
  assert.deepStrictEqual(ulebEncode(128), Buffer.from([0x80, 1]));
  assert.deepStrictEqual(ulebEncode(300), Buffer.from([0xac, 2]));
});

// 2. adler32
t('Adler32', () => {
  // Python zlib.adler32(b'123456789') == 0x091E01DE
  assert.strictEqual(adler32(Buffer.from('123456789')), 0x091e01de);
});

// 3. CodeStore 解析
t('CodeStore 解析 (含 XOR)', () => {
  const insns1 = Buffer.from([0x00,0x01,0x02,0x03,0x04,0x05,0x06,0x07]);
  const insns2 = Buffer.from([0x10,0x11,0x12,0x13]);
  const xorKey = 0x12345678;
  const xorBody = (data) => {
    const out = Buffer.alloc(data.length);
    for (let i=0;i<data.length;i++) out[i] = data[i] ^ ((xorKey >> ((i&3)*8)) & 0xff);
    return out;
  };
  const body1 = xorBody(insns1);
  const body2 = xorBody(insns2);
  // 构造: <H version=2, <H dexCount=1, <I off, dex0: <H count=2, [<I idx,<I size,body]...]
  const parts = [];
  const write16 = (v) => { const b=Buffer.alloc(2); b.writeUInt16LE(v); parts.push(b); };
  const write32 = (v) => { const b=Buffer.alloc(4); b.writeUInt32LE(v); parts.push(b); };
  write16(2); write16(1); // version, dexCount
  const offPos = Buffer.concat(parts).length;
  write32(0); // offset 占位 (稍后填)
  const dexOff = Buffer.concat(parts).length;
  // 填充 offset
  write16(2); // methodCount
  write32(10); write32(body1.length); parts.push(body1);
  write32(20); write32(body2.length); parts.push(body2);
  let blob = Buffer.concat(parts);
  blob.writeUInt32LE(dexOff, offPos);
  const store = parseCodeStore(blob, xorKey);
  assert.strictEqual(store.version, 2);
  assert.strictEqual(store.bodiesPerDex.length, 1);
  assert.deepStrictEqual(store.bodiesPerDex[0][10], insns1);
  assert.deepStrictEqual(store.bodiesPerDex[0][20], insns2);
});

// 4. DEX patch (构造最小 dex)
t('DEX Patch 方法体还原', () => {
  // 构造一个最小合法 dex
  const dex = Buffer.alloc(0x300);
  dex.write('dex\n035', 0, 'ascii'); // magic
  // header: file_size@0x20, checksum@0x08, signature@0x0C, class_defs_size@0x60, class_defs_off@0x64
  // class_data_off 在 class_def_item +24
  const classDataOff = 0x100;
  const cdOff = 0x80;
  dex.writeUInt32LE(1, 0x60); // class_defs_size = 1
  dex.writeUInt32LE(cdOff, 0x64); // class_defs_off
  // class_def_item: class_idx=0, access=1, super=0, ifaces=0, src=0, ann=0, class_data_off, svals=0
  dex.writeUInt32LE(0, cdOff);
  dex.writeUInt32LE(1, cdOff+4);
  dex.writeUInt32LE(0, cdOff+8);
  dex.writeUInt32LE(0, cdOff+12);
  dex.writeUInt32LE(0, cdOff+16);
  dex.writeUInt32LE(0, cdOff+20);
  dex.writeUInt32LE(classDataOff, cdOff+24);
  dex.writeUInt32LE(0, cdOff+28);
  // class_data: sfs=0, ifs=0, dms=1, vms=0, method: idx_diff=0, access=1, code_off=0x200 (stub)
  const cd = Buffer.concat([
    ulebEncode(0), ulebEncode(0), ulebEncode(1), ulebEncode(0),
    ulebEncode(0), ulebEncode(1), ulebEncode(0x200)
  ]);
  cd.copy(dex, classDataOff);
  // stub code_item @0x200: registers=1, ins=0, outs=0, tries=0, dbg=0, insns_size=1, insns=return-void
  const ci = Buffer.alloc(18);
  ci.writeUInt16LE(1, 0); ci.writeUInt16LE(0, 2); ci.writeUInt16LE(0, 4); ci.writeUInt16LE(0, 6);
  ci.writeUInt32LE(0, 8); ci.writeUInt32LE(1, 12); ci.writeUInt16LE(0x0000, 16); // nop
  ci.copy(dex, 0x200);
  // 真实方法体: mov 0x0, v0 (0x00 0x10)
  const bodies = { 0: Buffer.from([0x10, 0x00]) };
  const { dex: out, patched, touched } = patchDex(dex, bodies);
  assert.strictEqual(patched, 1, '应该还原 1 个方法体');
  assert.strictEqual(touched, 1, '应该触及 1 个类');
  // 验证新 class_data 中的 code_off 指向新 code_item (在文件末尾)
  const newCdOff = out.readUInt32LE(cdOff + 24);
  // 解析新 class_data 找 code_off
  let o = newCdOff;
  const uleb = (off) => { let r=0,s=0; while(true){const b=out[off++]; r|=(b&0x7f)<<s; if(!(b&0x80)) return [r,off]; s+=7;} };
  let r; [r,o]=uleb(o); [r,o]=uleb(o); [r,o]=uleb(o); [r,o]=uleb(o); // sfs ifs dms vms
  [r,o]=uleb(o); [r,o]=uleb(o); // method idx_diff, access
  let codeOff; [codeOff,o]=uleb(o);
  assert.ok(codeOff >= dex.length, '新 code_off 应指向追加区域');
  // 验证 insns = 0x10 0x00 (原方法体)
  const regs = out.readUInt16LE(codeOff);
  const insnsSize = out.readUInt32LE(codeOff + 12);
  assert.strictEqual(regs, 256, 'registers_size 应为 256');
  assert.ok(insnsSize >= 1);
  assert.strictEqual(out.readUInt16LE(codeOff + 16), 0x0010, 'insns[0] 应为 0x0010');
});

console.log(`\n结果: ${pass} 通过, ${fail} 失败`);
process.exit(fail ? 1 : 0);
