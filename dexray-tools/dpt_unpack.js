#!/usr/bin/env node
// dpt_unpack.js — dpt-shell 加壳 APK 静态脱壳器 (Node.js 移植自 dpt-unpacker, MIT)
//
// 原理: dpt-shell 打包时把真实方法指令体加密进 assets/OoooooOooo,
//       原始 dex 被 zip 追加到 stub classes.dex 末尾; AES 密钥存在 loader so 的
//       DPT_UNKNOWN_DATA 符号里; 配置 JSON 用 AES-128-CBC 加密在 assets/d_shell_data_001。
// 本工具静态还原: 找 key → 解密配置 → 解析 code store → 提取原始 dex → 回填方法体。
//
// 用法:
//   node dpt_unpack.js <packed.apk> [-o outdir]
//   或作为模块: const { unpack } = require('./dpt_unpack.js')

const fs = require('fs');
const path = require('path');
const zlib = require('zlib');
const crypto = require('crypto');

const KEY_SYMBOL = 'DPT_UNKNOWN_DATA';
const KEY_SIZE = 16;
const IV_OVERRIDES = { 3: 0x2f, 9: 0x76 };
const SHELL_CONFIG_PATH = 'assets/d_shell_data_001';
const CODE_STORE_PATH = 'assets/OoooooOooo';

// =====================================================================
// ZIP 工具 (零依赖: 直接解析 zip 结构, 支持 stored + deflate)
// =====================================================================
function readZipCentralDirectory(buf) {
  // 从尾部找 EOCD (PK\x05\x06)
  let eocd = -1;
  const minLen = 22;
  for (let i = buf.length - minLen; i >= Math.max(0, buf.length - 65557); i--) {
    if (buf.readUInt32LE(i) === 0x06054b50) { eocd = i; break; }
  }
  if (eocd < 0) throw new Error('ZIP: End of Central Directory not found');
  const cdCount = buf.readUInt16LE(eocd + 10);
  const cdSize = buf.readUInt32LE(eocd + 12);
  const cdOffset = buf.readUInt32LE(eocd + 16);
  const entries = [];
  let off = cdOffset;
  for (let i = 0; i < cdCount; i++) {
    if (off + 46 > buf.length || buf.readUInt32LE(off) !== 0x02014b50) {
      throw new Error('ZIP: bad central directory entry @' + off);
    }
    const method = buf.readUInt16LE(off + 10);
    const compSize = buf.readUInt32LE(off + 20);
    const uncompSize = buf.readUInt32LE(off + 24);
    const nameLen = buf.readUInt16LE(off + 28);
    const extraLen = buf.readUInt16LE(off + 30);
    const commentLen = buf.readUInt16LE(off + 32);
    const localOff = buf.readUInt32LE(off + 42);
    const name = buf.toString('utf8', off + 46, off + 46 + nameLen);
    entries.push({ name, method, compSize, uncompSize, localOff, extraLen, commentLen });
    off += 46 + nameLen + extraLen + commentLen;
  }
  return entries;
}

function zipExtract(buf, entry) {
  // 读 local header 找数据起点
  const off = entry.localOff;
  if (buf.readUInt32LE(off) !== 0x04034b50) throw new Error('ZIP: bad local header @' + off);
  const nameLen = buf.readUInt16LE(off + 26);
  const extraLen = buf.readUInt16LE(off + 28);
  const dataOff = off + 30 + nameLen + extraLen;
  const data = buf.subarray(dataOff, dataOff + entry.compSize);
  if (entry.method === 0) return Buffer.from(data);
  if (entry.method === 8) return zlib.inflateRawSync(data);
  throw new Error('ZIP: unsupported method ' + entry.method);
}

function zipRead(buf, name) {
  const e = readZipCentralDirectory(buf).find(x => x.name === name);
  if (!e) throw new Error('ZIP: entry not found: ' + name);
  return zipExtract(buf, e);
}

