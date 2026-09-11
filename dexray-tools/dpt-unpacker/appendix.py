from __future__ import annotations

import io
import struct
import zipfile
from pathlib import Path


PK_MAGIC = b"PK\x03\x04"


def extract_appended_dexes(apk_path: str | Path) -> dict[str, bytes]:
    with zipfile.ZipFile(apk_path) as zf:
        classes = zf.read("classes.dex")

    if len(classes) < 4:
        raise ValueError("classes.dex shorter than 4 bytes")
    length = struct.unpack(">I", classes[-4:])[0]
    if not (0 < length < len(classes)):
        raise ValueError(
            f"classes.dex tail u32 ({length}) isn't a valid appended-zip length"
        )
    tail = classes[len(classes) - length - 4 : len(classes) - 4]
    if tail[:4] != PK_MAGIC:
        raise ValueError("classes.dex tail isn't a plaintext ZIP")

    out: dict[str, bytes] = {}
    with zipfile.ZipFile(io.BytesIO(tail)) as zf:
        for name in zf.namelist():
            if name.endswith(".dex"):
                out[name] = zf.read(name)
    if not out:
        raise ValueError("appended ZIP contains no dex files")
    return out
