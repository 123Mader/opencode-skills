---
name: deepseek-harness-cli
description: DeepSeek Harness(DSH)蒸馏技能。用户说"DeepSeek Harness""DSH""dsh""deepseek 编程 agent""一切皆插件""蒸馏 CLI 工具"时使用。包含 DSH 的安装运行(npx/源码)、CLI 入口模式(web/headless/profile)、Cordis 插件架构(profile/bundle/patch 层)、核心包与事件域、内置工具全家桶(bash/fs/terminal/subagent/skill/todo/workflow/web)、权限与沙箱、模型配置(DEEPSEEK_API_KEY)、开发与构建。来源: deepseek-ai/deepseek-harness (MIT, 2026-08) 与 deepseek.com/harness。
---

# DeepSeek Harness 蒸馏 (deepseek-harness-cli)

DeepSeek(深度求索)官方开源的 AI Agent 智能体框架,口号**一切皆插件(Everything is a Plugin)**。由 Cordis 插件框架驱动(设计论文: _A Programming Paradigm for Spatiotemporal Composability_, github.com/cordiverse/paper),每个组件——模型适配器、工具注册表、会话日志、agent 循环本身——都是插件,全部可替换、可卸载。TypeScript 编写,MIT 协议。

当前处于**开发者预览**,迭代快,会有破坏兼容性的变更。

来源: github.com/deepseek-ai/deepseek-harness · deepseek.com/harness · 仓库 docs/ 目录(architecture.md、tool-catalog.md、config-catalog.md、agent-lifecycle.md 等,中英双语)

## 安装与运行

```bash
# 最快方式(需 Node.js 22.19+ / 24+)
npx @deepseek-ai/dsh web          # 启动 Web UI,默认 http://127.0.0.1:3080

# 从源码(需 corepack pnpm,仓库锁 pnpm@11.7.0)
git clone https://github.com/deepseek-ai/deepseek-harness.git
cd deepseek-harness
corepack enable && pnpm install
pnpm run build                    # 生产运行需先构建产物
pnpm dsh web                      # TypeScript 入口,参数全部透传
```

- 启动目录 = 默认工作区根;Web UI 首次需在 Settings→Models 填 DeepSeek API key,再 Choose workspace 选项目目录
- 环境变量:`DEEPSEEK_API_KEY=sk-...`(必填,也可在 UI 里填)、`DEEPSEEK_BASE_URL=...`(可选,自定义 OpenAI 兼容端点);Harness home 目录由 `$DSH_HOME` 指定
- 模型配置见 docs/user/guide/providers.md(其他 provider / 自定义 OpenAI 兼容端点)

## CLI 入口模式(apps/cli)

| 命令 | 作用 |
|------|------|
| `dsh --profile <name>` | 启动命名 profile(位于 `$DSH_HOME/profiles/<name>`) |
| `dsh web` | `--profile web` 别名(浏览器 UI) |
| `dsh --profile headless "job"` | 一次性运行:新起一个持久会话,打印最终答案后退出(适合 CI/脚本) |
| `dsh plugin --profile <name> <pnpm args>` | 管理 profile 的插件(转发给 pnpm) |
| `dsh --dump-config` / `--dump-default-config` | 打印实际组合出的配置树,不启动(任何一行都能用 patch 覆盖) |
| `dsh --help` | launcher 自己的帮助(不带 profile 时) |

- launcher 只解析自己的 flags,第一个不认识的 token 之后全部归 app:`dsh --profile web --port 8080`(--port 属于 web app);`dsh --profile headless "run the tests"`
- web 与 headless 两个 profile 首次使用自动从内置模板初始化;其他 profile 必须通过 `dsh plugin` 创建
- 无效命令/错误选项/配置错误/启动失败均以非零退出

## 插件架构(Cordis 五概念)

1. **插件** = 实现 Service 的对象:带可选 `inject` + `apply(ctx)` 的函数,或 `Service` 子类
2. **上下文** = 服务仓库:服务声明稳定的 `ctx.<key>`(如 `ctx.tools`、`ctx.llm`、`ctx.sessions`),其他插件按 key 查找,不 import 具体实现
3. **`inject` 声明依赖**:所需服务存在才加载,加载顺序由服务依赖表达,而非手工排序
4. **类型化事件**:TS 声明合并声明事件名,用 `emit`(观察,不等待)/ `waterfall`(包裹,返回新值)/ `parallel`(并行,等待)/ `serial`(按序,等待)分派
5. **注册 = 可逆效果**:prompt 段、工具 schema、适配器、provider、监听器都用 `ctx.effect()` / `ctx.on()` 安装,插件卸载时自动回滚

