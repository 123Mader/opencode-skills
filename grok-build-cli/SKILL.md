---
name: grok-build-cli
description: Grok Build CLI 蒸馏技能。用户说"Grok Build""grok 终端""xAI 编程 agent""SpaceXAI""蒸馏 CLI 工具"时使用。包含 Grok Build 的安装、登录认证(浏览器 OAuth/API Key/OIDC/设备码/外部认证)、CLI 命令与 headless 模式、斜杠命令、快捷键、权限模式与审批规则、内置工具、子 agent 与 personas、MCP、skills、plugins、hooks、Plan Mode、自定义模型、AGENTS.md 项目规则、会话管理。来源: xai-org/grok-build (Apache-2.0, 2026-08) 与 docs.x.ai/build。
---

# Grok Build CLI 蒸馏 (grok-build-cli)

SpaceXAI(xAI)的终端 AI 编程 agent(Rust 编写,Apache-2.0)。全屏 TUI,能读改代码、执行 shell、搜索文件、抓网页、管理长任务,并支持 headless(CI/脚本)与 ACP(编辑器嵌入)三种使用面。2026-05-25 起 beta,2026-07-15 开源(GitHub: xai-org/grok-build),当前由 Grok 4.6 驱动,二进制名 `xai-grok-pager`、官方安装为 `grok`。

来源: x.ai/cli · docs.x.ai/build/overview · github.com/xai-org/grok-build(仓库为周期性镜像,外部 PR 不接受;根 Cargo.toml 是生成的,只读)

## 安装

```bash
# macOS/Linux/Git Bash 官方脚本
curl -fsSL https://x.ai/cli/install.sh | bash
# 指定版本
curl -fsSL https://x.ai/cli/install.sh | bash -s 0.1.42
# Windows PowerShell
irm https://x.ai/cli/install.ps1 | iex
# 升级 / 验证
grok update && grok --version
```

从源码构建(需 rustup + dotslash + protoc;全工作区构建慢,按 crate 操作):

```bash
cargo run -p xai-grok-pager-bin              # 构建+启动 TUI
cargo build -p xai-grok-pager-bin --release  # -> target/release/xai-grok-pager
cargo check -p xai-grok-tools                # 只验证单个 crate
```

## 登录认证

- **浏览器 OAuth(默认)**:`cd <项目> && grok` 首次启动自动开浏览器(auth.x.ai / grok.com),凭证存 `~/.grok/auth.json`(0600),自动刷新;无浏览器环境用 `grok login --device-auth`(设备码流程,打印 URL+code)
- **API Key(CI/无浏览器)**:`export XAI_API_KEY="xai-..."`(console.x.ai 申请);有会话 token 时 token 优先,想回退到 key 先 `grok logout`
- **企业 OIDC(SSO)**:config.toml 里 `[grok_com_config.oidc] issuer/client_id`,或 `GROK_OIDC_ISSUER` / `GROK_OIDC_CLIENT_ID`;IdP 需允许 loopback redirect `http://127.0.0.1/callback`,PKCE 无 secret
- **外部认证代理**(沙箱/离线环境):`[auth] auth_provider_command = "/usr/local/bin/my-auth"`;契约:stdout 只打 token(裸串或 JSON),stderr 打登录 URL 给用户;`GROK_AUTH_EXPIRED=1` 表示无头静默刷新(别阻塞,秒退),未设置=交互登录(300s 上限)
- **认证优先级**:per-model `api_key`/`env_key` > session token(auth.json) > `XAI_API_KEY`
- `grok login`(重登)/ `grok logout`(清凭证);`GROK_LOG_FILE=/tmp/grok.log RUST_LOG=debug grok` 排障;`/privacy` 控制训练数据共享

## CLI 命令