// =====================================================================
// ELF 工具: 解析 program headers + 符号表, 读符号指向的数据
// =====================================================================
function parseElf(buf) {
  if (buf.toString('ascii', 0, 4) !== '\x7fELF') throw new Error('not an ELF');
  const is64 = buf[4] === 2;
  if (is64) {
    const phoff = Number(buf.readBigUInt64LE(32));
    const phentsize = buf.readUInt16LE(54);
    const phnum = buf.readUInt16LE(56);
    const shoff = Number(buf.readBigUInt64LE(40));
    const shentsize = buf.readUInt16LE(58);
    const shnum = buf.readUInt16LE(60);
    const shstrndx = buf.readUInt16LE(62);
    const segments = [];
    for (let i = 0; i < phnum; i++) {
      const off = phoff + i * phentsize;
      segments.push({
        type: buf.readUInt32LE(off),
        p_offset: Number(buf.readBigUInt64LE(off + 8)),
        p_vaddr: Number(buf.readBigUInt64LE(off + 16)),
        p_filesz: Number(buf.readBigUInt64LE(off + 32)),
      });
    }
    // 节表 (含 sh_addr 用于 vaddr→offset 备选)
    const sections = [];
    let shstrOff = 0;
    if (shstrndx < shnum) {
      const off = shoff + shstrndx * shentsize;
      shstrOff = Number(buf.readBigUInt64LE(off + 24));
    }
    for (let i = 0; i < shnum; i++) {
      const off = shoff + i * shentsize;
      const nameOff = buf.readUInt32LE(off);
      const type = buf.readUInt32LE(off + 4);
      const addr = Number(buf.readBigUInt64LE(off + 16));
      const secOff = Number(buf.readBigUInt64LE(off + 24));
      const size = Number(buf.readBigUInt64LE(off + 32));
      const entsize = Number(buf.readBigUInt64LE(off + 56));
      let name = '';
      if (shstrOff > 0 && nameOff > 0) {
        let end = shstrOff + nameOff;
        while (end < buf.length && buf[end] !== 0) end++;
        name = buf.toString('utf8', shstrOff + nameOff, end);
      }
      sections.push({ name, type, addr, offset: secOff, size, entsize });
    }
    // 符号表: SYMTAB(2)/DYNSYM(11), 字符串表: STRTAB(3)
    const symbols = [];
    const strSecs = sections.filter(s => s.type === 3);
    for (const sec of sections.filter(s => s.type === 2 || s.type === 11)) {
      if (!sec.entsize || !sec.size) continue;
      const strSec = sections[sec.link] || strSecs.find(s => s.name === (sec.type === 2 ? '.strtab' : '.dynstr'));
      const count = Math.floor(sec.size / sec.entsize);
      for (let i = 0; i < count; i++) {
        const off = sec.offset + i * sec.entsize;
        const nameOff = buf.readUInt32LE(off);
        const info = buf.readUInt8(off + 4);
        const shndx = buf.readUInt16LE(off + 6);
        const value = Number(buf.readBigUInt64LE(off + 8));
        const size = Number(buf.readBigUInt64LE(off + 16));
        let name = '';
        if (strSec && nameOff > 0 && strSec.offset + nameOff < buf.length) {
          let end = strSec.offset + nameOff;
          while (end < buf.length && buf[end] !== 0) end++;
          name = buf.toString('utf8', strSec.offset + nameOff, end);
        }
        if (name) symbols.push({ name, info, shndx, value, size });
      }
    }
    return { is64, segments, sections, symbols };
  }
  // ---- 32 位 ----
  const phoff = buf.readUInt32LE(28);
  const phentsize = buf.readUInt16LE(42);
  const phnum = buf.readUInt16LE(44);
  const shoff = buf.readUInt32LE(32);
  const shentsize = buf.readUInt16LE(46);
  const shnum = buf.readUInt16LE(48);
  const shstrndx = buf.readUInt16LE(50);
  const segments = [];
  for (let i = 0; i < phnum; i++) {
    const off = phoff + i * phentsize;
    segments.push({
      type: buf.readUInt32LE(off),
      p_offset: buf.readUInt32LE(off + 4),
      p_vaddr: buf.readUInt32LE(off + 8),
      p_filesz: buf.readUInt32LE(off + 16),
    });
  }
  const sections = [];
  let shstrOff = 0;
  if (shstrndx < shnum) {
    const off = shoff + shstrndx * shentsize;
    shstrOff = buf.readUInt32LE(off + 16);
  }
  for (let i = 0; i < shnum; i++) {
    const off = shoff + i * shentsize;
    const nameOff = buf.readUInt32LE(off);
    const type = buf.readUInt32LE(off + 4);
    const addr = buf.readUInt32LE(off + 12);
    const secOff = buf.readUInt32LE(off + 16);
    const size = buf.readUInt32LE(off + 20);
    const link = buf.readUInt32LE(off + 24);
    const entsize = buf.readUInt32LE(off + 36);
    let name = '';
    if (shstrOff > 0 && nameOff > 0) {
      let end = shstrOff + nameOff;
      while (end < buf.length && buf[end] !== 0) end++;
      name = buf.toString('utf8', shstrOff + nameOff, end);
    }
    sections.push({ name, type, addr, offset: secOff, size, entsize, link });
  }
  const symbols = [];
  for (const sec of sections.filter(s => s.type === 2 || s.type === 11)) {
    if (!sec.entsize || !sec.size) continue;
    const strSec = sections[sec.link];
    const count = Math.floor(sec.size / sec.entsize);
    for (let i = 0; i < count; i++) {
      const off = sec.offset + i * sec.entsize;
      const nameOff = buf.readUInt32LE(off);
      const value = buf.readUInt32LE(off + 4);
      const size = buf.readUInt32LE(off + 8);
      const info = buf.readUInt8(off + 12);
      const shndx = buf.readUInt16LE(off + 14);
      let name = '';
      if (strSec && nameOff > 0 && strSec.offset + nameOff < buf.length) {
        let end = strSec.offset + nameOff;
        while (end < buf.length && buf[end] !== 0) end++;
        name = buf.toString('utf8', strSec.offset + nameOff, end);
      }
      if (name) symbols.push({ name, info, shndx, value, size });
    }
  }
  return { is64: false, segments, sections, symbols };
}

