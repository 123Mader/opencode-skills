---
name: apk-rebuild-signing
description: Use when rebuilding or re-signing a modified APK — LSPatch modular shells with overlapped zip entries, jarsigner giving "can't install" errors, apktool build output too large after signing, signature verification fails on Android 11+, or zipalign/aapt2 unavailable on arm64. Trigger words: apk 签名, V2签名, 装不上, 体积膨胀, 重叠条目, jarsigner, apksigner, zipalign.
---

# APK 重建与签名（Termux/arm64 实战版）

## 核心事实

- **Android 11+ / targetSdk≥30 只认 V2+ 签名**；jarsigner 只产 V1 → 必拒装。别用 jarsigner。
- **apksigner 整包重建会把 LSPatch 重叠条目实体化 → 体积翻倍（如 165MB→299MB）**。想保体积必须用 apksig 低层手工注入 V2 块。
- **v3 是 v2 超集，v3+v2 同开会致 v2 失效**。只开 v1+v2 最稳。
- **重叠条目 zip**：Python 3.14 zipfile 直读报 `BadZipFile: Overlapped entries`，需按本地头手工 byte-copy 重建。
- **aapt2 拒 `$` 前缀资源名**（如 AppCompat `$avd_hide_password`）→ 无法 `apktool b` 完整重建；改用 `apktool d -r`（--no-res，manifest/resources 二进制原样）+ smali 注入等价改造。

## 重建-签名流水线

```bash
# 1) 反编译外/内层壳
JAVA_TOOL_OPTIONS="-Xmx4g" apktool d -f amap.apk -o work   # 或 d -r 免 res 重建
# 2) 打包
JAVA_TOOL_OPTIONS="-Xmx4g" apktool b work -o out_unsigned.apk
# 3) 对齐（自写 zipalign_py.py，纯 Python 重排 stored 条目）
python zipalign_py.py out_unsigned.apk aligned.apk
# 4) 签名 —— 按场景二选一：
#    a) 无重叠结构（常规）: uber-apk-signer
java -jar uber-apk-signer.jar -a aligned.apk --ks sign.keystore --ksAlias amap --ksPass 123456 --ksKeyPass 123456 -o signed_out --skipZipAlign
#    b) 保体积 + 重叠结构: apksig 低层注入（V2Sign.java，4096B 占位块插 CD 前，EOCD cdOffset 平移）
JAVA_TOOL_OPTIONS="-Xmx4g" java -cp uber-apk-signer.jar:. V2Sign in.apk out.apk
```

## V2Sign 关键（apksig 低层注入）

- digest 三段 = `apk=[0..cdOff]` + `centralDir=[cdOff..eocd]` + `eocd=[eocd..end]`（恰覆盖全文件，签名块区整体跳过）；别把"块"当参数传，会 chunk 错位。
- 换 keystore：`sed 's/sign\.keystore/mt2.keystore/;s/"amap"/"mt2"/' V2Sign.java` 并改类名再 javac。

## 验证（重要）

```bash
# 用 ApkSignerTool 直调，别用默认 verify（minSdk 要求 v1 时误报 DOES NOT VERIFY）
JAVA_TOOL_OPTIONS="-Xmx2g" java -cp uber-apk-signer.jar com.android.apksigner.ApkSignerTool verify --verbose --min-sdk-version 24 out.apk   # 期望 v2: true
jarsigner -verify out.apk   # v1 侧确认 jar verified
# 快速复查修改：strings classes.dex | grep 标记字段；apktool d 回验 try/catch 完好
```

## 常见坑

| 现象 | 原因 | 修 |
|---|---|---|
| 装不上提示解析失败 | 仅 V1 签名 | 重签 V2 |
| 299MB 膨胀 | 重叠条目被实体化 + V2 块重算 | apksig 低层注入 |
| v2 校验 false | v3 同开 | 禁 v3 只开 v1+v2 |
| Python 读 zip 报 Overlapped | 重叠条目 | deoverlap.py 手工复制 |
| apktool b 报 invalid entry name | `$` 资源 | `d -r` + smali 路线 |
| 覆盖安装失败 | 证书变更（CN/alias） | 先卸载重装 |

## 关联

- 经验卡：`~/.config/opencode/memory-cards/20260809-apk-rebuild-signing.md`
- 环境属性：Termux aarch64，无 apksigner/zipalign，JVM 需 `-Xmx4g`