from __future__ import annotations

import argparse
import sys
import zipfile
from pathlib import Path

from . import __version__
from .unpacker import unpack


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(
        prog="dpt-unpack",
        description="Static unpacker for dpt-shell-protected Android APKs.",
    )
    ap.add_argument("--version", action="version", version=__version__)
    ap.add_argument("apk", help="path to a dpt-shell-protected APK")
    ap.add_argument("-o", "--outdir", default="dpt_unpacked",
                    help="output directory")
    args = ap.parse_args(argv)

    outdir = Path(args.outdir)
    outdir.mkdir(parents=True, exist_ok=True)

    try:
        result = unpack(args.apk)
    except (ValueError, OSError, zipfile.BadZipFile) as e:
        print(f"error: {e}", file=sys.stderr)
        return 1

    (outdir / "shell_config.json").write_text(result.shell_config.raw_json)
    for d in result.dexes:
        (outdir / d.name).write_bytes(d.stripped_bytes)
        (outdir / f"{Path(d.name).stem}_patched.dex").write_bytes(d.patched_bytes)
        print(f"{d.name}: patched {d.methods_patched}/{d.bodies_available} "
              f"bodies in {d.classes_touched} classes")

    print(f"wrote {outdir}/")
    return 0
