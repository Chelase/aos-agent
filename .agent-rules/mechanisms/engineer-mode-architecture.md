# 工程师模式

## 结论

工程师模式是一个隐藏的调试诊断面板，提供三大能力：
1. **OEM/系统检测** — 识别车机厂商、AAOS 定制版本、硬件平台
2. **Car API 全覆盖扫描** — 枚举测试所有 VehicleProperty 的可用性
3. **真车自检报告** — 生成完整的系统兼容性报告，用于适配调优

> 分批实施说明：
> - **基础功能批次**：只实现只读系统信息、服务状态、基础权限状态、自检报告最小版。
> - **核心功能批次**：再接入 Car API 扫描、系统服务检查、自检报告增强。
> - 因此，“工程师模式”是一个完整能力集合，但允许按批次逐步落地，不要求第一批就实现三大能力全量版本。

## 涉及对象

| 层 | 文件/模块 | 角色 |
|---|---|---|
| UI | EngineerModeActivity.kt | 工程师模式入口 |
| UI | EngineerViewModel.kt | 诊断数据管理与刷新 |
| UI | CarApiScanner.kt | 扫描所有 Car API 可用性 |
| UI | SystemServiceInspector.kt | 系统服务状态查询 |
| UI | SensorDashboard.kt | 传感器实时数据 |
| UI | LogViewer.kt | 日志查看器 |
| UI | SelfCheckReport.kt | 自检报告生成与导出 |
| System | Car API 封装 | 统一 try-catch 调用所有 API |
| System | OEM Detector | 识别厂商/车型/系统版本 |
| System | Build.* / SystemProperties | 系统版本硬件信息 |

## 运行链路

### 入口

[点击 Logo 5 次] -> [密码验证] -> [工程师模式主面板]

### 主面板

[工程师模式主面板]
  |
  +---> [Car API 扫描] ---- 遍历所有 VehiclePropertyIds
  |       逐个 getProperty() try-catch
  |       成功 -> 显示实时值
  |       失败 -> 显示 "无权限 / 不支持"
  |
  +---> [OEM 系统检测] ---- 读取厂商信息
  |       Build.MANUFACTURER / BRAND / DEVICE / FINGERPRINT
  |       SystemProperties 读取 AAOS 定制属性
  |       显示: 车机厂商 | 车型 | AAOS 版本 | API Level
  |
  +---> [系统信息] ---- Android 版本 / 硬件 / 存储 / 内存
  |
  +---> [传感器] ---- GPS / 加速度 / 陀螺仪
  |
  +---> [日志] ---- Agent 运行日志
  |
  +---> [自检报告] ---- 一键生成完整报告
          导出为 JSON/文本，记录所有 API 可用状态

### 五菱星光一键开启

针对五菱星光 2025 款提供多种快速进入方式：

| 方式 | 操作 |
|------|------|
| 快捷设置磁贴 | 下拉通知栏 - 点击工程师模式磁贴 |
| 桌面快捷方式 | 添加工师模式快捷图标 |
| 语音唤醒 | 说进入工程师模式 |
| 方向盘快捷键 | 长按静音键(需系统权限,Phase 3) |
| ADB 命令 | adb shell am start 进入 |

### OEM 检测数据源

| 数据 | API / 来源 | 示例值（五菱） |
|---|---|---|
| 制造商 | Build.MANUFACTURER | SAIC-GM-Wuling |
| 品牌 | Build.BRAND | wuling |
| 车型设备名 | Build.DEVICE | 可能为 astra_ev 等 |
| 硬件平台 | Build.HARDWARE | qcom / mtxxxx |
| Android 版本 | Build.VERSION.RELEASE | 14 / 15 |
| API Level | Build.VERSION.SDK_INT | 34 |
| 构建指纹 | Build.FINGERPRINT | wuling/... |
| AAOS 版本 | SystemProperties.get("ro.car.version") | 可能为空 |
| 基带版本 | Build.getRadioVersion() | ... |
| 内核版本 | SystemProperties.get("ro.kernel.version") | ... |

### Car API 检测

对所有已知 VehiclePropertyIds 尝试 getProperty()：

```
[P0 必测列表]
  PERF_VEHICLE_SPEED       -> 车速
  GEAR_SELECTION           -> 档位
  BATTERY_LEVEL            -> 电池电量
  BATTERY_CHARGING_STATUS  -> 充电状态
  HVAC_TEMPERATURE_SET     -> 空调温度
  HVAC_FAN_SPEED_SETTING   -> 风量
  ...

[扩展列表 - 因车型而异]
  ODOMETER                 -> 总里程
  FUEL_LEVEL               -> 油位（混动）
  TIRE_PRESSURE            -> 胎压
  VEHICLE_MAP_URL          -> 导航地图
  ENVIRONMENT_OUTSIDE_TEMP -> 车外温度
  ...
```

### 自检报告输出格式

```json
{
  "reportTime": "2026-06-28T10:00:00Z",
  "vehicle": {
    "manufacturer": "SAIC-GM-Wuling",
    "brand": "wuling",
    "model": "星光 2025",
    "androidVersion": "14",
    "apiLevel": 34
  },
  "carApiScan": {
    "total": 127,
    "available": 45,
    "unavailable": 82,
    "details": [
      { "property": "PERF_VEHICLE_SPEED", "available": true, "value": "0 km/h" },
      { "property": "ODOMETER", "available": false, "error": "not supported" }
    ]
  },
  "permissions": {
    "granted": ["CAR_SPEED", "CAR_INFO"],
    "denied": ["CAR_ENERGY", "CAR_POWER"]
  },
  "recommendations": [
    "CAR_ENERGY 未授权，续航里程功能不可用",
    "ODOMETER 不支持，跳过该功能"
  ]
}
```

## 使用点

- **真车自检**：开发者在真实车机上运行，了解系统能力
- **多厂商适配**：不同车型导出报告，比对差异，调整代码
- **功能验证**：确认某个 API 在具体车型上是否可用
- **问题排查**：权限缺失 / API 不支持 / 系统版本兼容

## 修改点

| 要做什么 | 改哪里 | 怎么改 |
|---|---|---|
| OEM 检测 | ui/engineer/OemDetector.kt | 封装 Build.* + SystemProperties 读取 |
| Car API 扫描 | ui/engineer/CarApiScanner.kt | 遍历 VehiclePropertyIds try-catch |
| 自检报告 | ui/engineer/SelfCheckReport.kt | 聚合扫描结果输出 JSON |
| 导出功能 | ui/engineer/ | 支持文本/JSON 导出到存储 |

## 关键约束

1. Car API 枚举必须防抖（500ms 间隔），避免瞬时高负载。
2. 无权限的 Property 静默跳过，不弹窗。
3. 自检报告不包含个人隐私数据（位置、车辆 VIN 等）。
4. Release 编译必须移除工程师模式入口。
5. OEM 检测仅用于适配判断，不做绕过厂商限制之用。

## 维护方式

- **新增 API 扫描项**：在 VehiclePropertyIds 列表中追加。
- **新增 OEM 检测项**：在 OemDetector 追加 SystemProperties key。
- **验证**：在目标车机上运行自检，确认报告完整。

> 更新时间：2026-06-28
>
> TODO: 待用户提供五菱星光 2025 具体 AAOS 版本后补充适配参数。已知制造商可能为 SAIC-GM-Wuling 或 SGMW，品牌 wuling。

