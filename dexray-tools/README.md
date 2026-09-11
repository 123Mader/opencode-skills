# DeXRay AI — APK 逆向工作台 (MCP 服务器)

Kelivo 二次开发的 AI 逆向工作台。将 APK 分析/修改能力封装为 **MCP 服务器**，
Kelivo (或其他 MCP 客户端) 通过 HTTP 连接即可让 AI 对话直接分析/修改 APK。

## 架构

```
Kelivo App ──MCP(HTTP)──▶ DeXRay MCP 服务器 (Node, 端口 8791)
                              │
     第一期 · 分析引擎         │
       ├─ apk_info      APK 结构/清单/权限/组件
       ├─ apk_strings   dex 字符串线索
       ├─ apk_pack      脱壳/打包判断
       ├─ apk_review    AI 自然语言解读 (智谱 GLM)
       ├─ apk_extract   文件树
       └─ dex_classes   DEX 类/方法/字段解析 (smali 骨架)
     第二期 · 修改引擎 (独立脚本)
       ├─ apk_tool.js   解包/重打包/替换条目
       └─ sign_apk.js   v1 JAR 签名 (纯 Node RSA, debug)
```

## 启动

```sh
node /storage/emulated/0/MT2/apks/dexray/mcp_server.js [端口=8791]
# 或
sh /storage/emulated/0/MT2/apks/dexray/dexray.sh start|stop|status|restart
```

- 端点: `http://127.0.0.1:8791/mcp`
- 健康: `http://127.0.0.1:8791/health`
- 协议: MCP streamable HTTP, 版本 2025-11-25
- 零依赖 (纯 Node)

## MCP 工具

| 工具 | 说明 | 参数 |
|---|---|---|
| `apk_info` | 包名/版本/组件/权限/结构 | apkPath |
| `apk_strings` | dex 字符串 + URL/密钥线索 | apkPath, keyword? |
| `apk_pack` | 脱壳判断 (360/爱加密/Flutter) | apkPath |
| `apk_review` | AI 解读报告 (智谱 GLM) | apkPath |
| `apk_extract` | zip 文件树 | apkPath, filter? |
| `dex_classes` | 类/方法/字段签名 (smali 骨架) | apkPath, className?, maxClasses? |
| `apk_unpack` | **APK 脱壳**: 静态还原 dpt-shell 加壳 APK 的真实 DEX | apkPath, outDir? |
| `unpack_guide` | **脱壳知识库**: 原理/方法/工具/流程/案例/FAQ 查询 | topic? |

## 脱壳知识库 (技能储备)

`docs/APK_UNPACK_KNOWLEDGE.md` — 完整脱壳知识: 加壳识别 → dpt-shell 新旧版密钥
机制 → 静态/动态脱壳步骤 → 工具链 → 工作流 → 实战案例 (领克车机助手) → 破解
技巧 → FAQ。AI 可通过 MCP 工具 `unpack_guide` 查询 (`topic=原理/方法/动态/工具/
流程/案例/破解/FAQ`)。

## 脱壳模块 (dpt-unpacker 移植)