function vaddrToOffset(elf, vaddr) {
  for (const seg of elf.segments) {
    if (seg.type !== 1) continue; // PT_LOAD
    if (seg.p_vaddr <= vaddr && vaddr < seg.p_vaddr + seg.p_filesz) {
      return seg.p_offset + (vaddr - seg.p_vaddr);
    }
  }
  return null;
}

function readSymbolData(buf, name, size) {
  let elf;
  try { elf = parseElf(buf); } catch { return null; }
  for (const sym of elf.symbols) {
    if (sym.name !== name || sym.size < size) continue;
    let off = vaddrToOffset(elf, sym.value);
    if (off === null) {
      // 备选: 通过节表 sh_addr 转换
      const sec = elf.sections[sym.shndx];
      if (sec && sec.addr && sym.value >= sec.addr && sym.value < sec.addr + sec.size) {
        off = sec.offset + (sym.value - sec.addr);
      }
    }
    if (off === null || off + size > buf.length) continue;
    return buf.subarray(off, off + size);
  }
  return null;
}

// 特征扫描: DPT_UNKNOWN_DATA 是 16 字节随机 key, 但 key[3]=0x20, key[9]=0x74 固定。
// 符号被 strip 时按此特征扫描数据段找候选 (旧版 dpt-shell 适用)。
function findLoaderKeyByScan(elfBuf) {
  const KEY_FEATURES = [{ pos: 3, val: 0x20 }, { pos: 9, val: 0x74 }];
  let elf;
  try { elf = parseElf(elfBuf); } catch { return null; }
  const candidates = [];
  for (const seg of elf.segments) {
    if (seg.type !== 1) continue; // PT_LOAD
    const start = seg.p_offset;
    const end = Math.min(seg.p_offset + seg.p_filesz, elfBuf.length - 16);
    for (let i = start; i < end; i++) {
      let ok = true;
      for (const f of KEY_FEATURES) {
        if (elfBuf[i + f.pos] !== f.val) { ok = false; break; }
      }
      if (ok) candidates.push({ offset: i, key: Buffer.from(elfBuf.subarray(i, i + 16)) });
    }
  }
  if (!candidates.length) return null;
  // 返回第一个候选 (旧版多个 so 时通常只有一个真 key)
  return { key: candidates[0].key, candidates };
}

