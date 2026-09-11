# APK 脱壳知识库 (DeXRay AI · 技能储备)

> 用途: APK 加壳识别 → 脱壳原理 → 静态/动态脱壳方法 → 工具链 → 实战流程
> 集成: DeXRay MCP 服务器 (`mcp_server.js`) + 命令行工具 (`dpt_unpack.js`)

---

## 1. 什么是 APK 加壳 / 脱壳

**加壳 (Packing/Shell)**: 把真实 DEX 代码加密/抽离, 替换为小体积 stub, 运行时由
native loader 解密加载。静态分析看不到真实代码。

**脱壳 (Unpacking)**: 还原真实 DEX。
- **静态脱壳**: 从 APK 文件本身提取 (依赖壳格式逆向, 无需运行)
- **动态脱壳**: 运行时从内存 dump 解密后的 DEX (通用, 需要设备/模拟器)

## 2. 常见 Android 壳类型速查

| 壳 | 特征 | 脱壳方式 |
|----|------|---------|
| **360 加固** | libjiagu.so, assets/jiagu/ | 动态 (BlackDex/FART) |
| **爱加密 (ijiami)** | libexec.so, assets/ajm/ | 动态 |
| **梆梆 (Bangcle)** | libSecShell.so, assets/secData0 | 动态 |
| **腾讯乐固** | libshella-*.so, libshella.so | 动态 |
| **dpt-shell (旧版)** | assets/OoooooOooo + d_shell_data_001, 无 NDPT 头 | **静态 (dpt_unpack.js)** |
| **dpt-shell (新版 NDPT)** | 同上 + 文件头 "NDPT" + build-key HMAC | 部分静态 (提取原始dex) + 动态 |
| **Flutter 应用** | libapp.so (Dart AOT), libflutter.so | 非壳; 分析 AOT 快照 |

**快速识别**: `apk_pack` MCP 工具 / 检查 `assets/` + `lib/` 下特征文件名。

## 3. dpt-shell 脱壳原理 (深度)

### 3.1 加壳过程 (打包端 dpt 工具)

1. 遍历每个 dex, 把受保护方法的**指令字节 (insns)** 抽离
2. 抽离的方法体加密存入 `assets/OoooooOooo` (MultiDexCode 格式)
3. 原方法位置替换为垃圾数据或 `return-void` stub
4. 原始 dex 全部 zip 压缩, **追加到 stub `classes.dex` 末尾**
   (stub 最后 4 字节 = 大端 u32, 指向追加 zip 起点)
5. shell 配置 JSON (AES 加密) 存 `assets/d_shell_data_001`
6. loader native 库 (`lib<随机名>.so`) 负责运行时解密 + 回填方法体

### 3.2 文件结构

```
APK
├── classes.dex          # stub (代理类) + 尾部追加的原始 dex zip
├── assets/
│   ├── OoooooOooo       # 方法体存储 (旧版明文/新版NDPT加密)
│   ├── d_shell_data_001 # shell 配置 (AES 加密 JSON, 含 insns_xor_key)
│   └── vwwwwwvwww/.../lib<随机>.so  # native loader (含 DPT_UNKNOWN_DATA)
```

### 3.3 密钥机制

**旧版 (≤2025-11)**:
- AES-128 key = `DPT_UNKNOWN_DATA` 符号 (loader so 中 16 字节)
- IV = key 覆盖 `[3]=0x2F, [9]=0x76`
- 配置解密: AES-128-CBC(key=DPT_UNKNOWN_DATA, iv=generateIV(key))
- `insns_xor_key` 从解密后的 config JSON 读取, 用于方法体 XOR 还原

**新版 (≥2025-11, NDPT 头)**:
- 文件头: `"NDPT"` + 类型/版本 + 16 字节 salt
- 配置解密: AES-256-CBC
- key = **HMAC-SHA256(DPT_UNKNOWN_DATA, packageName + "_" + DPT_BUILD_KEY)**
- `DPT_BUILD_KEY` = 打包时 `Long.toHexString(secureRandom.nextLong())` (1-16 位小写 hex)
- `DPT_BUILD_KEY` 编译进 .so, 经 **8字节循环 XOR + MurmurHash3** 混淆
  (`Andoryuuta/obfuscate` 库: `data[i] ^= key[(i%8)*8位]`, key=generate_key(seed))

### 3.4 静态脱壳步骤 (dpt_unpack.js 实现)

```
1. 读取 classes.dex 尾部 u32 → 提取追加 zip → 原始 dex ✅ (无需密钥!)
2. 扫描 loader so 找 DPT_UNKNOWN_DATA 符号 (16B AES key)
   - 符号被 strip → 特征扫描 (key[3]=0x20, key[9]=0x74)
3. AES 解密 d_shell_data_001 → shell 配置 JSON (insns_xor_key)
4. 解析 OoooooOooo (MultiDexCode v2: version/dexCount/offsets/方法体)
   - 方法体按 insns_xor_key 做 4 字节循环 XOR
5. 解析原始 dex 的 class_data, 对每个 stub 方法:
   - 从 code store 取真实方法体 → 重建 code_item (registers=256)
   - 未恢复的方法 → 替换 return-void stub
6. 重算 dex 校验和 (SHA1@0x0C + Adler32@0x08)
```

### 3.5 新版 NDPT 限制

