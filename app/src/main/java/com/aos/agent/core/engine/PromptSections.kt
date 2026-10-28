package com.aos.agent.core.engine

/**
 * Agent harness 默认系统提示分节。顺序与 [PromptAssembler] 的 order 字段一致。
 * 每节 `render` 按当轮 [PromptContext] 求值，不在构造期烘焙。
 */
object PromptSections {

    val persona = PromptSection(
        name = "persona",
        order = 10,
        stable = true,
        render = {
            "你是运行在车机上的 Agent AOSAgent。回答简短直接，给数值必须带单位；" +
                "工具返回 status 而不是数值时，如实说明读不到及原因，不要编造，也不要把缺失当成 0。"
        },
    )

    val environment = PromptSection(
        name = "environment",
        order = 20,
        render = { ctx ->
            buildString {
                appendLine("运行环境：")
                appendLine("- 包名：${ctx["package_name"]}")
                appendLine("- 版本：${ctx["version_name"]} (${ctx["version_code"]})")
                appendLine("- Android API：${ctx["android_api"]}")
                appendLine("- Automotive：${ctx["automotive"]}；Car API：${ctx["has_car_api"]}")
                appendLine("- 当前时间：${ctx["now"]}")
                append("- 屏幕：${ctx["screen"]}")
            }
        },
    )

    val capabilities = PromptSection(
        name = "capabilities",
        order = 30,
        render = { ctx ->
            buildString {
                appendLine("已注册工具：")
                append(ctx["tools"].ifBlank { "（无）" })
                val skills = ctx["skills"]
                if (skills.isNotBlank()) {
                    appendLine()
                    appendLine()
                    append("已加载技能：$skills")
                }
            }
        },
    )

    val cli = PromptSection(
        name = "cli",
        order = 40,
        render = { ctx ->
            buildString {
                appendLine("应用自带 CLI（aos-llm）：")
                appendLine("- 绝对路径：${ctx["cli_path"]}")
                appendLine("- 是否已安装：${ctx["cli_present"]}")
                appendLine("- 子命令：${ctx["cli_commands"]}")
                append("- 查询当前模型配置示例：shell_exec 执行 `${ctx["cli_path"]} status`")
            }
        },
    )

    val rules = PromptSection(
        name = "rules",
        order = 50,
        stable = true,
        render = {
            """
            |规则：
            |- 用户给出的真实数值与工具读数矛盾时，以用户为准，并写明「工具读数与实际不符」。
            |- 需要底层数据或本机配置时，优先调用 shell_exec / vehicle_basic / system_info，不要以「没有提供给我」作答。
            |- 不要声称自己无法访问未开放的底层接口——你持有 shell_exec，应用 uid 能跑的命令就应去跑。
            """.trimMargin()
        },
    )

    fun defaults(): List<PromptSection> = listOf(persona, environment, capabilities, cli, rules)
}
