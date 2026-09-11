from __future__ import annotations

import struct
from dataclasses import dataclass


MULTI_DEX_CODE_VERSION = 2


@dataclass
class CodeStore:
    version: int
    bodies_per_dex: list[dict[int, bytes]]

    @property
    def total_methods(self) -> int:
        return sum(len(b) for b in self.bodies_per_dex)


def _xor_body(data: bytes, key: int) -> bytes:
    if key == 0:
        return data
    out = bytearray(len(data))
    for i, b in enumerate(data):
        out[i] = b ^ ((key >> ((i & 3) * 8)) & 0xFF)
    return bytes(out)


def parse(blob: bytes, insns_xor_key: int = 0) -> CodeStore:
    if len(blob) < 4:
        raise ValueError(f"code store too short: {len(blob)} bytes")
    version, dex_count = struct.unpack_from("<HH", blob, 0)
    if not (1 <= dex_count <= 0xFFFF) or 4 + dex_count * 4 > len(blob):
        raise ValueError(f"dex_count={dex_count} implausible for {len(blob)}-byte store")

    offsets = list(struct.unpack_from(f"<{dex_count}I", blob, 4))
    out: list[dict[int, bytes]] = []
    for i, off in enumerate(offsets):
        if off + 2 > len(blob):
            raise ValueError(f"dex offset[{i}]=0x{off:x} runs past EOF")
        method_count = struct.unpack_from("<H", blob, off)[0]
        o = off + 2
        bodies: dict[int, bytes] = {}
        for _ in range(method_count):
            if o + 8 > len(blob):
                raise ValueError(f"truncated record at offset 0x{o:x}")
            method_idx, size = struct.unpack_from("<II", blob, o)
            o += 8
            if o + size > len(blob):
                raise ValueError(f"insns size={size} at 0x{o:x} runs past EOF")
            bodies[method_idx] = _xor_body(blob[o:o + size], insns_xor_key)
            o += size
        out.append(bodies)
    return CodeStore(version=version, bodies_per_dex=out)