- 方法体在加密 code store (build-key HMAC), **静态不可回填**
- `apk_unpack` 仍提取**原始 dex** (类/方法/字段/字符串完整, 可分析)
- 方法体还原 → **动态脱壳** (BlackDex/Frida-dexdump)

## 4. 脱壳工具链

### 4.1 DeXRay 自带 (本目录)

| 工具 | 类型 | 用途 |
|------|------|------|
| `dpt_unpack.js` | 静态 | dpt-shell 脱壳 (旧版完整/新版提取原始dex) |
| `dex_parser.js` | 分析 | dex 类/方法/字段解析 |
| `elf_parser.js` | 分析 | ELF/SO 节表/符号分析 |
| `aot_index.js` | 分析 | Flutter AOT 字符串索引 |
| `apk_tool.js` | 修改 | APK 解包/重打包 |
| `sign_apk.js` | 修改 | v1 JAR 签名 |
| `APK/unpack-tools/BlackDex64.apk` | 动态 | 免 root 内存脱壳 (Android 5-12) |
| `APK/unpack-tools/frida-dexdump-agent.js` | 动态 | Frida 内存 dump |

### 4.2 MCP 工具 (mcp_server.js)

| 工具 | 说明 |
|------|------|
| `apk_pack` | 壳类型判断 (含 NDPT 识别) |
| `apk_unpack` | 脱壳 (静态完整/部分) |
| `apk_info` / `apk_strings` / `dex_classes` / `apk_extract` | 分析 |
| `elf_analyze` | SO 分析 |
| `unpack_guide` | 脱壳知识查询 |

## 5. 完整脱壳工作流

### 场景 A: dpt-shell 旧版 (静态一键)

```sh
node dpt_unpack.js <packed.apk> -o out/
# out/ 下: classes_patched.dex (完整) + shell_config.json
node dex_parser.js out/classes_patched.dex    # 分析
jadx out/classes_patched.dex                   # 反编译
```

### 场景 B: dpt-shell 新版 NDPT (部分静态 + 动态)

```sh
# 1) 静态: 提取原始 dex (类/方法/字符串完整)
node dpt_unpack.js <packed.apk> -o out/

# 2) 动态: 还原方法体 (任选)
# 方案 B1: BlackDex (免root)
#   安装 BlackDex64.apk → Select APK → Dump it → 结果在 /sdcard/BlackDex/
# 方案 B2: frida-dexdump (需root+frida)
#   pip3 install frida-dexdump; frida-dexdump -f <packageName>
# 方案 B3: 运行时取原始 dex
#   ls /data/data/<pkg>/code_cache/  (dpt-shell 会解压原始dex)

# 3) 合并分析: 动态 dump 的 dex + 静态提取的 dex 交叉验证
```

### 场景 C: 其他壳 / 未知壳

```sh
# 1) 识别
apk_pack <apk>        # MCP 或检查特征文件
# 2) 通用动态脱壳 (BlackDex / FART / Youpk)
# 3) 内存 dump 后用 dex_parser 分析
```

## 6. 实战案例: 领克车机助手 v2.0.7

```
壳: 定制 dpt-shell (新版 NDPT)
特征: assets/OoooooOooo + d_shell_data_001 均以 "NDPT" 开头
     loader: assets/vwwwwwvwww/arm64/lib3f73cd8b395af3ae.so
结果: apk_unpack 提取原始 dex:
  classes.dex  2,689,788 B (2314 类, 17373 方法id, 17759 字符串)
  classes2.dex 15,296 B    (51 类, 233 方法id)
  含 cn.navitool 完整代码结构 / HUD 服务路径 / shell 脚本等线索
下一步: BlackDex 动态 dump 还原方法体
```

## 7. 破解技巧储备 (定制壳对抗)

| 技术 | 说明 |
|------|------|
| **已知明文攻击** | 在 .so 中搜已知字符串 (如 "assets/OoooooOooo") 的混淆版本, 反推 XOR key |
| **MurmurHash3 求逆** | fmix64 是双射, 从已知 key 反推 seed (需枚举 2^8 bit0 组合) |
| **特征扫描** | DPT_UNKNOWN_DATA 固定字节 (旧版 key[3]=0x20, key[9]=0x74) |
| **约束求解** | build key 是 hex → XOR 后必须全为 [0-9a-f] + null 结尾 |
| **尾部 zip 提取** | classes.dex 最后 4 字节大端长度 → 追加 zip (无需密钥!) |

## 8. FAQ / 排查

| 问题 | 解决 |
|------|------|
| 找不到 DPT_UNKNOWN_DATA | .so 被 strip; 用特征扫描或动态脱壳 |
| config 解密失败 | 检查是否为 NDPT 新版 (build-key HMAC, 需动态) |
| 提取的 dex 方法体是垃圾 | 方法体在 code store, 需动态 dump 或 code store 解密 |
| BlackDex 无法运行 | Android 13+ 用 frida-dexdump; 确认悬浮窗权限 |
| frida 连接失败 | frida-server 版本必须与电脑 frida 完全一致 |
| 多 dex 应用 | 每个 classes*.dex 都要处理 |

---

*知识库版本: v1.0 (2026-09) · 基于 DeXRay 实际逆向领克车机助手 dpt-shell 的经验沉淀*
