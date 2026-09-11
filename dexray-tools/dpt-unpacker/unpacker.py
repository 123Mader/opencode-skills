from __future__ import annotations

import zipfile
from dataclasses import dataclass
from pathlib import Path

from . import appendix, codestore, config, dex, elf


SHELL_CONFIG_PATH = "assets/d_shell_data_001"
CODE_STORE_PATH = "assets/OoooooOooo"


@dataclass
class PatchedDex:
    name: str
    stripped_bytes: bytes
    patched_bytes: bytes
    bodies_available: int
    methods_patched: int
    classes_touched: int


@dataclass
class UnpackResult:
    loader_lib: str
    aes_key: bytes
    aes_iv: bytes
    shell_config: config.ShellConfig
    code_store: codestore.CodeStore
    dexes: list[PatchedDex]


def unpack(apk_path: str | Path) -> UnpackResult:
    apk_path = Path(apk_path)

    with zipfile.ZipFile(apk_path) as zf:
        names = set(zf.namelist())
        for path in (SHELL_CONFIG_PATH, CODE_STORE_PATH):
            if path not in names:
                raise ValueError(f"{apk_path}: missing {path}")
        config_blob = zf.read(SHELL_CONFIG_PATH)
        store_blob = zf.read(CODE_STORE_PATH)

    key = elf.find_loader_key(apk_path)
    if key is None:
        raise ValueError(
            f"{apk_path}: no {elf.KEY_SYMBOL} symbol in any bundled ELF"
        )

    shell_config = config.decrypt_config(config_blob, key.aes_key, key.aes_iv)
    store = codestore.parse(store_blob, insns_xor_key=shell_config.insns_xor_key)
    inner_dexes = appendix.extract_appended_dexes(apk_path)

    dexes: list[PatchedDex] = []
    for idx, name in enumerate(sorted(inner_dexes, key=lambda s: (len(s), s))):
        stripped = inner_dexes[name]
        bodies = store.bodies_per_dex[idx] if idx < len(store.bodies_per_dex) else {}
        patched, n_methods, n_classes = dex.patch(stripped, bodies)
        dexes.append(PatchedDex(
            name=name,
            stripped_bytes=stripped,
            patched_bytes=patched,
            bodies_available=len(bodies),
            methods_patched=n_methods,
            classes_touched=n_classes,
        ))

    return UnpackResult(
        loader_lib=key.lib_entry,
        aes_key=key.aes_key,
        aes_iv=key.aes_iv,
        shell_config=shell_config,
        code_store=store,
        dexes=dexes,
    )