// =====================================================================
// 工具函数
// =====================================================================
function aesCbcDecrypt(blob, key, iv) {
  const decipher = crypto.createDecipheriv('aes-128-cbc', key, iv);
  let out = Buffer.concat([decipher.update(blob), decipher.final()]);
  // PKCS7 unpad
  if (out.length) {
    const pad = out[out.length - 1];
    if (pad >= 1 && pad <= 16 && out.subarray(out.length - pad).every(b => b === pad)) {
      out = out.subarray(0, out.length - pad);
    }
  }
  return out;
}

function adler32(buf) {
  let a = 1, b = 0;
  const MOD = 65521;
  for (let i = 0; i < buf.length; i++) {
    a = (a + buf[i]) % MOD;
    b = (b + a) % MOD;
  }
  return ((b << 16) | a) >>> 0;
}

function xorBody(data, key) {
  if (!key) return Buffer.from(data);
  const out = Buffer.alloc(data.length);
  for (let i = 0; i < data.length; i++) {
    out[i] = data[i] ^ ((key >> ((i & 3) * 8)) & 0xff);
  }
  return out;
}

// MultiDexCode v2: <H version, <H dexCount, [<I off]*dexCount, dex_data...
// dex_data: <H methodCount, [<I methodIdx, <I size, body]...
function parseCodeStore(blob, insnsXorKey) {
  if (blob.length < 4) throw new Error('code store too short: ' + blob.length);
  const version = blob.readUInt16LE(0);
  const dexCount = blob.readUInt16LE(2);
  if (!(1 <= dexCount && dexCount <= 0xffff) || 4 + dexCount * 4 > blob.length) {
    throw new Error('dex_count=' + dexCount + ' implausible for ' + blob.length + '-byte store');
  }
  const offsets = [];
  for (let i = 0; i < dexCount; i++) offsets.push(blob.readUInt32LE(4 + i * 4));
  const bodiesPerDex = [];
  for (let i = 0; i < dexCount; i++) {
    let off = offsets[i];
    if (off + 2 > blob.length) throw new Error('dex offset[' + i + ']=0x' + off.toString(16) + ' runs past EOF');
    const methodCount = blob.readUInt16LE(off);
    off += 2;
    const bodies = {};
    for (let m = 0; m < methodCount; m++) {
      if (off + 8 > blob.length) throw new Error('truncated record at offset 0x' + off.toString(16));
      const methodIdx = blob.readUInt32LE(off);
      const size = blob.readUInt32LE(off + 4);
      off += 8;
      if (off + size > blob.length) throw new Error('insns size=' + size + ' at 0x' + off.toString(16) + ' runs past EOF');
      bodies[methodIdx] = xorBody(blob.subarray(off, off + size), insnsXorKey);
      off += size;
    }
    bodiesPerDex.push(bodies);
  }
  return { version, bodiesPerDex };
}

// 提取 classes.dex 尾部追加的 zip 里的原始 dex
function extractAppendedDexes(apkBuf) {
  const classes = zipRead(apkBuf, 'classes.dex');
  if (classes.length < 4) throw new Error('classes.dex shorter than 4 bytes');
  const length = classes.readUInt32BE(classes.length - 4);
  if (!(length > 0 && length < classes.length)) {
    throw new Error('classes.dex tail u32 (' + length + ") isn't a valid appended-zip length");
  }
  const tail = classes.subarray(classes.length - length - 4, classes.length - 4);
  if (tail.toString('ascii', 0, 4) !== 'PK\x03\x04') {
    throw new Error('classes.dex tail is not a plaintext ZIP');
  }
  const out = {};
  for (const entry of readZipCentralDirectory(tail)) {
    if (entry.name.endsWith('.dex')) out[entry.name] = zipExtract(tail, entry);
  }
  if (!Object.keys(out).length) throw new Error('appended ZIP contains no dex files');
  return out;
}

// =====================================================================
// DEX 修补 (移植自 dpt-unpacker dex.py)
// =====================================================================
function ulebDecode(buf, off) {
  let r = 0, s = 0;
  while (true) {
    const b = buf[off++];
    r |= (b & 0x7f) << s;
    if (!(b & 0x80)) return [r, off];
    s += 7;
  }
}

