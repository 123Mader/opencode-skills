---
name: vehicle-logcat-signal-debugging
description: Use when VehicleHUD 或类似车机日志信号检测/播报类 App 出现"不播报、状态不触发、检测不到信号"问题 — 涉及 logcat tag 自动探测、属性名前缀归一化、App 自身日志污染探测结果。症状：服务端监听错误 tag、信号被当 unknown 忽略、安装新版本后依然不播报、用户日志显示 discover 结果异常。
---

# 车机 logcat 信号检测调试

## Overview

车机日志信号检测（logcat → tag 探测 → 属性解析 → 状态机 → 播报）链条中，90% 的"不播报"问题不在播放器，而在**上游：tag 探测选错来源**。核心原则：**先验证信号是否到达解析器（unknown prop 日志），再谈映射和播放**。

## 问题现状（2026-08-09 VehicleHUD 实测）

| 版本 | 现象 | 根因 |
|------|------|------|
| 13寸适配版 | 不播报 | `discover()` 用 `logcat -v raw`（无 tag 列），把消息文本当 tag 写入 settings |
| 920099b 修复版 | 仍不播报 | discover 自污染 + 垃圾 tag 永不纠正 + 属性名带前缀 |
| b7e863a 悬浮窗修复版 | 仍不播报 | 同上（只改了 OverlayFormatter 值语义） |
| 5de9f9d（最新） | 待真机验证 | 三连环全修复，48/48 测试绿 |

## 三连环根因（App 日志铁证）

诊断日志：`/storage/emulated/0/MT2/VehicleHUD-log-20260809-194202.txt`（299 行，FileLog 累积含旧版+新版记录）

### 1. discover 自污染
- FileLog 双写 logcat，自身 tag=`VehicleHUD`（FileLog.kt:16），其**消息文本**含 `discover result=onPropertyValue CLUSTER_WARNING_...(557849581)` 字样
- `scan()` 只过滤含 `(` 的伪 tag，`VehicleHUD` 无括号被计数 → 计数压过车机信号 → 服务端监听自己的日志 → 空流不播报
- 日志铁证：`:284 discover result=VehicleHUD lines=3044`

### 2. 垃圾 tag 永不纠正
- 旧版（raw bug）写入 settings 的 `onPropertyValue CLUSTER_WARNING_ESCWARNINDCNREQCNTR(...)` 永久残留
- `discover` 返回 null 时服务端 keep 垃圾 tag（`:165`），下次启动继续监听垃圾

### 3. 门锁属性名带模块前缀
- 车机广播全大写带前缀：`CLUSTER_LOCKGCENSTSLOCKST=3`、`HU_LOCKGCENSTSLOCKST=3`（=解锁）
- PropertyCatalog 只匹配裸驼峰名 `LockgCenStsLockSt` → 信号在但被 unknown 忽略
- 日志铁证：`:8-9 unknown prop=CLUSTER_LOCKGCENSTSLOCKST value=3`
- 注：HoodSts 是裸名所以早期 14:59 版能触发机舱盖，掩盖了门锁问题

## 修复方法（commit 5de9f9d）

### LogTagScanner.scan() — 排除自身 tag
```kotlin
private const val SELF_TAG = "VehicleHUD"  // FileLog 的 tag
// 循环内：
if (tag == SELF_TAG) continue
if (garbageRegex.containsMatchIn(tag)) continue  // 含 ( 的伪 tag
```

### VehicleHudService.startListening() — 垃圾 tag 重置
```kotlin
val currentIsGarbage = current.contains("(")
when {
    detected != null && !detectedIsGarbage && detected != current -> settings.logcatTag = detected
    detected != null && !detectedIsGarbage -> FileLog.i(TAG, "tag confirmed: $detected")
    currentIsGarbage -> settings.logcatTag = LogcatSource.VEHICLE_TAG  // 重置默认
    else -> FileLog.w(TAG, "discover null, keep $current")
}
```

### PropertyMapper — 属性名前缀归一化
```kotlin
private val PREFIXES = listOf("CLUSTER_", "HU_", "LCM_", "VEH_", "AP_", "RTC_FLEX_")
private val normalizedCatalog = PropertyCatalog.table.keys.associateBy { normalize(it) }

private fun normalize(name: String): String {
    var s = name
    for (p in PREFIXES) {
        if (s.startsWith(p)) { s = s.removePrefix(p); break }
    }
    return s.uppercase()  // 与目录驼峰名全大写比较
}
```

## 值语义（实测校准，勿用厂商文档猜）

| 属性 | 值 → 含义 |
|------|-----------|
| DoorDrvrSts/DoorPassSts/DoorLeReSts/DoorRiReSts | 1=开 2=关 |
| LockgCenStsLockSt | **1=上锁 3=解锁**（特殊，无 2） |
| HoodSts | 1=开 2=关 |
| TrOpenerSts | 1=关 2=开 |
| WinPosnStsAtDrvr/Pass/ReLe/ReRi | 1=关 2=开 |
| PassSeatSts | 1=无 2=有人 |

## 修复思路（诊断方法论）

1. **先看 App 日志（FileLog 双写 logcat + 文件）**，找 `discover result=` 和 `listening tag=` 行——确认服务端到底在监听什么
2. **区分旧版残留**：FileLog 文件跨版本累积，前半是旧版记录。看 `export rows -> ...` 行定位版本分界
3. **unknown prop 是关键情报**：它证明信号已到达解析器。如果 unknown 里有"应该认识"的属性 → 映射/命名问题；如果没有 → tag 探测问题
4. **检查自身日志污染**：App 双写 logcat 会让任何探测类功能自污染（discover 结果、unparsed line 都含 onPropertyValue 字样）
5. **TDD 修复**：先写失败测试（scan 自污染回归 / 前缀归一化），再改实现
6. **用真实日志模拟验证**：从 App 日志提取真实属性名，Python 复刻 mapper 逻辑跑一遍，确认匹配

## 验证

- 测试：`app/src/test/java/com/maide/vehiclehud/PropertyMapperTest.kt`（**根包**，非 log/ 子包）、`app/src/test/java/com/maide/vehiclehud/log/LogTagScannerTest.kt`，48/48 绿
- 真实日志模拟脚本：`/data/data/com.termux/files/usr/tmp/opencode/vehiclehud-check/verify_v3.py`
- 产物：`/storage/emulated/0/MT2/VehicleHUD-V3-最新.apk`（md5 2760faa7d54ba78876305a7d488e00bf，v2 true）
- 构建：`JAVA_TOOL_OPTIONS="-Xmx4g" gradle :app:assembleRelease --no-daemon -q`；签名 mt2.keystore v1+v2 禁 v3

## Common Mistakes

- 用 `logcat -v raw` 做 tag 探测（无 tag 列）→ 必须 threadtime/brief
- 只修播放链（AudioPlayer/wav）不查探测链——"不播报"根因几乎都在上游
- 忽略 App 自身日志的污染（FileLog 双写 logcat 是元凶）
- 属性名只匹配裸驼峰名，忽略 CLUSTER_/HU_/LCM_ 前缀风格
- 值语义用厂商文档猜而非实测校准（锁 1/3 特殊）
