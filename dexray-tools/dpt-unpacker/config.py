from __future__ import annotations

import json
from dataclasses import dataclass

from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes


@dataclass
class ShellConfig:
    app_name: str
    acf_name: str
    jni_cls_name: str
    app_sign_sha256: str
    dex_sign: str
    insns_xor_key: int
    raw_json: str


def _pkcs7_unpad(buf: bytes) -> bytes:
    if not buf:
        return buf
    pad = buf[-1]
    if 1 <= pad <= 16 and buf[-pad:] == bytes([pad]) * pad:
        return buf[:-pad]
    return buf


def decrypt_config(blob: bytes, aes_key: bytes, aes_iv: bytes) -> ShellConfig:
    decrypted = Cipher(algorithms.AES(aes_key), modes.CBC(aes_iv)).decryptor().update(blob)
    decrypted = _pkcs7_unpad(decrypted)
    try:
        text = decrypted.decode("utf-8")
    except UnicodeDecodeError as e:
        raise ValueError("decrypted shell config isn't UTF-8") from e
    data = json.loads(text)
    return ShellConfig(
        app_name=data.get("app_name", ""),
        acf_name=data.get("acf_name", ""),
        jni_cls_name=data.get("jni_cls_name", ""),
        app_sign_sha256=data.get("app_sign_sha256", ""),
        dex_sign=data.get("dex_sign", ""),
        insns_xor_key=data.get("insns_xor_key", 0),
        raw_json=text,
    )