| 命令 | 作用 |
|------|------|
| `grok` | 当前目录启动 TUI;可带首轮 prompt:`grok "fix the failing test"` |
| `grok -p "<prompt>"` / `--single` | headless 单条提示词(还有 `--prompt-json` / `--prompt-file`;stdin 不读入) |
| `grok --output-format <FMT>` | plain / json(单对象:text+stopReason+sessionId+usage+cost) / streaming-json(NDJSON 事件流) / streaming-messages-json(Messages API 线格式) |
| `grok -m <model>` | 指定模型(如 `grok-build`);`--effort <level>` 推理力度 none/minimal/low/medium/high/xhigh/max |
| `grok -r <ID或标题>` / `--resume` | 恢复会话(脚本用 ID);`-c` / `--continue` 继续当前目录最近会话;`-s <UUID>` 仅新建会话(与 `-r` 组合需 `--fork-session`) |
| `grok --yolo` / `--always-approve` | 自动批准全部工具调用(deny 规则/hooks 仍生效);等价 `--permission-mode bypassPermissions` |
| `grok --permission-mode <M>` | default(ask)/ acceptEdits / plan / auto / dontAsk / bypassPermissions |
| `grok --allow 'Bash(git *)' --deny 'Bash(rm -rf *)'` | 权限规则(可重复;deny > ask > allow) |
| `grok --tools "read_file,grep,list_dir"` | 工具白名单(headless only);`--disallowed-tools` 黑名单,支持 `Agent`/`Agent(explore)` 条目禁子 agent |
| `grok --max-turns <N>` | 限制自主轮数(headless only) |
| `grok --rules "..."` | 追加系统提示规则(配合 `--yolo` 设护栏) |
| `grok --worktree=<name> "prompt"` | 新 git worktree 里跑(注意用 `=`;`--ref <branch>` 指定基底分支) |
| `grok --cwd <path>` | 指定工作目录(大仓库里指向子项目,避免全仓发现变慢) |
| `grok --sandbox <profile>` | 沙箱 off/workspace/devbox/read-only/strict |
| `grok --minimal` / `--fullscreen` | 滚动条原生渲染模式 / 标准全屏(记忆上次选择) |
| `grok agent [--always-approve] <stdio\|serve\|headless\|leader>` | ACP 服务器:stdio(本地/SDK)、`serve --bind 127.0.0.1:2419 --secret <token>`(WebSocket)、`--grok-ws-url`(中继) |
| `grok mcp list/add/remove/enable/disable/doctor` | MCP 管理(`add filesystem -- npx -y ...`,`--` 后参数直达服务器;`doctor` 诊断连通性) |
| `grok login/logout/inspect` | `inspect [--json]` 查看发现结果:模型/AGENTS.md/skills/plugins/hooks/MCP |
| `grok update` / `grok doctor` | 升级 / 诊断会话(终端、剪贴板、颜色、沙箱等) |

退出码:0 成功 · 1 错误 · 130 SIGINT · 143 SIGTERM(被中断的 headless 用 `grok -p "continue" -r <id>` 续跑;文件修改不回滚)。

## 斜杠命令(精选)

| 场景 | 命令 |
|------|------|
| 会话 | `/new`(/clear) · `/resume` · `/dashboard`(/agents-dashboard,/sessions,多会话台) · `/fork` 分支新 agent · `/rewind`(/undo) · `/compact [note]`(85% 上下文自动压缩,`[session] auto_compact_threshold_percent` 可调) · `/context` 用量明细 · `/copy [n\|文件]` · `/export` · `/rename [--auto]` · `/delete` · `/session-info`(/status,/info) · `/home`(/welcome) · `/quit`(/exit) |
| 模型/模式 | `/model <名> [effort]`(/m) · `/effort <level>` · `/always-approve` / `/auto`(互相切换) · `/plan [描述]` · `/view-plan`(/show-plan,/plan-view) · `/multiline`(/ml) · `/vim-mode` · `/minimal` / `/fullscreen`(/full) · `/compact-mode` |
| 记忆 | `/memory [on\|off]`(/mem,需 GROK_MEMORY=1) · `/flush` 压缩前固化知识 · `/dream` 合并主题 · `/remember <note>`(始终可用) |
| 扩展 | `/hooks` · `/plugins` · `/skills` · `/marketplace`(同一 modal 各 tab) · `/create-skill` 交互式建技能 · `/mcps` · `/config-agents`(/agents) · `/personas` |
| 规划/执行 | `/goal <目标> [--budget <tokens>]`(status/pause/resume/clear,完成需独立证据复核) · `/deep-research <查询>`(后台研究,独立验证碎片,部分失败标 Partial) · `/workflow <名> {args}`(pause/resume/stop/save) · `/workflows` 运行面板 · `/loop 30m <prompt>`(7 天过期,`scheduler_delete` 取消) |
| 其他 | `/btw <问题>` 旁路问询不打断任务 · `/theme`(/t) · `/feedback` · `/doctor [fix]` · `/docs [标题\|web]`(/howto,/guides) · `/tutorial`(/tour) · `/import-claude` 导入 ~/.claude 设置 · `/settings`(/config,/preferences) · `/usage`(/cost,`/usage manage` 计费) · `/privacy` · `/login` · `/logout` |