function ulebEncode(n) {
  const out = [];
  while (true) {
    let b = n & 0x7f;
    n >>>= 7;
    if (n) out.push(b | 0x80);
    else { out.push(b); return Buffer.from(out); }
  }
}

function patchDex(dexIn, bodies, opts = {}) {
  const registerCount = opts.registerCount || 256;
  const tailPadNops = opts.tailPadNops || 16;
  const stubUnrecovered = opts.stubUnrecovered !== false;
  const dex = Buffer.from(dexIn);
  const classDefsSize = dex.readUInt32LE(0x60);
  const classDefsOff = dex.readUInt32LE(0x64);
  const appended = [];
  let appendedSize = 0;
  let base = dex.length;
  let patched = 0, touched = 0;

  const pad4 = () => { while ((base + appendedSize) % 4 !== 0) { appended.push(Buffer.from([0])); appendedSize += 1; } };
  const pushBuf = (b) => { appended.push(b); appendedSize += b.length; };

  for (let ci = 0; ci < classDefsSize; ci++) {
    const cdOff = classDefsOff + ci * 32;
    const classDataOff = dex.readUInt32LE(cdOff + 24);
    if (classDataOff === 0) continue;

    let o = classDataOff;
    let sfs, ifs, dms, vms, r;
    [sfs, o] = ulebDecode(dex, o);
    [ifs, o] = ulebDecode(dex, o);
    [dms, o] = ulebDecode(dex, o);
    [vms, o] = ulebDecode(dex, o);
    const fieldsStart = o;
    for (let i = 0; i < sfs + ifs; i++) { [, o] = ulebDecode(dex, o); [, o] = ulebDecode(dex, o); }
    const fieldsEnd = o;

    // 第一遍: 扫描是否包含需要修补的方法
    let scanOff = o;
    let anyChange = false;
    for (const count of [dms, vms]) {
      let cum = 0;
      for (let i = 0; i < count; i++) {
        let md, co;
        [md, scanOff] = ulebDecode(dex, scanOff);
        [, scanOff] = ulebDecode(dex, scanOff);
        [co, scanOff] = ulebDecode(dex, scanOff);
        cum += md;
        if (co !== 0 && (cum in bodies || stubUnrecovered)) anyChange = true;
      }
    }
    if (!anyChange) continue;
    touched++;

    // 重建 class_data
    const newCd = [];
    newCd.push(ulebEncode(sfs), ulebEncode(ifs), ulebEncode(dms), ulebEncode(vms));
    newCd.push(dex.subarray(fieldsStart, fieldsEnd));

    for (const count of [dms, vms]) {
      let cum = 0;
      for (let i = 0; i < count; i++) {
        let md, af, co;
        [md, o] = ulebDecode(dex, o);
        [af, o] = ulebDecode(dex, o);
        [co, o] = ulebDecode(dex, o);
        cum += md;
        let newCo = co;

        if (stubUnrecovered && co !== 0 && !(cum in bodies)) {
          const stub = Buffer.alloc(14);
          stub.writeUInt16LE(1, 0); stub.writeUInt16LE(0, 2); stub.writeUInt16LE(0, 4); stub.writeUInt16LE(0, 6);
          stub.writeUInt32LE(0, 8); stub.writeUInt32LE(1, 12);
          // insns: return-void (0x2700) 追加
          const stubFull = Buffer.concat([stub, Buffer.from([0x27, 0x00])]);
          pad4();
          newCo = base + appendedSize;
          pushBuf(stubFull);
        }

        if ((cum in bodies) && co !== 0) {
          let body = Buffer.from(bodies[cum]);
          if (body.length % 2) body = Buffer.concat([body, Buffer.from([0])]);
          body = Buffer.concat([body, Buffer.alloc(tailPadNops * 2)]);
          const insnsUnits = body.length / 2;
          const ins = dex.readUInt16LE(co + 2);
          const outs = dex.readUInt16LE(co + 4);
          const dbg = dex.readUInt32LE(co + 8);
          const newCi = Buffer.alloc(16);
          newCi.writeUInt16LE(registerCount, 0);
          newCi.writeUInt16LE(ins, 2);
          newCi.writeUInt16LE(outs, 4);
          newCi.writeUInt16LE(0, 6); // tries_size
          newCi.writeUInt32LE(dbg, 8);
          newCi.writeUInt32LE(insnsUnits, 12);
          pad4();
          newCo = base + appendedSize;
          pushBuf(newCi);
          pushBuf(body);
          patched++;
        }

        newCd.push(ulebEncode(md), ulebEncode(af), ulebEncode(newCo));
      }
    }

    const newClassDataOff = base + appendedSize;
    pushBuf(Buffer.concat(newCd));
    dex.writeUInt32LE(newClassDataOff, cdOff + 24);
  }

  const full = Buffer.concat([dex, ...appended]);
  full.writeUInt32LE(full.length, 0x20); // file_size
  const sig = crypto.createHash('sha1').update(full.subarray(32)).digest();
  sig.copy(full, 12);
  full.writeUInt32LE(adler32(full.subarray(12)), 8); // checksum
  return { dex: full, patched, touched };
}

