---
name: claude-code-cli
description: Claude Code CLI 蒸馏技能。用户说"Claude Code""Claw Code""claude 终端""Anthropic 编程 agent""/plan 模式""蒸馏 CLI 工具"时使用。包含安装、登录认证(订阅/Console/Bedrock/Vertex)、CLI 启动参数、斜杠命令、快捷键、权限模式、内置工具与审批默认值、子 agent、hooks、MCP、CLAUDE.md 记忆与 skills、Agent 工作流。来源: Anthropic 官方文档 code.claude.com/docs 与 github.com/anthropics/claude-code (v2.1.x, 2026-08)。
---

# Claude Code CLI 蒸馏 (claude-code-cli)

Anthropic 的终端 AI 编程 agent（Node.js 原生二进制，专有协议）。能读改代码、执行 shell、搜索文件、抓网页、并行子 agent，并根据反馈自主规划下一步。截至 2026-08 为 v2.1.x（最新 v2.1.232，2026-08-13）。桌面版/Web/VS Code/JetBrains/Slack/GitHub Actions 全平台可用。

来源: code.claude.com/docs · github.com/anthropics/claude-code · npm: @anthropic-ai/claude-code

## 安装（推荐原生，npm 已弃用）

```bash
# macOS/Linux/WSL 官方脚本（自动后台更新）
curl -fsSL https://claude.ai/install.sh | bash
# Windows PowerShell
irm https://claude.ai/install.ps1 | iex
# Homebrew / WinGet / Linux 包管理器
brew install --cask claude-code        # 或 claude-code@latest
winget install Anthropic.ClaudeCode
# npm（已弃用，v2.1.198 起需 Node.js ≥ 22；装的是同一份原生二进制）
npm install -g @anthropic-ai/claude-code
claude --version   # 验证，如 2.1.232
```

## 登录认证

- 需要 Pro/Max/Team/Enterprise 订阅、Claude Console（API 预付费）或云厂商（Bedrock/Vertex/Foundry）；**免费 Claude.ai 计划不含 Claude Code**
- `cd <项目> && claude` → 首次启动自动开浏览器登录；浏览器没弹出按 `c` 复制登录 URL；WSL2/SSH/容器里回调不通时，把浏览器显示的一次性代码粘贴回终端
- `ANTHROPIC_API_KEY` 已设置时跳过浏览器，直接批准该 key
- CI/脚本无浏览器场景：`claude setup-token` 生成一年期 OAuth token → `export CLAUDE_CODE_OAUTH_TOKEN=...`
- 云厂商：`export CLAUDE_CODE_USE_BEDROCK=1`（AWS 凭证）或 `CLAUDE_CODE_USE_VERTEX=1`（GCP 凭证）
- `/login` 换账户；`/logout` 登出（会重置首启设置流程）；`/status` 查版本/模型/账户/连通性

## CLI 启动参数

| 参数 | 作用 |
|------|------|
| `claude` | 当前目录启动交互会话 |
| `claude -p "..."` / `--print` | 单条 headless 提示词，输出到 stdout 后退出（CI/CD 用） |
| `claude --output-format text\|json\|stream-json` | 输出格式（须与 `-p` 组合；json 可管道给 jq） |
| `claude -c` / `--continue` | 继续最近会话 |
| `claude -r [id]` / `--resume` | 按 ID 恢复会话（无 ID 打开选择器） |
| `claude --model <model>` | 指定模型（`claude-opus-5` / `claude-sonnet-5` / `claude-haiku-4-5` / `claude-fable-5`） |
| `claude --permission-mode <mode>` | default(UI 显示 Manual) / acceptEdits / plan / auto / dontAsk / bypassPermissions |
| `claude --dangerously-skip-permissions` | 跳过全部审批（仅限沙箱/容器；rm -rf / 和 ~ 仍会拦） |
| `claude --accept-edits` | 自动批准文件编辑 |
| `claude --max-turns <n>` | 限制自主轮数防失控 |
| `claude --max-budget-usd <usd>` | 花费上限（v2.1.217 起真正终止后台子 agent） |
| `claude --add-dir <路径>` | 附加工作目录 |
| `claude --safe-mode` | 干净会话：禁用所有自定义项排障 |
| `claude --fallback-model <model>` | 主模型过载时链式备用（最多 3 个） |
| `claude doctor` | 诊断安装与配置，可自动修复 |
| `claude update` | 升级（/upgrade 打开升级页） |
| `claude config` | 打开/编辑配置 |
| `claude mcp add\|list\|get\|remove` | 管理 MCP 服务器（`--scope user/project/local`） |
| `claude agents` | 管理子 agent（`!` 起后台 shell 会话） |
| `claude auth login\|logout\|status` | 命令行认证管理 |
| `claude setup-token` | 生成一年期 OAuth token（CI） |
| `claude remote-control` | 远程控制会话 |

