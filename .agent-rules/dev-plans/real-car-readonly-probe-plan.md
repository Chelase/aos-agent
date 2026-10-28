# 实车只读数据挖取计划

> 目标：所有关于"这台车到底能给什么数据"的结论，只允许来自实车命令输出。
> 本文只定义"测什么、怎么测、留存什么、什么数据说明什么"。**本文不含任何结论。**

## 0. 硬约束（违反即作废，重测）

| # | 约束 | 说明 |
|---|---|---|
| R1 | **只读** | 只用 `dumpsys` / `getprop` / `lshal` / `ls` / `cat` / `screencap` / `wm` / `pm list`。 |
| R2 | **禁止写系统状态** | 禁止 `inject-vhal-event`、`setprop`、`pm grant/revoke`、`cmd car_service` 的写子命令、`settings put`。 |
| R3 | **禁止推断** | 结论必须能指向一条原始输出文件。没有原始输出的，结论栏只能写"未测"。禁止出现"可能/应该/大概率/推测"。 |
| R4 | **判据先写** | 每条测量的判据（什么数据→什么事实）在开测前写死在表里，测完不许改。防止事后解释。 |
| R5 | **原始输出必须留存** | 每条命令的输出落盘到 `evidence/<编号>-<名称>.txt`，同时记录设备时间与主机时间。 |
| R6 | **唯一允许的写** | 安装被测 APK（`adb install`）。除此之外不改设备任何状态。 |

## 1. 现状：需要撤回的结论

2026-10-27 复测记录里以下几条属于推断，**撤回为"未测"**，由本计划重新取证：

| 原表述 | 撤回原因 |
|---|---|
| "这台车的 VHAL 是 AOSP 默认 fake 实现" | 未查 HAL 实现、未查全量属性表，仅凭 INFO_MAKE 字符串推断 |
| "`Toy Vehicle Speedy Model` 是 AOSP 默认假车型" | 该字符串的出处未在本机核实 |
| "P0-1 电量的复验在这台车上无效" | 建立在上面两条之上 |
| "车辆数据全是假值" | 未取得外部真值对照（仪表盘读数） |

保留为事实的只有：**命令输出里写了什么**。

## 2. 测量矩阵

编号规则：`M<序号>`。每条都要产出 `evidence/M<序号>-<名称>.txt`。

### A 组 · 构建与系统事实（只读）

| 编号 | 目的 | 命令 | 判据（测前固定） |
|---|---|---|---|
| M01 | 构建身份 | `getprop ro.build.fingerprint; ro.build.tags; ro.secure; ro.debuggable; ro.build.type` | 输出原样记录，不做解释 |
| M02 | 平台能力 | `pm list features` | 记录是否含 `FEATURE_AUTOMOTIVE`、`android.hardware.type.automotive` |
| M03 | 显示参数 | `wm size; wm density; dumpsys display \| head -40` | 记录物理分辨率/密度/内容区高度 |
| M04 | 网络出口 | `ip route; dumpsys connectivity \| grep -E "NetworkAgentInfo\|LinkAddresses"` | 记录默认网络类型与网关 |
| M05 | 外网可达 | `curl -s -o /dev/null -w "%{http_code}" https://api.github.com` | 仅记录状态码 |

### B 组 · VHAL 实现识别（核心，只读）

| 编号 | 目的 | 命令 | 判据（测前固定） |
|---|---|---|---|
| M10 | HAL 服务实现 | `lshal -i \| grep -i vehicle` | 记录实例名、包名、版本、二进制路径 |
| M11 | VHAL 二进制位置 | `ls -l /vendor/bin/hw/ \| grep -i vehicle` | 记录文件名与大小 |
| M12 | VHAL init 配置 | `ls /vendor/etc/init/ \| grep -i vehicle` | 记录配置文件是否存在 |
| M13 | VHAL 相关属性 | `getprop \| grep -iE "vhal\|vehicle\|car"` | 原样记录全部属性行 |
| M14 | **全量属性表** | `dumpsys car_service \| sed -n '/Supported Properties/,/^$/p'` | 记录每个 property 的 id/name/areaType/access/min/max。**属性条目数本身即数据** |
| M15 | 属性配置详情 | `dumpsys car_service \| sed -n '/mConfigs/,/^$/p'` | 记录 config 原文 |
| M16 | CarService 版本 | `dumpsys car_service \| head -40` | 记录 Car version / Platform version / EnabledFeatures |
| M17 | VHAL 是否含厂商属性 | 从 M14 输出中筛 id ≥ 0x20000000 的条目 | 只数条数并贴原文，不解释含义 |