// =====================================================================
// 主流程
// =====================================================================
function unpack(apkPath) {
  const apkBuf = fs.readFileSync(apkPath);
  const entries = readZipCentralDirectory(apkBuf);
  const names = new Set(entries.map(e => e.name));
  for (const p of [SHELL_CONFIG_PATH, CODE_STORE_PATH]) {
    if (!names.has(p)) throw new Error(apkPath + ': missing ' + p);
  }
  const configBlob = zipExtract(apkBuf, entries.find(e => e.name === SHELL_CONFIG_PATH));
  const storeBlob = zipExtract(apkBuf, entries.find(e => e.name === CODE_STORE_PATH));

  // 找 loader key: 先按符号 (DPT_UNKNOWN_DATA), 再按特征扫描 (key[3]=0x20, key[9]=0x74)
  let loader = null;
  for (const e of entries) {
    if (!(e.name.startsWith('lib/') || e.name.startsWith('assets/'))) continue;
    if (e.uncompSize < 1024 || e.uncompSize > 50 * 1024 * 1024) continue;
    let elfBuf;
    try { elfBuf = zipExtract(apkBuf, e); } catch { continue; }
    if (elfBuf.toString('ascii', 0, 4) !== '\x7fELF') continue;
    const keyBytes = readSymbolData(elfBuf, KEY_SYMBOL, KEY_SIZE) ||
      (() => { const s = findLoaderKeyByScan(elfBuf); return s ? s.key : null; })();
    if (!keyBytes) continue;
    const iv = Buffer.from(keyBytes);
    for (const [pos, val] of Object.entries(IV_OVERRIDES)) iv[Number(pos)] = val;
    loader = { libEntry: e.name, aesKey: Buffer.from(keyBytes), aesIv: iv };
    break;
  }
  // 检测 NDPT 格式 (新版 dpt-shell)
  const ndpt = storeBlob.toString('ascii', 0, 4) === 'NDPT' || configBlob.toString('ascii', 0, 4) === 'NDPT';

  // 无论新旧版, 先提取原始 dex (从 classes.dex 尾部 zip, 无需密钥)
  let innerDexes;
  try {
    innerDexes = extractAppendedDexes(apkBuf);
  } catch (e) {
    if (ndpt) throw new Error('NDPT dpt-shell: 提取原始 dex 失败: ' + e.message);
    throw e;
  }

  // 新版 NDPT: 方法体在加密 code store (build-key HMAC), 静态无法回填
  // 但仍交付原始 dex (完整类/方法/字符串), 方法体建议动态 dump
  if (ndpt || !loader) {
    const dexes = Object.entries(innerDexes)
      .sort((a, b) => a[0].length - b[0].length || (a[0] < b[0] ? -1 : 1))
      .map(([name, stripped]) => {
        // 简要统计类数/方法数 (读取 dex header)
        let stat = null;
        try {
          const s = stripped;
          const classDefsSize = s.readUInt32LE(0x60);
          const methodIdsSize = s.readUInt32LE(0x58);
          stat = { classes: classDefsSize, methods: methodIdsSize };
        } catch {}
        return { name, stripped, patched: null, stat,
          bodiesAvailable: 0, methodsPatched: 0, classesTouched: 0 };
      });
    return {
      loaderLib: loader ? loader.libEntry : null,
      aesKey: loader ? loader.aesKey : null,
      aesIv: loader ? loader.aesIv : null,
      shellConfig: null,
      codeStore: null,
      dexes,
      partial: true,
      note: ndpt
        ? 'NDPT 新版 dpt-shell: 方法体在加密 code store (build-key HMAC), 静态回填不可得。已提取原始 dex(类/方法/字符串完整); 方法体需动态 dump → 见 APK/unpack-tools/DYNAMIC_UNPACK.md (BlackDex/Frida-dexdump)。'
        : '未找到 loader 密钥, 已提取原始 dex 结构; 方法体需动态 dump → 见 APK/unpack-tools/DYNAMIC_UNPACK.md。',
    };
  }

  const decrypted = aesCbcDecrypt(configBlob, loader.aesKey, loader.aesIv);
  const configText = decrypted.toString('utf8');
  let cfg;
  try { cfg = JSON.parse(configText); } catch { throw new Error('decrypted shell config is not valid JSON'); }

  const store = parseCodeStore(storeBlob, cfg.insns_xor_key || 0);

  const dexes = Object.entries(innerDexes)
    .sort((a, b) => a[0].length - b[0].length || (a[0] < b[0] ? -1 : 1))
    .map(([name, stripped], idx) => {
      const bodies = idx < store.bodiesPerDex.length ? store.bodiesPerDex[idx] : {};
      const { dex: patched, patched: nMethods, touched: nClasses } = patchDex(stripped, bodies);
      return {
        name,
        stripped,
        patched,
        bodiesAvailable: Object.keys(bodies).length,
        methodsPatched: nMethods,
        classesTouched: nClasses,
      };
    });

  return {
    loaderLib: loader.libEntry,
    aesKey: loader.aesKey,
    aesIv: loader.aesIv,
    shellConfig: cfg,
    codeStore: store,
    dexes,
    partial: false,
    note: null,
  };
}