技能即斜杠命令(`user-invocable: true`);与内置重名时内置赢裸名,技能用限定名 `/local:name`、`/user:name`、`/plugin:name`。

## 快捷键(不可重绑定)

| 键 | 作用 |
|----|------|
| `Tab` | prompt/scrollback 焦点切换;阻塞卡片内行走选项 |
| `Enter` | 发送;turn 运行中=排队 follow-up,空输入框再按一次=发顶部队列;`Ctrl+Enter`/`Ctrl+I`(VS Code 系 `Ctrl+L`)=立即发送并打断当前 turn |
| `Shift+Tab` | 循环模式:Normal → Plan → Always-approve |
| `Esc` | turn 运行中取消(全屏 vim 模式是吞键,用 Ctrl+C);空闲时 800ms 内双击=清空草稿(非空)或开 rewind(空+有消息) |
| `Ctrl+C` | 取消 turn(有草稿先清草稿,再按取消) |
| `Ctrl+O` | 切换 always-approve(YOLO) |
| `Ctrl+P` 或 `?` | 命令面板(全部快捷键/斜杠命令/技能) |
| `Ctrl+M` | 模型选择器(焦点在 prompt 时=多行切换) |
| `Ctrl+S` | 会话选择器(恢复) |
| `Ctrl+N` | 新会话(1000ms 内双击确认,可选 worktree) |
| `Ctrl+\` | Agent Dashboard 多会话台 |
| `Ctrl+Q` | 退出(VS Code 系终端为 `Ctrl+D`;`Ctrl+D` 半页下滚) |
| `Ctrl+B` | 前台命令转后台 · `Ctrl+T` todos 面板 · `Ctrl+G` 任务/子 agent 面板 |
| `Ctrl+L` | 扩展 modal(仅非 VS Code 系) |
| `!` | 空 prompt 输入 `!` 进 shell 模式;`@` 附件选择器(`@file`、`@file:10-50`、`@dir`,`!` 前缀搜隐藏文件) |
| 滚动条 | `j`/`k` 或方向键;`H`/`L`(Shift+←/→)turn 导航;`g`/`G` 顶部/底部;`Ctrl+J`/`K` 逐行;`Ctrl+U`/`D` 半页;PgUp/PgDn |
| 折叠 | `h`/`l`(vim)或←/→ 折叠/展开;`e` 切换;`E` 全部;`Ctrl+E` thinking 块;`r` raw markdown;`y` 复制内容,`Y` 复制元数据,`Enter` 全屏查看 |
| 鼠标 | 点击选中/滚轮滚动/悬停高亮;Linux 中键粘 PRIMARY |

Vim 模式(`[ui] vim_mode = true` 或 `/vim-mode`)启用 `j/k` 等单键;`[ui] simple_mode = false` 是 prompt 编辑器的 vim 模式,两者独立。

## 内置工具与审批

| 工具 | 说明 |
|------|------|
| `read_file` / `search_replace` | 读文件 / 行级精确编辑 |
| `grep`(ripgrep)/ `list_dir` | 正则搜索 / 列目录 |
| `run_terminal_command` | 执行 shell 命令 |
| `web_search` / `web_fetch` | 搜索网页 / 抓取 URL |
| `todo_write` | 任务列表 |
| `spawn_subagent` | 并行子 agent |
| `memory_search` | 跨会话记忆搜索 |

- **自动执行(只读)**:read_file/list_dir/grep/web_search/todo_write/子 agent 控制/技能调用;只读 shell 命令白名单:`ls cat pwd date whoami hostname uptime ps head tail wc sort uniq tr cut`、`git status/log/diff/show/...`、`grep rg`、`kubectl get/logs/describe`
- **危险命令**(`rm chmod chown chgrp chattr pkill kill killall git push`)即使有记忆前缀也照常提示
- **权限规则**:`--allow`/`--deny` 或 config.toml `[permission] rules`/字符串数组,或 `.claude/settings.json`(`defaultMode` 支持 default/auto/acceptEdits/bypassPermissions/dontAsk/plan;`Ctrl+I` 导入);语法 `Bash(...)`(前缀匹配,`*` 匹配含空格)、`Read(src/**)`/`Edit(**/*.rs)`、`MCPTool(server__*)`、`WebFetch(domain:example.com)`;链式命令按段检查,deny 命中任一段即拒
- **评估顺序**:PreToolUse hooks → 权限规则(deny>ask>allow)→ 记忆授权 → 内置只读自动批准 → 提示策略
- **交互授权**:Allow once / Reject once(可带消息回给模型)/ 启用 always-approve / 允许本会话全部编辑;`[ui] remember_tool_approvals = true` 开启逐命令 "Always allow"(按项目持久化);管理员可用 requirements.toml `[ui] disable_bypass_permissions_mode = true` 锁定禁用 yolo

## 子 Agent 与 Personas

- 内置类型:`general-purpose`(全能力)/ `explore`(只读调研,不编辑)/ `plan`(产出实施计划,不编辑);**嵌套深度限 1 层**(子 agent 不能再生子)
- 参数:prompt / description / subagent_type / `background`(后台运行返回 ID,用 `get_command_or_subagent_output` 取结果)/ `capability_mode`(read-only / read-write / execute / all)/ `isolation`(none 或 worktree 隔离工作树)/ `resume_from`(续接已完成子 agent)/ `cwd`
- **Personas**:行为叠加层,注入为 `<system-reminder>`,不改类型/模型/工具;config.toml `[subagents.personas.<名>]` 或 `.grok/personas/*.toml`,字段:instructions/instructions_file/description/inputs/outputs(链式契约)/model/reasoning_effort/default_isolation;解析优先级:spawn 覆盖 > 角色默认 > persona 默认 > 父会话
- 配置:`[subagents.toggle]`(按类型开关)/ `[subagents.models]`(按类型换模型)/ `[subagents.roles.<名>]`(default_capability_mode/model/prompt_file);`GROK_SUBAGENTS=0` 或 `[subagents] enabled=false` 全禁
- MCP 继承:`mcpInheritance` all(默认)/ none / named / except;任务面板 `Ctrl+G`,todos `Ctrl+T`,子 agent 滚动块上 Enter 看完整转录

## MCP / Skills / Plugins / Hooks

- **MCP**:config.toml `[mcp_servers.<名>]`(stdio:command/args/env/enabled/startup_timeout_sec/tool_timeout_sec/tool_timeouts;HTTP:url/headers,流式会话 `{{session_id}}`);CLI `grok mcp add/list/remove/enable/disable/doctor`;工具名 `server__tool`,规则 `MCPTool(linear__*)`;结果上限 20KB 截断(`GROK_MAX_MCP_OUTPUT_BYTES` 调)
- **Skills**:`.grok/skills/<名>/SKILL.md`(frontmatter:name/description/when-to-use/allowed-tools/argument-hint/user-invocable/disable-model-invocation/model/effort/license/compatibility/metadata);发现优先级 `./.grok/skills` > repo > `~/.grok/skills`,兼容 `~/.claude/skills`、`~/.cursor/skills`、`.agents/skills`;**不遵循 .gitignore**;`[skills] paths/ignore/disabled` 管理;`/create-skill` 交互式创建;`grok inspect` 列出全部
- **Plugins**:`/plugins` modal 或 `grok plugins`;来源 marketplace / git 仓库 / 本地路径;装插件可带 skills/MCP/hooks
- **Hooks**:JSON 文件(`~/.grok/hooks/*.json` 或项目 `.grok/hooks/`),事件:`SessionStart`/`SessionEnd`、`UserPromptSubmit`、`PreToolUse`(可拒)、`PostToolUse`/`PostToolUseFailure`、`Stop`/`StopFailure`/`StopCancelled`、`Notification`、`SubagentStart`/`SubagentStop`;**fail open**(脚本崩了照放行,安全边界需自查错误);项目 hooks 需信任

## Plan Mode

`/plan` 或 `Shift+Tab` 进入,或 agent 主动调 `enter_plan_mode`(需你批准)。规划期只读,唯一可写 `plan.md`(会话目录内),其他文件编辑直接拒绝(任何权限模式都拦)。`exit_plan_mode` 打开审批预览:`a` 批准开建 / `s` 要求修改 / `c` 行内注释 / `y` 复制 / `q` 放弃。yolo 在 plan 模式底下仍生效(bash 照跑,只是文件编辑被 gate)。适合架构歧义任务;清晰任务别用。`/view-plan` 重新打开已存计划。

## AGENTS.md 项目规则

`~/.grok/AGENTS.md`(全局)< repo 根 `<repo>/AGENTS.md` < `<cwd>/AGENTS.md`(更深者优先);也读 `CLAUDE.md` 兼容。会话/技能/插件全由 `grok inspect` 查看发现结果。

## Headless 与 CI 要点

```bash
grok -p "Review changes for bugs" --output-format json --yolo | jq -r '.text' > review.md
grok -p "..." --output-format json | jq -r '.sessionId'   # 取 ID 供 -r 续跑
# 工具过滤:只读审查
grok -p "Explain" --tools "read_file,grep,list_dir"
# 护栏
grok -p "..." --yolo --rules "Never delete files" --deny 'Bash(rm*)'
# 输出事件流类型:thought / tool_call / tool_call_update / text / usage / plan / available_commands / end / error
```

每次 `-p` 默认全新会话;跨调用用 `-r`/`-c`。CI 无浏览器:`XAI_API_KEY` 或 `grok login --device-auth`。只读挂载 `~/.grok` 可让会话变临时(`GROK_DISABLE_AUTOUPDATER=1 --no-auto-update` 关更新检查)。

## 配置与文件位置

- `~/.grok/config.toml`:`[cli]`(auto_update) `[models]`(default/web_search) `[ui]`(permission_mode/remember_tool_approvals/screen_mode/follow_up_behavior) `[features]`(telemetry/lsp_tools) `[session]`(auto_compact_threshold_percent) `[permission]` `[mcp_servers]` `[memory]`(GROK_MEMORY=1 开启) `[subagents]` `[skills]` `[workflows]` `[compat.cursor/claude/codex]`(兼容扫描开关)
- **自定义模型/本地推理**(本地优先):`[model.<名>] model/base_url(OpenAI 兼容)/name/api_key/env_key/temperature/context_window/query_params/env_http_headers`;本地部署指 `base_url` 到内网端点即可离线
- 项目 `.grok/config.toml` 只贡献 `[mcp_servers]`/`[plugins]`/`[permission]`;`pager.toml` 管外观;`GROK_CONFIG`/`GROK_CONFIG_PATH` 是注入式 overlay(仅 allowlist 字段)
- 数据:`~/.grok/{config.toml, auth.json, sessions/(SQLite), memory/, skills/, plugins/, agents/, personas/, logs/, worktrees/}`;`GROK_HOME` 可整体搬家
- 关键环境变量:`XAI_API_KEY` `GROK_HOME` `GROK_MEMORY` `GROK_SUBAGENTS` `GROK_WORKFLOWS` `GROK_WEB_FETCH` `GROK_SANDBOX` `GROK_LOG_FILE` `RUST_LOG` `GROK_CLI_CHAT_PROXY_BASE_URL` `GROK_DEFAULT_SELECTED_PERMISSION` `MCP_TIMEOUT`

## 核心工作流(蒸馏要点)

1. 项目规则:写 AGENTS.md(比 CLAUDE.md 原生);`grok inspect` 验证发现
2. 架构歧义任务 → `/plan`(先探索→审阅→批准);清晰任务直接干
3. 长会话 → `/compact`(或调 85% 自动阈值);`/flush` 压缩前固话记忆
4. 独立任务 → 子 agent(explore 调研 / general-purpose 实现,worktree 隔离)
5. 权限分级:默认 ask + 窄 allow 规则;仅可信目录/CI 用 `--yolo` + deny 护栏
6. 自动化 → `-p --output-format json` + `-r` 续跑;ACP → `grok agent serve`
7. 外部工具 → MCP;重复流程 → skills;生命周期脚本 → hooks

## 对比定位(2026-08)

| 项目 | Grok Build | Claude Code | Codex CLI | OpenCode |
|------|-----------|-------------|-----------|----------|
| 语言 | Rust | Node.js | Rust | TypeScript |
| 许可 | **Apache 2.0** | 专有 | Apache 2.0 | MIT |
| 模型 | 任意(config.toml) | Anthropic | OpenAI | 75+ providers |
| 外部 PR | 不收 | n/a | 公开队列 | 社区项目 |
| 开源日期 | 2026-07-15 | — | — | — |

## 常见问题

- 用户问"怎么装 Grok Build" → `curl -fsSL https://x.ai/cli/install.sh | bash` 一行,`grok --version` 验证
- 用户问"和 Claude Code 比" → 见对比表;Grok Build 是 Rust 单二进制 + Apache 2.0 + 模型可换
- 用户想复刻其工作流 → "核心工作流"段:AGENTS.md + Plan + 审批分级 + headless CI + 子 agent 隔离
- 用户想本地跑 → 源码编译(cargo run -p xai-grok-pager-bin)+ `[model.<名>] base_url` 指向本地推理
- 用户想接编辑器 → `grok agent stdio`(ACP),Zed/Neovim(CodeCompanion/avante)/Emacs(agent-shell)/marimo 已支持,JetBrains 待发布