### C 组 · 车辆属性实测（只读 · 时间序列）

**目的**：用"数值是否会变"区分静态数据与实时数据，用"仪表盘读数"做外部真值锚定。

| 编号 | 目的 | 命令 | 判据（测前固定） |
|---|---|---|---|
| M20 | 静止态读数 T0 | `dumpsys car_service \| sed -n '/Vehicle Properties/,/^$/p'` | 记录 EV_BATTERY_LEVEL / EV_CURRENT_BATTERY_CAPACITY / PERF_VEHICLE_SPEED / ENV_OUTSIDE_TEMPERATURE 的 value |
| M21 | 行驶态读数 T1 | 同上，在车辆行驶 ≥ 3km 后执行 | 与 T0 逐字段比对：**完全相同 = 静态数据；有变化 = 实时数据** |
| M22 | 充电/用电态 T2 | 开空调压缩机 ≥ 5 分钟后执行 | 同上，比对 EV_BATTERY_LEVEL 是否下降 |
| M23 | 外部真值 | **人工读仪表盘 SOC**，拍照或手抄 | 与 M20 的 EV_BATTERY_LEVEL 换算值对照，差值记录 |
| M24 | 里程属性 | `dumpsys car_service \| grep -i -E "odometer\|mileage\|MILEAGE"` | 记录属性是否存在、value 是否为数值 |
| M25 | 厂商应用特权 | `dumpsys package com.car.sgmw.hvac \| grep -E "android.car.permission"` | 记录 granted 列表原文 |
| M26 | 厂商应用特权 2 | `dumpsys package com.sgmw.scene \| grep -E "android.car.permission"` | 同上 |
| M27 | 本应用授权态 | `dumpsys package com.aos.agent \| grep -E "android.car.permission"` | 记录 granted 列表原文 |

> M23 是唯一能把 API 数值和真实车况锚起来的测量。没有 M23，M20–M22 只能证明"值变不变"，不能证明"值对不对"。

### D 组 · 截图能力（只读）

| 编号 | 目的 | 命令 | 判据（测前固定） |
|---|---|---|---|
| M30 | 系统截图 · 桌面 | `input keyevent KEYCODE_HOME; sleep 2; exec-out screencap -p > M30.png` | 用像素统计判黑：非黑像素占比 > 5% = 可截；= 0% = 被 FLAG_SECURE 挡 |
| M31 | 系统截图 · 本应用 | 启动 App 后 `exec-out screencap -p > M31.png` | 同上 |
| M32 | 系统截图 · 车机原生页 | 打开车机设置页后同上 | 同上 |
| M33 | FLAG_SECURE 证据 | `dumpsys window windows \| grep -E "Window #\|mAttrs.*flags"` | 记录含 SECURE 标志的窗口名原文 |
| M34 | 应用内截图有效性 | App 内点截图按钮，`adb pull` 产物 | 非黑像素占比 > 5% 记为可用 |

> 像素统计脚本见 §5。

### E 组 · 广播送达（P1-4，只读）

