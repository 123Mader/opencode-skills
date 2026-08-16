---
name: distilling-skill-cards
description: Use when a complex task just finished (bug fixed, reverse-engineering milestone, architecture decision, hard-won lesson) and the user says 记一下/沉淀/蒸馏/存卡/存档, or at session end when notable reusable knowledge was produced. Also use at the START of a new session on a similar task to RETRIEVE relevant past skill cards before planning.
---

# 技能卡蒸馏（TRS 式记忆流程）

把「一次性经验」蒸馏成「可复用技能卡」，存到 `~/.config/opencode/memory-cards/`。

## 何时用

- 复杂任务收尾后（用户说"记一下/沉淀/存档"，或明显有跨任务价值的知识产生）
- 新会话开始，遇到与记忆卡目录中同类任务时——先检索卡再动手

## 操作流程

### 蒸馏（一次经验 → 一张卡）

1. 判断是否值得存：跨任务可复用 > 项目特定事实。项目特定事实进 AGENTS.md 即可，不值得出卡
2. 按模板写卡，存为 `~/.config/opencode/memory-cards/YYYYMMDD-主题-kebab-case.md`
3. 若存在同主题旧卡，合并并保留日期最新者
4. 更新卡片总数到本文件下方索引

### 检索（新任务 → 找卡）

1. 会话开始/任务启动时，`ls ~/.config/opencode/memory-cards/` 看主题
2. 匹配主题则 `cat` 对应卡，把卡中「规则」注入当前推理
3. 验证卡中规则是否仍适用（环境变化应更新卡而非盲从）

## 卡片模板

```markdown
# 主题：<一句话主题>

## 现象
当初的难题是什么

## 根因 / 教训
为什么发生（找根因链，不要止于表象）

## 规则（可复用）
- <行动规则 1>
- <行动规则 2>
- 反例：什么情况下规则不适用

## 验证状态
- [ ] 已在本项目按此规则验证
```

## 与 AGENTS.md 的关系

- AGENTS.md = 用户的长期项目记忆主文件（会话连续性主来源）
- memory-cards/ = 我自己的经验卡库（跨项目复用）
- 蒸馏出的「全局通用经验」记录进 AGENTS.md「经验资产」节，卡库保留详细版