`dpt_unpack.js` 是 dpt-shell 加壳 APK 的**静态脱壳器** (Node.js 零依赖移植自
[nitanmarcel/dpt-unpacker](https://github.com/nitanmarcel/dpt-unpacker), MIT)：

```sh
node dpt_unpack.js <packed.apk> -o out/     # 还原真实 DEX
```

**原理**: dpt-shell 把真实方法指令体加密进 `assets/OoooooOooo`, 原始 dex 被 zip
追加到 stub `classes.dex` 末尾 (最后 4 字节是大端长度)。脱壳器:
1. 从 loader so 找 `DPT_UNKNOWN_DATA` 符号 (16 字节 AES 密钥; 符号被 strip 时按
   `key[3]=0x20, key[9]=0x74` 特征扫描数据段)
2. AES-128-CBC 解密 `assets/d_shell_data_001` → shell 配置 JSON (含 `insns_xor_key`)
3. 解析 `OoooooOooo` (MultiDexCode v2, XOR 还原方法体)
4. 提取 classes.dex 尾部 zip 里的原始 dex
5. 把方法体回填到 class_data 的 code_item, 重算 SHA1 + Adler32

**已知限制**: 新版 dpt-shell (2025-11 之后, 带 `NDPT` 魔数头 + HMAC-SHA256
build-key 派生) 静态密钥不可得, 但 `apk_unpack` 会**自动提取原始 dex** (从
classes.dex 尾部 zip, 无需密钥 — 类/方法/字段/字符串完整), 方法体回填需动态脱壳。

**动态脱壳工具链** (随附 `APK/unpack-tools/`):
- `BlackDex64.apk` — BlackDex v3.2, 免 root, 设备上几秒 dump 真实 DEX
- `frida-dexdump-agent.js` — Frida 内存 dump 脚本 (需 frida 环境)
- `DYNAMIC_UNPACK.md` — 完整动态脱壳指南 (BlackDex / frida-dexdump / 运行时取 dex)

**脱壳流程**:
```sh
node dpt_unpack.js <packed.apk> -o out/   # 旧版 dpt-shell: 完整还原方法体
                                          # 新版 NDPT: 提取原始 dex (部分脱壳)
```

自测: `node test_unpack.js` (ULEB128 / Adler32 / CodeStore / DEX patch 4 项全过)

## 第二期 · 修改引擎 (独立脚本)

```sh
node dex_parser.js classes.dex          # 类/方法/字段清单 (实测 6112 类)
node dex_parser.js classes.dex --json   # 结构化 JSON
node apk_tool.js unpack app.apk dir     # 解包
node apk_tool.js repack dir app2.apk    # 重打包 (zip 完整实现)
node apk_tool.js replace app.apk META-INF/MANIFEST.MF new.mf out.apk  # 替换条目
node sign_apk.js app.apk signed.apk     # v1 JAR 签名 (debug 自签)
```

**关于重签名**：纯 Node 的 `sign_apk.js` 实现了 v1 JAR 签名结构
(MANIFEST.MF + CERT.SF + RSA 签名)，供离线/学习验证；**正式安装请用官方
apksigner** (云构建 GitHub Actions 环境自带，一条命令)。

## 连接 Kelivo

Kelivo 已内置 DeXRay 注册 (`lib/core/providers/mcp_provider.dart` 的
`_builtinDexrayServerIfMissing`)，启动即自动连接 `http://127.0.0.1:8791/mcp`。

## 测试

```sh
# 健康检查
curl http://127.0.0.1:8791/health

# MCP 工具调用
curl -X POST http://127.0.0.1:8791/mcp -H "Content-Type: application/json" \
  -H "Accept: application/json, text/event-stream" \
  -d '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"apk_info","arguments":{"apkPath":"/path/to.apk"}}}'
```

## 第三期 · 深度引擎

| 工具/脚本 | 能力 | 实测 |
|---|---|---|
| `elf_analyze` (MCP) | SO/ELF 分析: 节表/符号/导入导出/加固特征/AOT 字符串 | ✅ |
| `elf_parser.js` | ELF 解析 (AArch64 实测: 26节/338符号) | ✅ |
| `aot_index.js` | Flutter AOT 字符串索引 (libapp.so 63663 条) | ✅ |

**AOT 索引威力**：从 libapp.so 的 .rodata 直接挖出 App 全部后端 API——
`open.bigmodel.cn`(智谱)、`siliconflow.cn`、`tavily.com` 等，逆向定位后端端点一步到位。

```sh
node elf_parser.js lib.so              # 节/符号/导入导出
node elf_parser.js lib.so --json
node aot_index.js libapp.so            # AOT 字符串索引
node aot_index.js libapp.so --grep api # 搜 API 端点
```

## 路线图

- [x] 第一期: 分析引擎 (info/strings/pack/review/extract/dex_classes)
- [x] 第二期: 修改引擎 (dex 解析 / 解包 / 重打包 / v1 签名)
- [x] 第三期: 深度引擎 (ELF/SO 分析, Flutter AOT 索引)
- [ ] 第四期: 加密特征库 / 重打包写回 libapp.so / v2 签名

## 许可

基于 Kelivo (AGPL-3.0) 二次开发。仅供学习研究，请仅处理你拥有或已获授权的 APK。