waterfall 语义:`ctx.waterfall` 是中间件,listener 收到 `(...args, next)`,调 `next()` 委托(可返回被包裹的结果),不调即短路;单决策事件短路即设计(policy listener 拥有决策时直接返回)

## Profile / Bundle / Patch 分层

- **profile**:命名组合,存于 Harness home,列出它堆叠的 bundles、安装的树外插件、用户自己的 `cordis.patch.yml`;`dsh.profile` 字段在 package.json 里声明
- **bundle**:Cordis 配置行 + 其挂载代码的分发格式;`dsh.bundle` 指向 patch 文件
- **组合顺序**(从空表开始):每个 bundle 按 profile 列出的顺序 → profile 的 `cordis.patch.yml` → home 级 `$DSH_HOME/cordis.patch.yml` → `--patch` 覆盖层
- patch 按行 id 定位,可整体替换某行配置或插入新行
- 三个内置 bundle:`dsh-base`(第一层:模型适配器、工具、持久化、沙箱与审批策略、设置、凭据、遥测)、`dsh-web-app`(浏览器应用)、`dsh-headless`(无服务器的 one-shot runner)

## 核心包与事件域

| 包 | 职责 | ctx key |
|---|---|---|
| core/session | 追加式 `SessionEvent` 日志与内存存储 | `ctx.sessions` |
| core/system-prompt | prompt 段与工具 schema 组装 | `ctx.systemPrompt` |
| core/tools | 作用域工具注册表与受守卫的执行管线 | `ctx.tools` |
| core/agent | `Agent` 接口、实时注册表、`agent/*` 事件 | `ctx.agents` |
| core/agent-loop | 默认驱动 | `ctx.agentLoop` |
| llm/llm | 消息/流词汇与适配器接缝 | `ctx.llm` |

- **session 事件**:持久事实,追加进日志并广播(`session/event`);需跨重载存活的用这个
- **agent 事件**(`agent/*`):携带 live `Agent`(inbox/step/status/request/validation/continuation),观察或拦截进行中的工作
- **capability 事件**:把策略和适配器挂到接缝(`fs/*`、`tools/*`、`telemetry/*`)
- **turn 流**:step = 一次模型请求 + 其工具调用;turn = 零或多个 step。关键事件:`agent/pre-step`(决定模型看到什么,可改写/拒绝输入)、`agent/request`、`llm/stream`、`tools/pre-execute`→`tools/execute`→`tools/post-execute`(waterfall,需调 `next()`)、`agent/turn-stopping`(serial,无 next,可停 turn)
- **模型可见即日志化**:任何到达模型请求的内容必须能从日志重建,运行时不变式保证

## 内置工具(tool-catalog,均以插件包分发)

| 工具 | 来源包 | 说明 |
|---|---|---|
| `ask_user_question` | dsh-tool-ask-user | 需确认/选择/缺信息时提问;可多问题,每个带稳定 `id`(答案回显);`options` 可带推荐项(放第一并标 "(Recommended)");阻塞直到 UI 返回答案 |
| `bash` | dsh-tool-bash | 每次 `bash -c` 全新 shell,无状态(cwd/变量不保留,用 `workdir` 而非 `cd`);非零退出报 `[exit code: N]`;沙箱拒绝报 `[sandbox: file access denied under <mode> mode]`(策略拒绝,别换方式重试);长输出截尾但存文件报路径;`run_in_background: true` 返回 job id |
| `run_code` | dsh-tools | 执行代码 |
| `exit_plan_mode` | dsh-plan-mode | 退出 Plan Mode |
| `edit`/`read`/`read_image`/`write` | dsh-tool-fs | 文件读写 |
| `glob`/`grep` | dsh-tool-fs-search | 文件搜索 |
| `str_replace_editor` | dsh-tool-str-replace-editor | 字符串替换编辑 |
| `terminal_open/send/read/list/close/signal` | dsh-tool-terminal | 持久终端 |
| `create_goal/get_goal/update_goal` | dsh-tool-goal | 同会话目标管理 |
| `schedule_create/delete/list` | dsh-schedule | 定时任务 |
| `lsp` | dsh-tool-lsp | 语言服务器 |
| `skill` | dsh-tool-skill | 按会话技能目录里的确切名称加载技能指令 |
| `session_event_read/search/trace`、`session_search/trace` | dsh-tool-session-query | 会话日志查询 |
| `subagent`(默认 continuable,可后台) | dsh-tool-subagent | 委托自包含任务给子 agent(独立上下文,看不到本会话,需完整自足 prompt);`run_in_background` 返回 job id;另捆绑 `subagent_fork`(one-shot,前台默认) |
| `interrupt_agent`/`list_agents`/`send_message` | dsh-tool-subagent-control | 子 agent 控制 |
| `report` | dsh-tool-subagent-report | 子 agent 汇报 |
| `job_kill`/`job_list`/`job_output` | dsh-tool-jobs | 后台任务收集/停止 |
| `todo_write` | dsh-tool-todo | 任务清单 |
| `workflow` | dsh-tool-workflow | 工作流编排 |
| `web_fetch`/`web_search` | dsh-tool-web | 网页抓取与搜索 |
| `cordis_define`/`cordis_inspect_list`/`cordis_inspect_query`/`cordis_inspect_self`/`cordis_run`/`cordis_stop`/`cordis_undefine` | dsh-tool-cordis | 运行时定义/检查/运行 Cordis 插件 |

