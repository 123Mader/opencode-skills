# dpt-unpacker

A tiny Python tool that pulls the real method bodies out of an APK packed with [dpt-shell](https://github.com/luoyesiqiu/dpt-shell).

## Install

```sh
poetry install
```

## Use

```sh
dpt-unpack packed.apk -o out/
baksmali d out/classes_patched.dex -o smali/
```

## How dpt-shell works

When dpt-shell packs an APK it walks every dex, lifts the protected methods' instruction bytes into `assets/OoooooOooo`, and overwrites the originals with either random garbage or repeating `return-void`. The original dex files (`classes.dex`, `classes2.dex`, …) get zipped and appended to a new stub `classes.dex`; the last 4 bytes of that file are a big-endian `u32` pointing at where the appendix starts.

At runtime a native loader (`libdpt.so`) reads the side-store, decodes it with an AES key called `DPT_UNKNOWN_DATA` that lives inside the lib itself, and patches the original bodies back into ART via method-entry hooks. Static analysis sees gibberish; the app still runs.

```mermaid
flowchart TD
    APK[packed APK]

    APK -->|scan every ELF<br/>under lib/ and assets/| ELF[loader lib]
    ELF -->|read DPT_UNKNOWN_DATA symbol| KEY[16-byte AES key + IV]

    APK -->|read| CFG[assets/d_shell_data_001]
    KEY --> DEC{AES-128-CBC<br/>PKCS7}
    CFG --> DEC
    DEC --> JSON[shell config JSON<br/>insns_xor_key, app_name, …]

    APK -->|read| STORE[assets/OoooooOooo]
    JSON -->|insns_xor_key| STORE
    STORE -->|parse MultiDexCode<br/>and XOR-decode bodies| BODIES["{method_idx → insns}"]

    APK -->|last 4 bytes of classes.dex<br/>= length of appended ZIP| ZIP[appended ZIP]
    ZIP -->|unzip| ORIG[original dexes]

    BODIES --> PATCH[patch each code_item]
    ORIG --> PATCH
    PATCH --> OUT[classes_patched.dex]
```

## License

MIT.
