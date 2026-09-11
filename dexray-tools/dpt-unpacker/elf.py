from __future__ import annotations

import io
import zipfile
from dataclasses import dataclass
from pathlib import Path

from elftools.elf.elffile import ELFFile
from elftools.elf.sections import SymbolTableSection


KEY_SYMBOL = "DPT_UNKNOWN_DATA"
KEY_SIZE = 16
IV_OVERRIDES = {3: 0x2F, 9: 0x76}


@dataclass
class LoaderKey:
    aes_key: bytes
    aes_iv: bytes
    lib_entry: str


def _vaddr_to_offset(elf: ELFFile, vaddr: int) -> int | None:
    for segment in elf.iter_segments():
        if segment["p_type"] != "PT_LOAD":
            continue
        start = segment["p_vaddr"]
        end = start + segment["p_filesz"]
        if start <= vaddr < end:
            return segment["p_offset"] + (vaddr - start)
    return None


def _read_symbol(data: bytes, name: str, size: int) -> bytes | None:
    try:
        elf = ELFFile(io.BytesIO(data))
    except Exception:
        return None
    for section in elf.iter_sections():
        if not isinstance(section, SymbolTableSection):
            continue
        for sym in section.iter_symbols():
            if sym.name != name or sym["st_size"] < size:
                continue
            off = _vaddr_to_offset(elf, sym["st_value"])
            if off is None:
                continue
            return data[off:off + size]
    return None


def find_loader_key(apk_path: str | Path) -> LoaderKey | None:
    with zipfile.ZipFile(apk_path) as zf:
        for info in zf.infolist():
            name = info.filename
            if not (name.startswith("lib/") or name.startswith("assets/")):
                continue
            if info.file_size < 1024 or info.file_size > 50 * 1024 * 1024:
                continue
            with zf.open(info) as fh:
                if fh.read(4) != b"\x7fELF":
                    continue
            key_bytes = _read_symbol(zf.read(name), KEY_SYMBOL, KEY_SIZE)
            if key_bytes is None:
                continue
            iv = bytearray(key_bytes)
            for pos, val in IV_OVERRIDES.items():
                iv[pos] = val
            return LoaderKey(
                aes_key=bytes(key_bytes),
                aes_iv=bytes(iv),
                lib_entry=name,
            )
    return None
