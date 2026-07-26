# Git 操作规则

## 1. 本地检查

本地只读检查使用 `git`，例如：

- `git status`
- `git diff`
- `git log`
- `git show`

## 2. 远端平台

当前 Git 远端平台：GitHub

- 远端：`origin` → `https://github.com/Chelase/aos-agent.git`（私有仓库）
- 默认分支：`main`，本地 `main` 跟踪 `origin/main`
- 推送前先确认工作区干净、提交粒度符合本文件第 5 节要求。

## 3. 提交内容语言

git 提交内容必须使用中文，包括：

- 提交信息（commit message）的标题和正文。
- 合并请求（MR/PR）的标题和描述。
- 议题（issue）的标题和描述。

## 4. 提交信息格式

git 提交信息必须同时遵守约定式提交规范（Conventional Commits）和中文描述要求。

提交标题格式：

```text
<type>(<scope>): <中文说明>
```

约束：

- `type` 必须使用约定式提交类型，例如 `feat`、`fix`、`docs`、`refactor`、`test`、`chore`、`build`、`ci`、`perf`、`style`、`revert`。
- `scope` 可选；能准确表达模块或功能域时优先填写，例如 `feat(auth): 支持 OAuth2 登录`。
- 冒号后的提交说明必须使用中文。
- 不要使用缺少 `type:` 前缀的纯中文提交标题，例如 `完善登录功能`。

## 5. 提交粒度

- 一个提交只做一件事。
- 不要在一个提交里混入无关改动（例如修 bug 的同时重构另一个模块）。
- 提交前用 `git diff --staged` 确认改动范围。
