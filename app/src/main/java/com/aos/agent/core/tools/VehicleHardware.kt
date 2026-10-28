package com.aos.agent.core.tools

/**
 * 车辆硬件动作禁令。
 *
 * 边界（2026-10-28 明确）：
 * - **允许**：操作车机系统内部——查隐藏模块/功能/数据、启用被隐藏的功能
 *   （行车记录仪、隐藏诊断页、工程模式等）。
 * - **禁止**：触碰车辆硬件——让车动、开门、开空调、动方向盘/刹车/油门/换挡，
 *   或向车机注入物理按键事件。
 *
 * 这条边界不能只写在系统提示里：模型会换参数、换工具重试，甚至拼一条绕过
 * 白名单的命令。所以判定放在 harness，被判定的调用**不执行、不弹确认框**。
 *
 * 判定分两类：
 * 1. **命令类**（[commandReason]）：按 argv[0] 与子命令判定。
 *    - 硬件注入原语（`input`/`sendevent`）、提权、块设备写入、电源动作整条禁；
 *    - **命令包装器必须一起禁**（`sh`/`python`/`toybox`/`timeout`…），
 *      否则 `sh -c "cmd car_service set-power off"` 会让按名限制形同虚设；
 *    - 车机内部的调度器（`cmd`/`pm`/`am`/`settings`/`setprop`）**放行**，
 *      它们正是"调出隐藏功能"要用的工具；只有 `cmd <车辆服务> <写动作>` 才拦。
 * 2. **远端工具类**（[toolReason]）：MCP 工具在远端实现、行为不可知，
 *    只能按命名特征判断。
 */
object VehicleHardware {

    /**
     * 整条禁掉的程序。
     *
     * 分四组：硬件事件注入、提权、块设备/电源、命令包装器。
     */
    private val HARD_BLOCKED_PROGRAMS = setOf(
        // 硬件事件注入：会触发物理按键或车机对外动作
        "input", "sendevent", "evemu", "uinput",
        // 提权
        "su", "sudo", "doas", "runuser", "setuid", "chown", "chmod",
        // 块设备与内核模块：能直接写硬件
        "dd", "mknod", "mount", "umount", "insmod", "rmmod", "modprobe", "devmem",
        // 电源：重启车机等于整车断电
        "reboot", "poweroff", "halt", "kexec",
        // 命令包装器：能藏住任意命令，也就能藏住硬件动作
        "sh", "bash", "zsh", "ksh", "mksh", "dash", "ash", "csh", "tcsh",
        "busybox", "toybox", "timeout", "nice", "ionice", "env", "nohup", "setsid",
        "stdbuf", "xargs", "exec", "watch", "script",
        "python", "python2", "python3", "perl", "ruby", "php", "node", "nodejs",
        "awk", "gawk", "mawk", "lua", "java", "app_process", "dalvikvm", "kotlin", "groovy",
    )

    /**
     * `find` 的危险参数：`-exec`/`-delete` 能执行任意命令或删文件。
     * 只读遍历（找隐藏模块、配置文件）要放行，所以按参数判而不是整条禁。
     */
    private val FIND_DANGEROUS_FLAGS = setOf(
        "-exec", "-execdir", "-ok", "-okdir", "-delete", "-fprintf", "-fprint",
    )

    /** 车辆服务名里的特征词：命中即认为这个 service 属车辆域。 */
    private val VEHICLE_SERVICE_TOKENS = setOf("car", "vehicle", "vhal", "hvac", "climate", "ev")

    /** 写动作词：作为子命令出现即视为"要改东西"。 */
    private val WRITE_ACTION_TOKENS = setOf(
        "set", "write", "control", "actuate", "adjust", "change", "update", "toggle",
        "put", "post", "apply", "enable", "disable", "lock", "unlock", "open", "close",
        "start", "stop", "power", "send", "run", "exec", "invoke", "call", "trigger",
        "press", "release", "move", "drive", "park",
    )

    /**
     * 中文写动作词。
     *
     * 远端工具名可能是 `action_1` 这种无意义名，写动作信号只在描述里。
     * 描述走子串匹配，所以这组只收中文——英文 `set` 会命中 `offset`，不能用于子串匹配。
     */
    private val WRITE_ACTION_TEXTS = setOf(
        "打开", "关闭", "设置", "开启", "启用", "禁用", "调节", "调整", "执行",
        "触发", "启动", "停止", "操控", "控制", "接管", "按下", "松开", "挂挡", "换挡",
    )

    /** 远端工具名的读取前缀：以这些开头的一律视为读操作。 */
    private val READ_PREFIXES = listOf(
        "get", "read", "list", "query", "fetch", "describe", "dump", "show", "inspect", "peek", "stat", "search",
    )

    /** 车辆硬件名词：远端工具命中这些才可能是硬件动作。 */
    private val HARDWARE_TOKENS = setOf(
        "hvac", "climate", "temperature", "temp", "door", "window", "seat", "mirror",
        "wiper", "light", "headlight", "horn", "trunk", "engine", "gear", "steer",
        "brake", "throttle", "accelerator", "cruise", "sunroof", "airbag", "ecall",
        "cluster", "hud", "defrost", "fan", "epb", "lock", "unlock",
        // 中文：远端工具名可能是 action_1 这类无意义名，硬件词只出现在描述里
        "空调", "车门", "车窗", "座椅", "后备箱", "喇叭", "大灯", "雨刷", "天窗",
        "方向盘", "刹车", "油门", "加速", "换挡", "驻车", "车速", "巡航", "气囊",
    )