// =====================================================================
// CLI
// =====================================================================
if (require.main === module) {
  const args = process.argv.slice(2);
  const apk = args.find(a => !a.startsWith('-'));
  const outIdx = args.indexOf('-o');
  const outdir = outIdx >= 0 && args[outIdx + 1] ? args[outIdx + 1] : 'dpt_unpacked';
  if (!apk || !fs.existsSync(apk)) {
    console.error('用法: node dpt_unpack.js <packed.apk> [-o outdir]');
    process.exit(1);
  }
  fs.mkdirSync(outdir, { recursive: true });
  try {
    const result = unpack(apk);
    if (result.shellConfig) {
      fs.writeFileSync(path.join(outdir, 'shell_config.json'), JSON.stringify(result.shellConfig, null, 2));
    }
    for (const d of result.dexes) {
      fs.writeFileSync(path.join(outdir, d.name), d.stripped);
      if (d.patched) {
        const stem = d.name.replace(/\.dex$/, '');
        fs.writeFileSync(path.join(outdir, stem + '_patched.dex'), d.patched);
        console.log(`${d.name}: patched ${d.methodsPatched}/${d.bodiesAvailable} bodies in ${d.classesTouched} classes`);
      } else {
        const stat = d.stat ? `, 类=${d.stat.classes} 方法id=${d.stat.methods}` : '';
        console.log(`${d.name}: 提取原始 dex (${d.stripped.length} bytes${stat}, 方法体待动态 dump)`);
      }
    }
    if (result.loaderLib) console.log('loader: ' + result.loaderLib);
    if (result.note) console.log('note: ' + result.note);
    console.log('wrote ' + outdir + '/');
  } catch (e) {
    console.error('error: ' + e.message);
    process.exit(1);
  }
}

module.exports = { unpack, patchDex, parseCodeStore, extractAppendedDexes, readZipCentralDirectory, zipExtract, zipRead, parseElf, ulebDecode, ulebEncode, adler32, findLoaderKeyByScan };
