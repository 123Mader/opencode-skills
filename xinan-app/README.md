# 「心安」— 安卓焦虑情绪助手 (打开即用版)

> **宗旨**: 让人类开心快乐，摆脱焦虑情绪困扰
> **核心**: 视觉微表情心理学精准识别 + 实时分析 + 对话疏导
> **特点**: ✅ 无需注册登录，打开即用 ✅ 全部本地运行，数据不出手机

---

## 项目结构

```
心安项目/
├── docs/                      # 文档 (设计思路)
│   ├── 心理预期引擎设计.md
│   ├── UI主题与构建优化.md
│   └── HTTPS部署.md
├── database/
│   └── schema.sql             # 数据库表结构 (匿名情绪事件/会话/聚合分析)
├── backend/
│   └── server.js              # 后端 API (可选: 情绪上报/分析, 无账号)
└── android/
    └── app/src/main/java/com/xinan/app/
        ├── MainActivity.kt        # 主入口: 双模式切换 (聊天/视频), 打开即用
        ├── vision/                # ★ 视觉微表情核心
        │   ├── MicroExpressionAnalyzer.kt   # FACS 微表情分析 + 焦虑指数
        │   ├── FaceLandmarkerHelper.kt      # MediaPipe 468点人脸关键点
        │   ├── PsychologicalProjection.kt   # 心理预期推断引擎
        │   ├── EmotionStateHolder.kt        # 跨模式情绪状态
        │   └── YuvToBitmap.kt               # 相机帧转换
        ├── chat/                  # 聊天模式 (CBT 对话疏导)
        ├── video/                 # 视频陪伴模式 (摄像头实时分析 + 情绪仪表盘)
        ├── llm/                   # 本地大模型模块 (GGUF 推理/管理/下载)
        ├── data/                  # 本地记忆系统 (Room 情绪日志/趋势/报告)
        ├── relax/                 # 正念呼吸引导 (高焦虑自动触发)
        └── report/                # 成长报告 (30天情绪趋势)
```

## 快速开始

### 环境要求
- **Android Studio** (最新版 + Android SDK 35+)
- 本机无 JDK 时可用 GitHub Actions 云构建 APK（见下）

### 构建 Android APP
```bash
# 本机构建
cd android
gradle assembleDebug
# APK 输出: app/build/outputs/apk/debug/app-debug.apk

# 云构建 (无电脑也能出 APK)
# 推送到 GitHub main 分支 → Actions 自动构建 → 下载 xinan-debug-apk
# 推送脚本: node push_xinan.js <owner/repo>
```

### 运行
1. 安装 APK (允许未知来源)
2. 打开即用: 主界面可切换「💬 聊天」/「📹 视频」
3. 视频模式首次使用授权摄像头
4. 聊天模式需装大模型 (菜单→🧠模型管理, 下载 GGUF 或放入 `Android/data/com.xinan.app/files/models/`)
5. 情绪数据自动记录在本机 Room 数据库, 可看 📈 成长报告

### 后端 (可选, 情绪数据云端收纳分析)
```bash
cd backend
npm install express mysql2 cors
mysql -u root -p < ../database/schema.sql
node server.js        # http://localhost:3000 (无证书回退)
# HTTPS 部署见 docs/HTTPS部署.md
```
后端采用**匿名设备标识**，无注册登录、无手机号，纯情绪数据分析。

## 核心模块

| 模块 | 文件 | 说明 |
|------|------|------|
| ★★★★★ | MicroExpressionAnalyzer.kt | 微表情分析核心 (FACS AU + 焦虑指数) |
| ★★★★★ | VideoFragment.kt | CameraX + FaceMesh 实时视频分析 |
| ★★★★ | PsychologicalProjection.kt | 心理预期推断 (焦虑/压抑/期待/回避...) |
| ★★★★ | LLMInference.kt | MediaPipe 本地 LLM + CBT 对话 |
| ★★★★ | ModelManager.kt | GGUF 模型添加/切换/下载 |
| ★★★ | MemoryRepository.kt | Room 本地情绪记忆/趋势/报告 |
| ★★★ | BreathingGuideActivity.kt | 正念呼吸引导 (高焦虑防抖触发) |

## 设计思路

- **微表情→情绪**: FaceMesh 468点 → FACS AU 强度 (AU4/AU23/AU15/AU12/AU17) → 焦虑指数 (心理学加权)
- **心理预期**: 头部偏航(视线回避) + 眨眼率滑窗 + 焦虑轨迹 → 8 类心理预期评分, 对话中"给出"对方预期
- **对话疏导**: CBT 系统提示词 + 心理预期镜像反馈 ("我注意到你似乎…, 是这样吗?")
- **隐私优先**: 摄像头数据仅本地处理; 情绪记录存本机; 后端仅匿名聚合

## 隐私合规

- 无账号体系: 不收集手机号/身份信息, 打开即用
- 摄像头数据仅本地处理
- 后端仅存匿名设备标识 + 情绪统计
- 非医疗设备，严重情况转介专业帮助

---

## 开发进度

### ✅ 已完成
- 双模式入口 MainActivity (打开即用, 无登录)
- 视频模式: CameraX 前置 + FaceLandmarker 468点 GPU + 微表情分析 + 情绪仪表盘 + 心理预期引擎
- 聊天模式: ChatFragment + LLMInference (MediaPipe LLM + CBT)
- 模型管理: ModelManager (推荐模型/本地导入/切换/下载)
- 记忆系统: Room 本地情绪日志 + 30天趋势 + 成长报告
- 正念呼吸: BreathingGuideActivity (高焦虑自动引导)
- 后端: server.js (匿名情绪上报/聚合分析, 无账号)
- 数据库: schema.sql (情绪事件/会话/聚合)
- 构建: GitHub Actions 云构建 APK

### 🔜 下一步开发
- [ ] 微表情深度学习分类器 (CNN+LSTM 替换规则加权)
- [ ] 个人基线校准优化
- [ ] 语音情绪识别 (src_audio)