    /**
     * 判定一条 shell 命令是否触碰车辆硬件。
     *
     * 只看 argv：命令不经 shell 逐个执行（见 [ShellTool]），真正被执行的就是
     * argv[0] 及其参数，所以子命令也要一起看。
     */
    fun commandReason(argv: List<String>): String? {
        val program = argv.firstOrNull()?.let { basename(it) }?.lowercase() ?: return null
        return when (program) {
            in HARD_BLOCKED_PROGRAMS -> blockedReason(program)
            "find" -> findReason(argv.drop(1))
            "cmd" -> cmdReason(argv.drop(1))
            "service" -> serviceReason(argv.drop(1))
            else -> null
        }
    }

    /**
     * `cmd <service> <sub> ...`。
     *
     * `cmd` 是通用调度器——查隐藏模块（`cmd package list`）、调出隐藏功能
     * （`cmd package enable`、`cmd overlay enable`）都要用它，所以**整条放行**。
     * 只有对车辆服务下写动作时才拦：`cmd car_service set-power off` 是动硬件，
     * 而 `cmd car_service list` 只是读。
     */
    private fun cmdReason(args: List<String>): String? {
        val service = args.firstOrNull() ?: return null
        if (!isVehicleService(service)) return null
        val sub = args.getOrNull(1) ?: return null
        // 子命令常是连字符形式（set-power / set-hvac / set-window-position），
        // 要按词拆开看，不能整串比对。
        val tokens = tokenize(sub).map { it.lowercase() }
        if (tokens.none { it in WRITE_ACTION_TOKENS }) return null
        return "命令 $service $sub 试图修改车辆硬件状态；本 Agent 只允许操作车机系统内部，" +
            "禁止操控车辆硬件，该调用已被拒绝。如实告知用户该操作不可用。"
    }

    /**
     * `service list|call ...`。
     *
     * `service list` 是查隐藏服务的重要手段，放行。`service call` 是通用 binder
     * 原语，能绕过权限层直接调服务写接口：
     * - 车辆服务（`car_service`/`vehicle`/…）的写事务直接驱动硬件，拦；
     * - 纯数字的服务号无法判断指向哪个服务，等于无法判定，拦；
     * - 其余车机内部服务按"允许操作车机系统内部"放行。
     */
    private fun serviceReason(args: List<String>): String? {
        val sub = args.firstOrNull()?.lowercase() ?: return null
        if (sub != "call") return null
        val service = args.getOrNull(1) ?: return null
        // 纯数字的服务号无法判断指向哪个服务，等于无法判定，拦。
        val isOpaqueCode = service.all { it.isDigit() }
        if (isOpaqueCode || isVehicleService(service)) {
            return "命令 service call $service 能绕过权限层直接调用系统服务写接口；" +
                "本 Agent 禁止操控车辆硬件，该调用已被拒绝。"
        }
        return null
    }

    /** `find` 只在带执行/删除类参数时拦，纯遍历放行。 */
    private fun findReason(args: List<String>): String? {
        val hit = args.firstOrNull { it.lowercase() in FIND_DANGEROUS_FLAGS } ?: return null
        return "命令 find $hit 能执行任意命令或删除文件；该调用已被拒绝。"
    }

    private fun blockedReason(program: String): String =
        "命令 $program 可能用于操控车辆硬件或绕过底层限制；本 Agent 只允许操作车机系统内部，" +
            "禁止操控车辆硬件，该调用已被拒绝。如实告知用户该操作不可用。"

    private fun isVehicleService(service: String): Boolean =
        tokenize(service).any { it.lowercase() in VEHICLE_SERVICE_TOKENS }

    /**
     * 判定一个远端工具是否疑似操控车辆硬件。
     *
     * MCP 工具在远端实现，行为不可知，只能按命名特征判断：
     * 同时出现"写动作"与"硬件名词"才拦。带读取前缀的放行。
     *
     * 车辆域的 server：写动作工具一律拦——远端不可知，`set_temperature` 与
     * `enable_dashcam` 只差一个词，不能靠猜。隐藏功能优先走 shell
     * （`pm`/`settings`/`am`/`setprop`），那里判定是确定的。
     */
    fun toolReason(serverId: String, toolName: String, description: String): String? {
        val tokens = tokenize(toolName)
        if (tokens.isEmpty()) return null
        val isRead = tokens.first().lowercase() in READ_PREFIXES
        if (isRead) return null
        val hasWrite = tokens.any { it.lowercase() in WRITE_ACTION_TOKENS } ||
            WRITE_ACTION_TEXTS.any { description.contains(it) }
        // 硬件词可能只在描述里（工具名可能是 action_1 这类无意义名），
        // 描述用子串匹配——中文分词不可靠，子串对中英文都成立。
        val lowerDescription = description.lowercase()
        val hasHardware = tokens.any { it.lowercase() in HARDWARE_TOKENS } ||
            HARDWARE_TOKENS.any { lowerDescription.contains(it.lowercase()) }
        val serverIsVehicle = tokenize(serverId).any { it.lowercase() in VEHICLE_SERVICE_TOKENS }
        val suspect = when {
            serverIsVehicle -> hasWrite
            else -> hasWrite && hasHardware
        }
        if (!suspect) return null
        return "远端工具 $toolName 疑似操控车辆硬件；本 Agent 禁止操控车辆硬件，" +
            "该调用已被拒绝。如需车辆状态，请改用只读诊断工具。"
    }

    private fun basename(program: String): String =
        program.substringAfterLast('/').substringAfterLast('\\')

    /** 拆词：支持下划线、连字符与驼峰。 */
    private fun tokenize(name: String): List<String> =
        name.replace(Regex("([a-z0-9])([A-Z])"), "$1_$2")
            .split(Regex("[^A-Za-z0-9]+"))
            .filter { it.isNotEmpty() }
}
