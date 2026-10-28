package com.aos.agent.core.tools

/**
 * 车辆操控禁令。
 *
 * 硬约束：**允许操作底层，禁止操控车辆**。
 *
 * 这条约束不能只写在系统提示里——模型会换参数、换工具重试，甚至直接拼一条
 * 绕过白名单的命令。所以判定放在 harness 里，被判定的调用**不弹确认框、不执行**，
 * 直接以 [RejectionReason.VehicleControlForbidden] 拒掉。
 *
 * 判定分两类：
 * 1. **命令类**（[commandReason]）：能发起动作的二进制 + 能包装任意命令的解释器。
 *    解释器必须一起禁，否则 `sh -c "..."` 会让按名限制形同虚设。
 * 2. **远端工具类**（[toolReason]）：MCP 工具不可信，只能按命名与描述里的
 *    "写动作 + 车辆名词"组合判断；带读取前缀的一律放行。
 */
object VehicleActuation {

    /**
     * 硬禁程序。
     *
     * 分三组：车辆控制通道、能拉起/杀服务的调度器、能包装任意命令的解释器。
     * 代价是 `cmd package list` 这类只读子命令也一并禁掉——`cmd` 本身是通用
     * 调度器，禁整条二进制才 airtight；`service`、`input`、`sendevent`、`am`
     * 同理，它们都能触发设备侧动作而不只是读状态。
     */
    private val BLOCKED_PROGRAMS = setOf(
        // 车辆控制通道与设备动作注入
        "service", "cmd", "input", "sendevent", "am", "monkey", "svc",
        "start", "stop", "kill", "killall", "pkill", "reboot", "poweroff", "halt", "setprop",
        // 应用安装与提权
        "pm", "install", "uninstall", "su", "sudo", "doas", "runuser",
        // 命令包装器：能绕过任何按名限制
        "sh", "bash", "zsh", "ksh", "mksh", "dash", "ash", "csh", "tcsh",
        "busybox", "toybox", "timeout", "nice", "ionice", "env", "nohup", "setsid",
        "stdbuf", "xargs", "find", "exec", "watch", "script",
        // 通用解释器：能执行任意代码
        "python", "python2", "python3", "perl", "ruby", "php", "node", "nodejs",
        "awk", "gawk", "mawk", "lua", "java", "app_process", "dalvikvm", "kotlin", "groovy",
    )

    /** 远端工具名的读取前缀：以这些开头的一律视为读操作。 */
    private val READ_PREFIXES = listOf(
        "get", "read", "list", "query", "fetch", "describe", "dump", "show", "inspect", "peek", "stat", "search",
    )

    /** 写动作词。 */
    private val WRITE_TOKENS = setOf(
        "set", "write", "control", "actuate", "adjust", "change", "update", "toggle", "put", "post",
        "apply", "enable", "disable", "lock", "unlock", "open", "close", "start", "stop", "power",
        "send", "run", "exec", "invoke", "call", "trigger", "press", "release", "move", "drive",
    )

    /** 车辆/车身相关词。 */
    private val VEHICLE_TOKENS = setOf(
        "car", "vehicle", "hvac", "climate", "temperature", "temp", "door", "window", "seat",
        "mirror", "wiper", "light", "headlight", "horn", "trunk", "charge", "engine", "gear",
        "steer", "brake", "throttle", "accelerator", "speed", "cruise", "sunroof", "airbag",
        "ecall", "emergency", "cluster", "hud", "park", "defrost", "fan", "epb",
    )

    /**
     * 判定一条 shell 命令是否越界。
     *
     * 只看 argv[0]：命令不经 shell 逐个执行（见 [ShellTool]），所以真正被执行的就是
     * argv[0] 这个二进制。包装器（`sh`、`python` 等）本身就在禁名单里，
     * 因此不存在"用包装器藏一条禁命令"的口子。
     */
    fun commandReason(argv: List<String>): String? {
        val program = argv.firstOrNull()?.let { basename(it) }?.lowercase() ?: return null
        if (program !in BLOCKED_PROGRAMS) return null
        return "命令 $program 可能用于操控车辆或绕过底层限制；本 Agent 只允许只读诊断，" +
            "禁止操控车辆，该调用已被拒绝。如实告知用户该操作不可用。"
    }

    /**
     * 判定一个远端工具是否疑似操控车辆。
     *
     * MCP 工具在远端实现，行为不可知，只能按命名与描述里的特征判断：
     * 同时出现"写动作"与"车辆名词"才拦。带读取前缀的放行，
     * server 本身就是车辆域时，除读工具外全拦。
     */
    fun toolReason(serverId: String, toolName: String, description: String): String? {
        val tokens = tokenize(toolName)
        if (tokens.isEmpty()) return null
        val isRead = tokens.first().lowercase() in READ_PREFIXES
        val hasWrite = tokens.any { it.lowercase() in WRITE_TOKENS }
        val hasVehicle = tokens.any { it.lowercase() in VEHICLE_TOKENS }
        val serverIsVehicle = tokenize(serverId).any { it.lowercase() in VEHICLE_TOKENS }
        val suspect = when {
            isRead -> false
            serverIsVehicle -> true
            else -> hasWrite && hasVehicle
        }
        if (!suspect) return null
        return "远端工具 $toolName 疑似操控车辆；本 Agent 禁止操控车辆，该调用已被拒绝。" +
            "如需车辆状态，请改用只读诊断工具。"
    }

    private fun basename(program: String): String =
        program.substringAfterLast('/').substringAfterLast('\\')

    /** 拆词：支持下划线、连字符与驼峰。 */
    private fun tokenize(name: String): List<String> =
        name.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
            .split(Regex("[^A-Za-z0-9]+"))
            .filter { it.isNotEmpty() }
}
