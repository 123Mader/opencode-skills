from __future__ import annotations

import hashlib
import struct
import zlib


def uleb_decode(buf: bytes, off: int) -> tuple[int, int]:
    r = s = 0
    while True:
        b = buf[off]
        off += 1
        r |= (b & 0x7F) << s
        if not (b & 0x80):
            return r, off
        s += 7


def uleb_encode(n: int) -> bytes:
    assert n >= 0
    out = bytearray()
    while True:
        b = n & 0x7F
        n >>= 7
        if n:
            out.append(b | 0x80)
        else:
            out.append(b)
            return bytes(out)


def patch(dex_in: bytes,
          bodies: dict[int, bytes],
          *,
          register_count: int = 256,
          tail_pad_nops: int = 16,
          stub_unrecovered: bool = True) -> tuple[bytes, int, int]:
    dex = bytearray(dex_in)
    class_defs_size = struct.unpack_from("<I", dex, 0x60)[0]
    class_defs_off = struct.unpack_from("<I", dex, 0x64)[0]

    appended = bytearray()
    base = len(dex)
    patched = 0
    touched = 0

    for ci in range(class_defs_size):
        cd_off = class_defs_off + ci * 32
        class_data_off = struct.unpack_from("<I", dex, cd_off + 24)[0]
        if class_data_off == 0:
            continue
        o = class_data_off
        sfs, o = uleb_decode(dex, o)
        ifs, o = uleb_decode(dex, o)
        dms, o = uleb_decode(dex, o)
        vms, o = uleb_decode(dex, o)
        fields_start = o
        for _ in range(sfs + ifs):
            _, o = uleb_decode(dex, o)
            _, o = uleb_decode(dex, o)
        fields_end = o

        scan_off = o
        any_change = False
        for count in (dms, vms):
            cum = 0
            for _ in range(count):
                md, scan_off = uleb_decode(dex, scan_off)
                _, scan_off = uleb_decode(dex, scan_off)
                co, scan_off = uleb_decode(dex, scan_off)
                cum += md
                if co != 0 and (cum in bodies or stub_unrecovered):
                    any_change = True
        if not any_change:
            continue
        touched += 1

        new_cd = bytearray()
        new_cd += uleb_encode(sfs)
        new_cd += uleb_encode(ifs)
        new_cd += uleb_encode(dms)
        new_cd += uleb_encode(vms)
        new_cd += dex[fields_start:fields_end]

        for count in (dms, vms):
            cum = 0
            for _ in range(count):
                md, o = uleb_decode(dex, o)
                af, o = uleb_decode(dex, o)
                co, o = uleb_decode(dex, o)
                cum += md
                new_co = co

                if stub_unrecovered and co != 0 and cum not in bodies:
                    stub = struct.pack("<HHHH", 1, 0, 0, 0)
                    stub += struct.pack("<II", 0, 1)
                    stub += b"\x27\x00"
                    while (base + len(appended)) % 4 != 0:
                        appended.append(0)
                    new_co = base + len(appended)
                    appended += stub

                if cum in bodies and co != 0:
                    body = bodies[cum]
                    if len(body) % 2:
                        body += b"\x00"
                    body += b"\x00" * (tail_pad_nops * 2)
                    insns_units = len(body) // 2
                    ins = struct.unpack_from("<H", dex, co + 2)[0]
                    outs = struct.unpack_from("<H", dex, co + 4)[0]
                    dbg = struct.unpack_from("<I", dex, co + 8)[0]
                    new_ci = struct.pack("<HHHH", register_count, ins, outs, 0)
                    new_ci += struct.pack("<II", dbg, insns_units)
                    new_ci += body
                    while (base + len(appended)) % 4 != 0:
                        appended.append(0)
                    new_co = base + len(appended)
                    appended += new_ci
                    patched += 1

                new_cd += uleb_encode(md)
                new_cd += uleb_encode(af)
                new_cd += uleb_encode(new_co)

        new_class_data_off = base + len(appended)
        appended += new_cd
        struct.pack_into("<I", dex, cd_off + 24, new_class_data_off)

    dex += appended
    struct.pack_into("<I", dex, 0x20, len(dex))
    dex[12:32] = hashlib.sha1(bytes(dex[32:])).digest()
    struct.pack_into("<I", dex, 8, zlib.adler32(bytes(dex[12:])))

    return bytes(dex), patched, touched
