# 动态脱壳指南 (针对新版 NDPT dpt-shell / 定制壳)

静态脱壳 (`apk_unpack`) 无法回填方法体时, 用动态脱壳在设备上从内存 dump 真实 DEX。

## 方案 A: BlackDex (推荐, 免 root, 几秒钟)

`BlackDex64.apk` (v3.2) 支持 Android 5.0~12, 无需 root/模拟器环境。

1. 安装并打开 BlackDex (需要 悬浮窗 + 存储 权限)
2. 在主界面点击 **"Select APK"** → 选择目标 APK (如 领克车机助手)
3. 点击 **"Dump it!"**
4. 等待 dump 完成 (几秒~几十秒), 查看 dump 日志
5. 输出的 dex 在 `/sdcard/BlackDex/` 或应用内提示的路径:
   - `dump_<时间戳>/<包名>_classes.dex` 等
6. 把这些 dex 拷出, 即可用 jadx / dex_parser / apktool 分析

> 若设备是 Android 13+ 或 BlackDex 无法运行, 用方案 B。

## 方案 B: frida-dexdump (需要 Frida + root/模拟器)

`frida-dexdump-agent.js` 是从内存搜索并 dump dex 的 Frida 脚本。

1. 电脑安装 frida:
   ```sh
   pip3 install frida frida-tools frida-dexdump
   ```
2. 手机/模拟器:
   - root 设备装 `frida-server` (与电脑 frida 版本一致)
   - 或模拟器支持 frida (Genymotion/雷电 + frida-server)
3. 运行目标 APK, 然后:
   ```sh
   # dump 前台应用
   frida-dexdump -FU
   # 或指定包名 (spawn 模式, 能抓到加载早期 dex)
   frida-dexdump -f cn.navitool
   ```
4. 输出 dex 在 `./<时间戳>/` 目录

## 方案 C: 直接取应用运行时的原始 dex (零工具)

dpt-shell 运行时会把 classes.dex 尾部 zip 里的原始 dex 解压到应用数据目录:

```sh
# 设备上 (需 adb/root 或应用自身可读)
adb shell
su
ls /data/data/cn.navitool/app_code_cache/ 2>/dev/null
ls /data/data/cn.navitool/code_cache/ 2>/dev/null
# 或 shell 写入的 dex:
ls /data/local/tmp/navitool-*/ 2>/dev/null
```

找到的 `classes*.dex` 即原始 dex (方法体同样被 stub, 需配合 OoooooOooo 或内存 dump)。

## 脱壳后分析

```sh
# DeXRay 自带 dex 解析器
node /storage/emulated/0/MT2/apks/dexray/dex_parser.js dump_xxx/classes.dex
node /storage/emulated/0/MT2/apks/dexray/dex_parser.js dump_xxx/classes.dex --json
# 或用 jadx 直接打开 dex 反编译
jadx dump_xxx/classes.dex
```

## 常见问题

| 问题 | 解决 |
|------|------|
| BlackDex "Dump failed" | 确认已授予悬浮窗/存储权限; 换 64 位版本 (BlackDex64.apk) |
| frida 连接失败 | frida-server 版本必须与电脑 frida 完全一致; 确认 root |
| dump 出的 dex 无法解析 | 可能只 dump 到部分 dex; 用 `-f` spawn 模式重试 |
| 多 dex 应用 | dump 输出多个 classes*.dex, 全部都要分析 |