## 斜杠命令（精选，按场景分组）

| 场景 | 命令 |
|------|------|
| 会话 | `/clear [name]`(别名 /reset /new) · `/compact [提示]` · `/context [all]` · `/resume [会话]`(别名 /continue) · `/rewind`(别名 /checkpoint /undo) · `/export [文件]` · `/rename` · `/recap` · `/btw <问题>` · `/exit` |
| 记忆 | `/init` 生成 CLAUDE.md · `/memory` 编辑记忆文件、管 auto-memory |
| 模型 | `/model [模型]` · `/effort [low\|medium\|high\|xhigh\|max\|ultracode]` · `/fast [on\|off]` · `/thinking` |
| 规划/执行 | `/plan [描述]` 直接进 Plan Mode · `/goal [条件\|clear]` 持续工作到条件满足 · `/loop [间隔] [提示]` · `/background [提示]`(别名 /bg) · `/tasks`(别名 /bashes) · `/stop` · `/branch [名字]`(别名 /fork) · `/batch <任务>` 并行大改（5~30 个独立 worktree 各开 PR） |
| 子 agent | `/agents` 创建/编辑/查看子 agent · `/workflows` 动态工作流（几十到几百个 agent 后台并行） |
| 权限 | `/permissions`(别名 /allowed-tools) 管理 allow/ask/deny · `/fewer-permission-prompts` 扫描历史生成白名单建议 · `/sandbox` 切换沙箱 |
| 配置 | `/config`(别名 /settings) · `/keybindings` · `/terminal-setup` · `/theme` · `/statusline` · `/add-dir` · `/cd` · `/env` · `/privacy-settings`(Pro/Max) |
| 审查 | `/diff` · `/review [PR]`(现为 /code-review 别名) · `/code-review [级别] [--fix]` · `/security-review` · `/simplify`（仅清理类审查） · `/commit-push-pr` |
| 集成 | `/mcp` 管理 MCP 连接与 OAuth · `/hooks` 查看 hook 配置 · `/skills` 列出/隐藏技能 · `/plugin` 管理插件 · `/ide` · `/chrome` |
| 云端 | `/remote-control`(别名 /rc) · `/teleport`(别名 /tp) · `/desktop`(别名 /app，macOS/Win+订阅) · `/autofix-pr` · `/schedule`(别名 /routines) 云端定时任务 · `/ultrareview [PR]` 云端多 agent 深度审查 |
| 诊断 | `/doctor`(现为 bundled skill，别名 /checkup) · `/debug [描述]` · `/status` · `/usage`(别名 /cost /stats) · `/usage-credits`(原 /extra-usage) · `/insights` · `/release-notes` · `/feedback`(别名 /bug) |
| 登录 | `/login` · `/logout` · `/upgrade` |

> 2026 变化提醒：`/vim` 已移除（v2.1.92，去 `/config` 的 Editor mode 切 Vim）；`/pr-comments` 已移除（v2.1.91）；`/cost`、`/stats` 只是 `/usage` 的别名；`/ultraplan` 已移除（2026-08）。
> 自定义斜杠命令已并入 skills 体系：`.claude/commands/deploy.md` 和 `.claude/skills/deploy/SKILL.md` 都生成 `/deploy`。命令只在消息开头识别。

## 快捷键

