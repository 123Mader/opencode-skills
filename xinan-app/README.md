# 「心安」— 安卓焦虑情绪助手 (开发项目)

> **宗旨**: 让人类开心快乐，摆脱焦虑情绪困扰
> **核心**: 视觉微表情心理学精准识别 + 实时分析 + 对话疏导
> **平台**: 一加手机 (12GB / 天玑9000) | Android APP + 云端后端

---

## 项目结构

```
心安项目/
├── docs/                      # 文档
│   └── 方案文档.md             # 完整方案 (v3.0)
├── database/
│   └── schema.sql             # 数据库表结构 (用户/情绪/会话/聚合分析)
├── backend/
│   └── server.js              # 后端 API (注册/登录/情绪上报/分析)
└── android/
    └── app/src/main/java/com/xinan/app/
        ├── MainActivity.kt        # 双模式入口 (聊天/视频)
        ├── vision/
        │   └── MicroExpressionAnalyzer.kt  # ★ 微表情分析核心
        ├── chat/                  # 聊天模式 (待开发)
        │   └── ChatFragment.kt
        ├── video/                 # 视频陪伴模式 (待开发)
        │   └── VideoFragment.kt
        └── llm/                   # 大模型模块 (待开发)
            ├── ModelManager.kt    # 模型添加/切换
            └── LLMInference.kt    # MediaPipe LLM推理
```

## 快速开始（电脑端开发）

### 1. 环境要求
- **Android Studio** (最新版 + Android SDK 33+)
- **Node.js 18+** (后端)
- **MySQL 8+** (数据库)

### 2. 数据库
```bash
mysql -u root -p < database/schema.sql
```

### 3. 后端
```bash
cd backend
npm install express mysql2 cors
node server.js
# 后端运行在 http://localhost:3000
```

### 4. Android APP
1. Android Studio 打开 `android/` 目录
2. 添加依赖 (build.gradle):
```gradle
dependencies {
    // MediaPipe (人脸+LLM)
    implementation 'com.google.mediapipe:tasks-vision:0.10.14'
    implementation 'com.google.mediapipe:tasks-genai:0.10.14'
    // CameraX
    implementation 'androidx.camera:camera-camera2:1.3.4'
    // 网络
    implementation 'com.squareup.okhttp3:okhttp:4.12.0'
    // 本地数据库
    implementation 'androidx.room:room-runtime:2.6.1'
}
```
3. 下载 GGUF 模型放入 `app/src/main/assets/models/` 或运行时下载
4. 连接手机运行

## 核心模块开发顺序

| 优先级 | 模块 | 文件 | 说明 |
|--------|------|------|------|
| ★★★★★ | 微表情分析 | MicroExpressionAnalyzer.kt | 已提供核心帧分析逻辑 |
| ★★★★★ | 视频捕捉 | VideoFragment.kt | CameraX + FaceMesh 实时 |
| ★★★★ | LLM 对话 | LLMInference.kt | MediaPipe LLM + CBT 提示 |
| ★★★★ | 模型管理 | ModelManager.kt | GGUF 添加/切换 |
| ★★★ | 后端对接 | ApiClient.kt | 注册/登录/情绪上报 |
| ★★★ | 记忆系统 | MemoryRepository.kt | Room 本地存储 |

## 微表情分析模块说明 (MicroExpressionAnalyzer)

已实现:
- ✅ FaceMesh 468点 → FACS AU 强度计算 (AU4/AU23/AU15/AU12/AU17)
- ✅ 眨眼频率检测 (AU45, 焦虑时加快)
- ✅ 1秒滑窗 → 微表情突变检测
- ✅ 焦虑指数计算 (FACS 心理学加权)
- ✅ 情绪判定 (焦虑/紧张/快乐/悲伤/平静)

待接入:
- [ ] FaceLandmarker 实时帧输入
- [ ] 个人基线校准 (30秒平静视频)
- [ ] 深度学习分类器 (CNN+LSTM) 替代规则加权
- [ ] 与对话上下文融合

## API 一览 (后端)

| 方法 | 路径 | 功能 |
|------|------|------|
| POST | /api/register | 手机号注册 |
| POST | /api/login | 短信登录 |
| POST | /api/emotion | 情绪事件上报 |
| POST | /api/session/start | 会话开始 |
| POST | /api/session/end | 会话结束 |
| GET | /api/user/:id/progress | 个人30天趋势 |
| GET | /api/analysis/trends | 匿名聚合分析 |
| GET | /api/analysis/efficacy | 疗效研究 |

## 隐私合规

- 手机号哈希存储（SHA-256+salt）
- 情绪数据可选匿名化
- 摄像头数据仅本地处理
- 用户可导出/删除个人数据
- 非医疗设备，严重情况转介专业帮助