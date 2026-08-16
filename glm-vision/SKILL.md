---
name: glm-vision
description: Use when you need to analyze, describe, OCR, or answer questions about an image in this Termux/opencode workspace — screenshots, photos, diagrams, charts, UI mockups, code screenshots. Calls Zhipu AI GLM-4V-FLASH (open.bigmodel.cn) from a shell script and returns the model's text answer. Requires a GLM_API_KEY.
---

# GLM Vision Skill

用智谱 AI 的 **GLM-4V-FLASH** 视觉模型，可对工作区内的图片做阅读理解：截图分析、照片描述、图表/架构图解读、OCR 文字提取、UI 设计稿审查、代码截图转文字等。

## When to Use

需要理解一张图片内容时使用：
- 截图 / 照片 / 图表 / UI 界面截图 / 代码截图 / 设计稿等，需要描述、识别、提问
- 用户明确要求用 GLM 视觉模型分析图片
- SSH/Termux 环境，app 端没有配视觉模型

不需要图片理解时不要用（纯文本任务应忽略本技能）。

## 前置要求

**必须有 GLM_API_KEY**（获取：https://open.bigmodel.cn/）。三种给法任选：

```bash
# ① 会话环境变量（推荐）
export GLM_API_KEY="your_key"
# ② 配置文件（兼容原脚本）
echo "your_key" > ~/.linecode/.glm_api_key && chmod 600 ~/.linecode/.glm_api_key
```

依赖：`curl` `python3`（必要）、`file`（推荐，MIME 检测）。

## 用法

脚本位于 opencode 技能目录（不在 PATH 时用完整路径）：

```bash
GLM=~/.config/opencode/skills/glm-vision/glm_vision.sh

# 默认：描述图片内容
"$GLM" photo.jpg

# 自定义提示词
"$GLM" screenshot.png "提取图中的所有文字，保持原格式"

# 提示词从 stdin 读
echo "这张图表展示什么趋势？" | "$GLM" chart.png -
```

### 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `GLM_API_KEY` | (无) | 智谱 API Key，必需 |
| `GLM_VISION_MODEL` | `glm-4v-flash` | 模型名 |
| `GLM_VISION_MAX_SIZE` | `10485760` (10MB) | 图片字节上限，超限先压缩 |
| `GLM_VISION_TIMEOUT` | `60` | 请求超时秒数 |
| `TMPDIR` | 系统默认 | 临时文件目录（Termux 为 `…/usr/tmp`） |

### 常用场景

- OCR：`"$GLM" doc.jpg "提取所有文字，保持原始格式"`
- UI 分析：`"$GLM" ui.png "分析布局结构，列出所有可见 UI 元素"`
- 图表解读：`"$GLM" chart.png "X/Y 轴含义？数据趋势如何？"`
- 代码截图：`"$GLM" code.png "提取代码，保持缩进格式"`

## 输出约定

- **stdout**：模型文本回复
- **stderr**：错误信息；退出码 0=成功，非 0=失败
- 图片过大/缺 key/HTTP 异常/格式异常均有明确中文报错，不会静默失败

## 备选通道：设备端 MCP（com.glmvision.app）

若设备上装有 `GLM-Vision-MCP.apk`（`com.glmvision.app`，本地 MCP 服务端）且其 `McpService` 在运行，`http://127.0.0.1:8789/mcp` 可直达，暴露 `analyze` 工具（异步作业，入参文件路径+提示词）。探测端口通不通：

```bash
curl -sS --max-time 3 http://127.0.0.1:8789/ >/dev/null 2>&1 && echo "MCP up" || echo "MCP down"
```

优先用上面的脚本（独立、无需 app 常驻）；`analyze` 结果需轮询作业状态，仅当脚本因故不可用且该端口在跑时采用。端点未通时不要假设可用，回退脚本通道。

## 常见问题

- `ERROR: GLM_API_KEY not set` → 按上文设置 key
- `ERROR: Image too large` → 调大 `GLM_VISION_MAX_SIZE` 或先压缩（可用 python3 PIL / ffmpeg）
- `API request failed (HTTP 401)` → key 无效或需激活账号
- 网络失败 / QPS 超额 → GLM-4V-FLASH 免费但有调用频率限制，稍后重试

## 安全

- API Key 不硬编码在脚本里；配置仅存 `600` 权限文件
- 不在日志/输出中打印 key
- 全程 HTTPS