| 编号 | 目的 | 命令 | 判据（测前固定） |
|---|---|---|---|
| M40 | debug 包广播 | `am broadcast --user 0 -n com.aos.agent.debug/.receiver.LlmConfigReceiver -a com.aos.agent.debug.llm.REFRESH` | 输出含 `data=` = 送达；无 data = 未送达 |
| M41 | 同上 + 未停止包 | 加 `--include-stopped-packages` 重发 | 与 M40 对比，定位是否 stopped 过滤 |
| M42 | 系统广播日志 | `logcat -b all -d \| grep -iE "BroadcastQueue\|Background execution"` | 记录原文 |
| M43 | 组件是否注册 | `dumpsys package com.aos.agent.debug \| grep -A3 LlmConfigReceiver` | 记录原文 |

### F 组 · 服务与脚本（只读）

| 编号 | 目的 | 命令 | 判据（测前固定） |
|---|---|---|---|
| M50 | 服务运行态 | `dumpsys activity services com.aos.agent` | 记录是否有 ServiceRecord |
| M51 | 脚本是否就位 | `ls -l /data/data/com.aos.agent/files/bin/` | 记录文件是否存在及时间戳 |
| M52 | 冷启耗时 | `logcat -c` → 启动 App → `logcat -d \| grep -E "Displayed\|Fully drawn"` | 记录 Displayed 毫秒数与 Skipped frames |

### G 组 · UI 取图（只读）

| 编号 | 目的 | 命令 | 判据 |
|---|---|---|---|
| M60–M65 | 六页各一张 | 用 D 组确认可用的截图方式，逐页取图 | 文件名 `M6x-<页名>.png`，附 `uiautomator dump` 文本树 |

## 3. 执行顺序

1. **M01–M05**（环境基线，5 分钟）
2. **M10–M17**（VHAL 身份，10 分钟）← 这一组直接回答"会不会是我们找错了 API"
3. **M30–M33**（截图判定，2 分钟）← 决定后面 G 组能不能做
4. **M40–M43**（广播，5 分钟）
5. **M50–M52**（服务，5 分钟）
6. **M20–M27 / M23**（车辆数据，需要真实驾驶，最耗时；M23 必须人工读仪表盘）
7. **M60–M65**（UI 取图，最后做）

## 4. 结论登记规则

测完只允许按这个格式写结论：

```
[M14] Supported Properties 共 N 条，其中 id ≥ 0x20000000 的 M 条。
原始输出：evidence/M14-supported-properties.txt 第 x–y 行。
```

禁止写法：

```
~~这台车的 VHAL 是模拟的~~
~~厂商没实现真实 VHAL~~
~~我们读错了属性~~
```

要表达怀疑，写成待测项，不写进结论。

## 5. 工具

### 5.1 截图黑图判定

```powershell
python -c "from PIL import Image; im=Image.open('M30.png').convert('L'); px=im.getdata(); n=len(px); nonblack=sum(1 for p in px if p>10); print(f'size={im.size} nonblack={nonblack/n:.4%}')"
```

判据：`nonblack > 5%` = 有画面；`= 0%` = 全黑。

### 5.2 取图命令（Windows 必须 exec-out）

```powershell
adb -s <IP>:5555 exec-out screencap -p > M30.png
```

### 5.3 属性时间序列对比

```powershell
# T0 / T1 / T2 各跑一次，文件名带时间戳
adb -s <IP>:5555 shell "dumpsys car_service | sed -n '/Vehicle Properties/,/^$/p'" > "evidence/M20-T0-$(Get-Date -Format yyyyMMdd-HHmmss).txt"
```

## 6. 证据目录

```
evidence/
├── M01-build-facts.txt
├── ...
├── M14-supported-properties.txt
├── M20-T0-静止.txt
├── M21-T1-行驶3km.txt
├── M23-仪表盘对照.txt        # 人工记录：仪表盘 SOC / API 读数
└── M30-desktop.png
```

目录放在仓库外或 `app/build/`（不入库）。

## 7. 本计划的验收

- [ ] A–F 组全部有原始输出文件
- [ ] 每条结论都能指到文件与行号
- [ ] M23 完成（否则车辆数值类结论一律记"未测"）
- [ ] 全文无"可能/应该/推测"
- [ ] 全部操作满足 R1–R6