| 键 | 作用 |
|----|------|
| `Shift+Tab` | 循环权限模式：Manual → acceptEdits → plan → bypassPermissions → auto |
| `Esc` | 中断 Claude / 关闭弹窗；`Esc Esc` 打开 rewind 回退菜单 |
| `Ctrl+C` | 中断；空闲时再按退出 |
| `Ctrl+R` | 反向搜索命令历史（fullscreen 渲染下为搜索对话框，Ctrl+S 切换作用域） |
| `Ctrl+G` / `Ctrl+X Ctrl+E` | 打开系统编辑器编辑长提示词 |
| `Ctrl+L` | 清屏（fullscreen 下两秒内连按两次 = /clear） |
| `Ctrl+S` | 暂存当前输入 |
| `Ctrl+J` | 插入换行不发送（`\`+Enter 也行） |
| `Ctrl+O` | 展开完整转写视图 |
| `Ctrl+X Ctrl+K` | 停止本会话所有后台子 agent |
| `Alt+P` / `Alt+O` / `Alt+T` | 模型选择器 / 切换 fast 模式 / 切换扩展思考（macOS 需 Option 作 Meta） |
| `Ctrl+V` | 从剪贴板粘贴图片 |
| `↑`/`↓` | 历史浏览 · `?` 快捷键帮助（fullscreen） |

Vim 编辑模式：`/config` → Editor mode 开启；NORMAL 模式支持 `hjkl`/`w`/`b`/`f{char}` 移动、`d`/`c`/`y` 编辑与 `iw`/`aw` 文本对象。

## 内置工具与审批默认值

| 工具 | 审批 | 工具 | 审批 |
|------|------|------|------|
| Read / Grep / Glob / LS | 自动 | Edit / Write / MultiEdit | 需审批(acceptEdits 自动) |
| WebFetch / WebSearch | 自动 | Bash / PowerShell | 需审批（每项目首用） |
| Task(子 agent) / TodoWrite / Skill | 自动 | NotebookRead / NotebookEdit | 自动 |
| AskUserQuestion | 自动 | KillShell / Monitor | 自动 |
| SendMessage / ListAgents | 自动 | | |

规则求值顺序：**deny → ask → allow**，首个匹配生效，deny 永远赢。权限写在 `~/.claude/settings.json`（全局）与 `.claude/settings.json`（项目，可入库）。

## 权限模式

| 模式 | 行为 |
|------|------|
| `default`（UI 显示 Manual） | 每个工具首次使用弹确认 |
| `acceptEdits` | 自动批准文件编辑与常见文件命令（mkdir/touch/mv/cp） |
| `plan` | 只读：可探索、给方案，不编辑文件、不执行变更命令 |
| `auto` | 自动批准+后台安全分类器检查（research preview） |
| `dontAsk` | 未预授权的一律拒绝 |
| `bypassPermissions` | 跳过所有确认（仅容器/VM；rm -rf / 与 ~ 仍有熔断） |

## 子 Agent（独立上下文/工具/权限/模型）

- 定义在 `.claude/agents/<name>.md`（项目）或 `~/.claude/agents/`（个人）；frontmatter：`name`、`description`（决定 Claude 何时自动委派）、`tools` 白名单、`disallowedTools` 黑名单、`model`（默认继承主会话模型——记得显式指定省钱）、`permissionMode`/`skills`/`mcpServers`/`hooks`
- 内置类型：`explore`（探索）、`general-purpose`；用 `/agents` 面板创建/编辑，改盘上文件需重启会话才生效（`/agents` 创建即时生效）
- 子 agent 看不到主会话上下文，只回传摘要；不能用 AskUserQuestion/EnterPlanMode/Agent 工具
- 2026-08：嵌套子 agent 默认开启（深度 3，`CLAUDE_CODE_MAX_SUBAGENT_SPAWN_DEPTH=1` 关闭）；并发上限约 20（`CLAUDE_CODE_MAX_CONCURRENT_SUBAGENTS`）；子 agent 默认后台运行，可提交/推送/开 draft PR
- Agent Teams（实验性）：多实例协调共享任务列表，`CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS=1`，v2.1.32+

## Hooks（确定性护栏层，不靠模型自觉）

- 29 个生命周期事件：SessionStart / UserPromptSubmit / PreToolUse / PermissionRequest / PostToolUse / SubagentStart / SubagentStop / Stop / PreCompact / PostCompact / FileChanged / Notification / SessionEnd 等
- 5 种处理器：`command`(shell) / `http`(webhook) / `mcp_tool` / `prompt`(单轮 LLM 评估) / `agent`(派生子 agent)
- 退出码 2 = 阻止该动作（PreToolUse 拦工具调用、UserPromptSubmit 拒提示词……）；JSON 响应可强制 allow/ask/deny
- 经典用法：编辑后自动格式化、跑测试不过不让 Stop、阻止写生成文件、SubagentStop 做质量门禁

## MCP 集成

```bash
claude mcp add github --transport http --url ... --headers '{"Authorization":"Bearer ${GITHUB_TOKEN}"}'
claude mcp add postgres -- npx -y @modelcontextprotocol/server-postgres postgresql://localhost/mydb
claude mcp list / get / remove
```
- 作用域：`--scope project` 写 `.mcp.json`（入库共享团队）· `--scope user` 写 `~/.claude.json`（个人全局）· 默认 local（仅本机、本项目）
- `/mcp` 会话内管理连接与 OAuth 认证；建议 3~6 个 MCP 服务器，超过 10 个工具选择精度下降

## 记忆与 Skills

- 记忆：`CLAUDE.md`（项目根，`/init` 生成、`/memory` 编辑）+ `~/.claude/CLAUDE.md`（全局，跨项目）；auto-memory 可自动写入
- Skill = 升级版斜杠命令：`.claude/skills/<名>/SKILL.md` 或 `~/.claude/skills/`；frontmatter 支持 `description`、`allowed-tools`（调用该技能时免审批）、`disable-model-invocation`（只许手动触发）、`background: false`（禁止后台运行）
- 正文可用 `$ARGUMENTS`/`$1`/`$2` 接参数，`` !`命令` `` 在发送前注入现场数据（如 git diff）；技能体只在调用时加载，长参考材料零成本
- Bundled skills（内置）：`/doctor` `/code-review` `/batch` `/debug` `/loop` `/claude-api` `/verify` `/deep-research`（仅手动触发）`/dataviz` `/fewer-permission-prompts`；`/skills` 可列出并按 token 占用排序、从菜单隐藏

## 核心工作流（蒸馏要点）

1. `/init` 生成 CLAUDE.md 建立项目记忆；按需写全局 `~/.claude/CLAUDE.md`
2. 大改/高风险任务 → `/plan` 或 Shift+Tab 进 plan 模式：先探索→审阅方案→批准后才动文件
3. 权限分级渐进：default → acceptEdits → auto/bypassPermissions（后者仅沙箱）；`/permissions` 配 allow/ask/deny 白名单终结确认轰炸
4. 长会话 → `/context` 看谁吃 token，再 `/compact` 压缩；任务间 `/clear`
5. 改坏东西 → `/rewind` 回退对话和/或代码到检查点
6. 重活隔离 → 子 agent（各自工具白名单+模型）；规则强制 → hooks；流程复用 → skills
7. CI/CD → `claude -p --output-format json` 管道给 jq；`--max-turns`/`--max-budget-usd` 设护栏
8. 出问题先 `/doctor`（自动修复），再 `/debug` 抓日志；`/status` 排查账号/版本

## 对比定位（2026-08）

| 项目 | Claude Code | Kimi Code | Codex CLI | Gemini CLI |
|------|-------------|-----------|-----------|------------|
| 语言 | Node.js | TypeScript | Rust | TypeScript |
| 许可 | **专有** | MIT | 开源 | Apache 2.0 |
| 子 agent | explore/general-purpose，嵌套深 3 | coder/explore/plan | 有 | 无(串行) |
| hooks | 29 事件/5 处理器 | 有(生命周期 hooks) | 无 | 无 |
| MCP | /mcp + claude mcp add | /mcp-config 对话式 | 有 | 有 |
| 记忆 | CLAUDE.md | AGENTS.md | AGENTS.md | 有 |

## 常见问题

- 用户问"怎么装 Claude Code" → 官方脚本一行装；npm 已弃用别推荐；免费 Claude.ai 计划不含 Claude Code
- 用户问"和 Kimi Code 比" → 见对比表；注意许可差异（Claude Code 专有）与记忆文件名差异（CLAUDE.md vs AGENTS.md）
- 用户问"老弹权限确认" → `/permissions` 把 `Bash(npm run test:*)` 之类加入 allow，或 `/fewer-permission-prompts` 自动生成白名单
- 用户问"怎么在 CI 里用" → `claude setup-token` 出 token + `claude -p --output-format json "..."`，加 `--max-turns`/`--max-budget-usd`
- 用户想在自己项目复刻其工作流 → 参照"核心工作流"段：CLAUDE.md + plan 模式 + 审批分级 + hooks 护栏 + 子 agent 隔离
- 用户问"Claw Code"是什么 → 指 Claude Code（本技能），Anthropic 终端 AI 编程 agent