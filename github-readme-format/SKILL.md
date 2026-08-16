---
name: github-readme-format
description: Use when creating or rewriting a GitHub project README to publish/分享/公开 a repo, when the user asks to 按第一个的形式/统一格式/发布格式/和之前一样的形式 to polish a README, or when starting a new GitHub project that needs a clear 中英双语 public-facing README. Front-load when user says 优化 README、GitHub 发布、整理成公开项目.
---

# GitHub 发布格式（README 模板）

## Overview

用户发布的 GitHub 项目 README 采用统一格式：**居中头部（标题+英文副标题+徽章+双语标注）→ emoji 分节 → 表格化信息**。两次实战验证过（termux-opencode-deploy、bili-auto-publish），用户指定"跟第一个一样的形式"即指本模板。

## When to Use

- 新建 GitHub 项目要写公开 README
- 重写/优化已有 README（用户说"优化一下""清晰一点""跟第一个一样的形式"）
- 把内部文档整理成公开项目（"整理为公开项目"）

## 模板

```markdown
<div align="center">

# 📦 <Emoji> <项目名>

**<英文一句话副标题>**

[![Badge 名](https://img.shields.io/badge/<badge>-<value>-<color>.svg)](<链接>)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

*简体中文 | English*

</div>

---

## 🚀 这是什么

<项目定位 1-2 句>

- ✅ 要点 1
- ✅ 要点 2
- ✅ 要点 3（如果项目背景有"为什么做这个"的坑/动机，可加一段说明）

## ⚡ 快速开始

### 1️⃣ 步骤标题
```bash
命令
```

### 2️⃣ 步骤标题
```bash
命令
```

### 3️⃣ 步骤标题
```bash
命令
```

<补充说明（如"把视频放到…文件名即标题"这类关键约定）>

---

## ⚙️ 配置

<说明配置文件来源>

| 字段 | 默认值 | 说明 |
|---|---|---|
| `xxx` | `yyy` | 说明 |

## 🏗️ 架构

<ASCII 架构图或组件链路说明>

---

## 🧪 测试

```bash
<测试命令>
```

---

## ❓ 常见问题

| 问题 | 解决 |
|---|---|
| <报错现象> | <解决方式> |

---

## 📜 版本记录

- <关键特性/版本说明要点>

---

## ⚖️ 合规 & 免责说明

<合规声明、免责声明，结尾 *与官方无关* 声明（如适用）>
```

## 格式规则

1. **居中头部**：`<div align="center">` 包标题、副标题、徽章，最后 `*简体中文 | English*`
2. **徽章**：用 shields.io，至少 2-3 个（技术栈/工具版本/License）；README 提到但仓库没有的元素不要做假链接，可指向官方站或 `LICENSE`
3. **分节标题**：emoji 开头，统一用 🚀 ⚡ ⚙️ 🏗️ 🧪 ❓ 📜 ⚖️（第一个项目用过的 ✅ 风格一致）
4. **快速开始**：分 1️⃣ 2️⃣ 3️⃣ 步骤，每步一个代码块，最后给关键约定说明
5. **信息表格化**：配置项、常见问题用 Markdown 表格（不用散列表）
6. **代码块标注语言**：bash / json / python 等
7. **分隔线**：头部与正文之间 `---`，大节之间可用 `---`
8. **中文为主，标题带英文副标题**，整体双语暗示
9. **结尾**：合规/免责声明 + 星标引导（可选）+ 与官方无关声明

## 已应用实例（参考）

- `termux-opencode-deploy`：工具类项目，徽章=Termux/OpenCode/Install
- `bili-auto-publish`：脚本类项目，徽章=Python/Bilibili/License

## Common Mistakes

- 头部不加 `<div align="center">` → 标题不会居中，格式立刻不像
- 徽章链接指向不存在的资源 → 用官方链接或 LICENSE 文件
- 快速开始只有一条命令不加步骤 → 用户要求"清晰"，必须 1️⃣2️⃣3️⃣ 分步
- 分节标题不用 emoji → 与已发布项目风格不一致
- 配置/FAQ 用散列表 → 必须转成 Markdown 表格
