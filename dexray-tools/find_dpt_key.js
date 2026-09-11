// 从 dpt-shell loader so 中搜索 AES 密钥 (DPT_UNKNOWN_DATA)
// 方法: 遍历 PT_LOAD 数据段所有 16 字节窗口作为 key, 解密 d_shell_data_001, 验证是否 JSON
const fs = require('fs');
const crypto = require('crypto');

const soPath = process.argv[2];
const cfgPath = process.argv[3];
const soBuf = fs.readFileSync(soPath);
const cfgBlob = fs.readFileSync(cfgPath);

// 解析 ELF64 program headers 找可读段
function getSegments(buf) {
  const is64 = buf[4] === 2;
  const segments = [];
  if (is64) {
    const phoff = Number(buf.readBigUInt64LE(32));
    const phentsize = buf.readUInt16LE(54);
    const phnum = buf.readUInt16LE(56);
    for (let i = 0; i < phnum; i++) {
      const off = phoff + i * phentsize;
      segments.push({
        type: buf.readUInt32LE(off),
        p_offset: Number(buf.readBigUInt64LE(off + 8)),
        p_vaddr: Number(buf.readBigUInt64LE(off + 16)),
        p_filesz: Number(buf.readBigUInt64LE(off + 32)),
      });
    }
  }
  return segments;
}

function tryDecrypt(key, iv) {
  try {
    const d = crypto.createDecipheriv('aes-128-cbc', key, iv);
    let out = Buffer.concat([d.update(cfgBlob), d.final()]);
    // PKCS7
    const pad = out[out.length - 1];
    if (pad >= 1 && pad <= 16 && out.subarray(out.length - pad).every(b => b === pad)) out = out.subarray(0, out.length - pad);
    const t = out.toString('utf8');
    if (t.startsWith('{') && t.includes('app_name') && t.includes('insns_xor_key')) return t;
  } catch {}
  return null;
}

const segments = getSegments(soBuf);
console.log('段数:', segments.length);
const candidates = [];
// 只搜 PT_LOAD 段的文件数据 (整个文件基本都在段内)
for (const seg of segments) {
  if (seg.type !== 1) continue;
  const start = seg.p_offset;
  const end = Math.min(seg.p_offset + seg.p_filesz, soBuf.length - 16);
  console.log(`搜索段 offset 0x${start.toString(16)}-0x${end.toString(16)} (${(end-start)/1024|0}KB)`);
  for (let i = start; i < end; i++) {
    const key = soBuf.subarray(i, i + 16);
    // 尝试 IV = key
    let r = tryDecrypt(key, key);
    if (r) { console.log('✅ 找到 key @0x' + i.toString(16) + ' (IV=key): ' + r.slice(0,80)); process.exit(0); }
    // 尝试 IV = key + 覆盖
    const iv = Buffer.from(key);
    iv[3] = 0x2f; iv[9] = 0x76;
    r = tryDecrypt(key, iv);
    if (r) { console.log('✅ 找到 key @0x' + i.toString(16) + ' (IV覆盖): ' + r.slice(0,80)); process.exit(0); }
  }
}
console.log('未找到密钥');
