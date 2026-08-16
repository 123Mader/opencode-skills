---
name: kimi-code-cli
description: Kimi Code CLI 蒸馏技能。用户说"Kimi Code""kimi CLI""月之暗面""/plan 模式""终端 AI 编程 agent""蒸馏"时使用。包含 Kimi Code CLI 的安装、登录、全部命令/斜杠命令/快捷键、内置工具与审批默认值、子 agent、MCP 配置、ACP IDE 集成、会话管理、Agent 工作流。来源: MoonshotAI/kimi-code (MIT, v0.36.0, 2026-08)。
---

# Kimi Code CLI 蒸馏 (kimi-code-cli)

月之暗面开源终端 AI 编程 agent 的蒸馏参考（MIT 协议）。Kimi Code CLI 能读改代码、执行 shell、搜索文件、抓网页，并根据反馈自主规划下一步。**成功迁移自旧版 kimi-cli**（自动迁移配置与会话）。

来源: github.com/MoonshotAI/kimi-code · 文档: moonshotai.github.io/kimi-code/ · 中文文档: kimi.com/code/docs · npm: @moonshot-ai/kimi-code

## 安装（两种方式）

```bash
# 官方脚本: 单二进制, 无需 Node.js (推荐)
curl -fsSL https://kimi.com/code/install | bash
# 或 npm (Node.js ≥ 22.19)
npm install -g @moonshot-ai/kimi-code
# 升级
kimi upgrade
```

## 首次使用与登录

```bash
cd <项目> && kimi   # 启动交互式会话
/login              # Kimi Code OAuth 设备码登录(免手动管理 Key)
```
- 也可配 Moonshot Open Platform API Key；其他 provider（Anthropic/OpenAI/Google）编辑 `~/.kimi-code/config.toml`
- Kimi 模型端点：OpenAI 兼容 `https://api.kimi.com/coding/v1`；Anthropic 兼容 `https://api.kimi.com/coding/`（可用于 OpenCode/Claude Code/Codex 等第三方工具）

## CLI 命令

| 命令 | 作用 |
|------|------|
| `kimi` | 当前目录启动交互会话 |
| `kimi -C` / `--continue` | 继续最近会话 |
| `kimi -S [id]` / `--session` | 按 ID 恢复会话（无 ID 打开选择器） |
| `kimi -p "..."` / `--prompt` | 单条非交互提示词（`--output-format stream-json` 输出 JSONL 事件） |
| `kimi -m` / `--model` | 指定模型别名启动 |
| `kimi --plan` | 以 Plan Mode 启动 |
| `kimi --yolo` / `-y` | 自动批准常规工具调用（仅可信目录） |
| `kimi --auto` | 自动权限模式，不提问 |
| `kimi --skills-dir <dir>` | 指定技能目录（覆盖自动发现） |
| `kimi login` | 不进 TUI 直接 OAuth 登录 |
| `kimi acp` | 作为 ACP 服务器供 IDE 驱动 |
| `kimi server` / `kimi web` | 本地 REST/WebSocket/web 服务；浏览器 UI |
| `kimi doctor` | 校验 config.toml / tui.toml |
| `kimi export [id]` | 会话打包 ZIP |
| `kimi migrate` | 从旧版 kimi-cli 迁移数据 |
| `kimi vis [id]` | 浏览器会话可视化 |
| `kimi provider` | 终端管理 provider |

## 斜杠命令

| 命令 | 作用 |
|------|------|
| `/login` | 登录 |
| `/init` | 分析代码库生成/刷新 AGENTS.md |
| `/plan [on\|off]` `/plan clear` | Plan Mode：先探索规划，批准后才改文件（大/高风险任务用） |
| `/compact [文本]` | 压缩上下文释放空间（长会话必用） |
| `/sessions` | 浏览/恢复历史会话 |
| `/model` | 切换本次会话模型 |
| `/undo [n]` | 撤销最近提示词 |
| `/export-md` | 会话导出 Markdown |
| `/yolo` `/auto` | 切换免审批/自动权限模式 |
| `/swarm [on\|off]` `/swarm <任务>` | 集群模式：并行子 agent 批量任务 |
| `/goal [...]` | 自主目标模式 |
| `/mcp` `/mcp-config` | 列出 MCP 服务器；**对话式添加/编辑/认证 MCP**（免手写 JSON） |
| `/skill:name` | 调用已注册技能 |
| `/usage` `/status` | token 用量；运行时状态 |
| `/btw [问题]` | 旁路对话（fork 子 agent） |
| `/plugins` `/version` `/feedback` `/exit` | 插件/版本/反馈/退出 |

## 快捷键

| 键 | 作用 |
|----|------|
| `Shift-Tab` | 切换 Plan Mode |
| `Esc` | 中断流式输出/关弹窗 |
| `Ctrl-C` | 中断；空闲时连按两次退出 |
| `Ctrl-S` | 输出中插入消息 |
| `Ctrl-O` | 折叠/展开工具输出与压缩摘要 |
| `Ctrl-X` | （旧版 kimi-cli）shell 命令模式 |

## 内置工具与审批默认值

| 工具 | 审批 | 工具 | 审批 |
|------|------|------|------|
| Read | 自动 | Write/Edit | 需审批 |
| Grep / Glob | 自动 | Bash | 需审批 |
| WebSearch / FetchURL | 自动 | ReadMediaFile | 自动 |
| EnterPlanMode/ExitPlanMode | 自动(计划需用户确认) | TodoList/Agent/AgentSwarm | 自动 |
| AskUserQuestion | 自动 | Skill | 自动 |

只读操作默认自动执行；改文件/执行命令默认征求确认。

## 子 Agent（隔离上下文并行工作）

- `coder`：编码任务 · `explore`：代码库探索 · `plan`：规划
- 主对话保持干净；可用 `/swarm` 批量并行调度

## IDE 集成 (ACP)

`kimi acp` 以 Agent Client Protocol 提供会话服务，Zed/JetBrains 等 ACP 客户端可直接驱动，登录一次即可。

## 核心工作流（蒸馏要点）

1. `/init` 生成 AGENTS.md 建立项目上下文
2. 大/高风险/不明确任务 → `/plan on`（先探索→审阅计划→批准）
3. 长会话 → `/compact` 防上下文膨胀
4. 多任务 → `/swarm` 并行子 agent；单点 → `/btw`
5. 权限分级：默认确认制；**仅可信目录**用 `--yolo`/`--auto`
6. 生命周期 hooks：关键节点执行本地命令（拦截高危工具调用、审计决策、桌面通知）

## 对比定位（2026-08）

| 项目 | Kimi Code | Claude Code | Codex CLI | Gemini CLI |
|------|-----------|-------------|-----------|------------|
| 语言 | TypeScript | Node.js | Rust | TypeScript |
| 许可 | **MIT** | 专有 | 开源 | Apache 2.0 |
| 子 agent | coder/explore/plan | 有 | 有 | 无(串行) |
| MCP | /mcp-config 对话式 | 有 | 有 | 有 |

## 常见问题

- 用户问"怎么装 Kimi Code" → 官方脚本一行装，无 Node 依赖
- 用户问"和 Claude Code 比" → 见对比表，注意许可差异（Kimi 是 MIT）
- 用户想在自己项目里复刻其工作流 → 参照"核心工作流"段：AGENTS.md + Plan + 审批分级 + 长会话压缩
- 用户想用 Kimi 模型接 OpenCode → 端点 `https://api.kimi.com/coding/v1`（OpenAI 兼容）
