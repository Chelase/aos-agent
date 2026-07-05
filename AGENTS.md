# AGENTS.md

本文件是 Codex 和其他 Agent 的桥接入口，不重复维护完整规则。

在本项目开始分析、编辑、测试、评审或提交准备之前，必须先读取：

1. `.agent-rules/README.md`
2. `.agent-rules/` 下实际存在的全部项目规则和相关机制文档
3. 与任务相关的项目文档

项目级规则统一维护在根 `.agent-rules/`。

规则优先级：

1. 系统或宿主工具指令。
2. 用户当前明确提出的要求。
3. 根 `.agent-rules/rules/` 下的项目规则。
4. 根 `.agent-rules/mechanisms/` 下的稳定机制文档。
5. 本文件或其他 Agent 桥接文件中的摘要说明。
6. 其他项目文档。