## 扩展方式(新行为挂到哪)

| 目标 | 机制 |
|---|---|
| 加模型 provider | 在 `ctx.llm` 注册适配器 |
| 加模型可见能力 | 注册到 `ctx.tools`,schema 自动进 prompt 组装 |
| 加 shell 执行 | 注册 `ctx.shell` 后端(本地经 `ctx.subprocess` 派生) |
| 加持久终端 | `ctx.terminals` 后端 + `dsh-tool-terminal` |
| 加人工命令 | 注册 `ctx.commands`(不经模型 turn 直接派发) |
| 加后台工作 | 注册 `ctx.jobs`;`job_*` 工具收集/停止 |
| 加文件访问或策略 | `ctx.fs` provider 或监听 `fs/*` 事件 |
| 限制派生进程 | `ctx.sandbox` 后端(spawn 前包裹 argv) |
| 拦截请求/工具/turn | `agent/*` 或 `tools/*` 事件;`agent/turn-stopping` 停 turn |
| 加模型可见上下文 | `agent.inject()`(进入下一次被接受的请求) |
| 给某会话不同能力集 | 组合 agent preset;service 行需 `isolate` realm |
| 派生活会话 | `ctx.sessions.fork(source, boundary?, childSessionId?)` |
| 注册限定单 agent | 用该 agent 的 `agent.ctx` |

## 开发与构建

- 首次: `pnpm install`(顺带装 worktree 级 Lefthook hooks)→ `pnpm run typecheck` 通过即完成
- 构建顺序:`tsc -b tsconfig.host.json` → `tsdown --env.DSH_BUILD_FACE host` → `tsc -b tsconfig.client.json` → `tsdown --env.DSH_BUILD_FACE client` → `pnpm run build:web`;或一条 `pnpm run build`
- Host/Client 两个聚合 tsconfig 各成 program(两侧都声明合并 cordis `Context`,一个 program 里会冲突);新包只注册进其中一个聚合
- 常用门禁:`pnpm run typecheck` / `pnpm run lint` / `pnpm run hygiene`(publint + verify-node-next-types)/ `pnpm run check:all`(全量本地门禁,独立于 git hooks)
- lefthook:`pre-commit` 验 i18n 配对记录 + Oxlint 修复 + 白空格检查;`pre-push` 跑 typecheck
- CI 覆盖 Node 22.19 / 24 / 26 兼容矩阵;真实 API e2e 在未设 `DEEPSEEK_API_KEY` 时自动跳过

## 社区与生态

- 反馈/bug: GitHub Discussions(不强制 PR,鼓励 Discussion)
- 插件仓库加 [`dsh-plugin`](https://github.com/topics/dsh-plugin) 话题便于发现
- 国际:Discord(discord.gg/Ycq5dCaS4);中文:企微群(扫 README 中文版二维码 + 问卷)、微信公众号
- 官方主页:deepseek.com/harness;核心文档目录:docs/architecture.md、docs/tool-catalog.md、docs/config-catalog.md(130KB 生成目录)、docs/cookbook/(extension-cookbook:加包/加工具/加 LLM 适配器/加 Chat 节点)、docs/postmortem/

## 与同类对比速记

- 与 Grok Build(`grok`,Rust/全屏 TUI/headless)/ Kimi Code(Go)/ Claude Code(Node)不同:DSH 是 **Web UI + headless 双面**,核心卖点是 Cordis 插件体系——没有特权内核,一切皆插件,连 agent 循环本身都可替换;插件卸载时所有注册自动回滚
- 同为 MIT 开源、同为 2026 年开源,但 DSH 更"框架化"(面向二次开发),另三者更"开箱即用的 CLI"