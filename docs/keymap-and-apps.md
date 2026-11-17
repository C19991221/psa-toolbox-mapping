# 按键映射与应用管理

> 本文说明「按键映射」「应用管理」两个页面的能力，以及按键映射的注入载荷并入「修复PSA工具箱」修复链路的实现方式。涉及路径均相对仓库根目录；模块划分与包结构见 [架构说明](architecture.md)。

## 概述

工具箱的 11 个 Tab 中，「按键映射」「应用管理」面向车机日常操作：

- **按键映射**：把车机物理键的单击 / 长按映射为启动指定应用；
- **应用管理**：查看、清理、卸载车机应用，并通过扫码无线安装 APK。

页面只负责采集配置（写入系统属性或偏好），实际按键分发由工具箱的 Hook 与 `com.cusc.misc` 注入载荷完成。注入载荷不单独分发，随「修复PSA工具箱」（`ModApk`）流程一起打进原厂核心件，一次「修复核心组件」即可同时更新音乐 / 地图补丁与按键映射补丁。

## 按键映射页

`app/src/main/java/com/bandwa/openadb/presentation/main/keymap/`

页面列出 13 个键位，采用两列卡片布局（车机屏幕较宽，单列会浪费横向空间）：

| 键位 | 说明 |
| --- | --- |
| 灯杆顶端 | 仅短按；长按保留原厂「唤醒语音助手」 |
| 蓝牙电话 / 方向盘语音 / SRC 切源 | 单击；前两者可配长按 |
| 空调钢琴键 / 车辆设置钢琴键 / 蓝牙电话钢琴键 / 手机互联钢琴键 | 单击与长按 |
| 音乐键 / 地图键 | 单击与长按；短按由工具箱 Hook 的 `launchByProp` 接管，长按由注入层接管 |
| LIST | 原厂语义是开关 360 全景，只有开 / 关，没有可变目标应用 |
| 会员中心 / 车机设置 | 仅部分车型有该按键，仅单击 |

每个键位点「单击」或「长按」打开两栏选应用对话框（`AppPickerTwoPane` / `AppPickDialog`）：左侧为当前选择与预设，右侧按「系统应用 / 第三方应用」规则过滤可启动应用，选「无」恢复原厂行为。

配置写入系统属性（`persist.bandwa.*.shortclick` / `.longclick`），由 Hook 或注入层读取后启动目标应用。页面会检测注入层版本与安装状态，未生效时给出提示；未注入时长按等行为仍走原厂。

## 应用管理页

`app/src/main/java/com/bandwa/openadb/presentation/main/apps/`

- **应用列表**：列出车机自带关键应用与已装第三方应用，显示图标、名称与版本，支持按名称 / 按时间排序与手动刷新。
- **点行展开操作**：清缓存、清数据、卸载；对可退回到车机自带版本的应用显示「卸载更新」。操作带确认弹窗与进度提示，按钮平时收起，点该行才展开。
- **扫码无线安装 APK**：页面生成二维码，手机扫码后通过 `PicHttpServerHost` 的局域网 HTTP 服务上传 APK，页面显示上传 / 安装进度并等待安装回执。
- **安装应用市场**：沙发管家安装入口，走 `service/src/main/java/com/bandwa/openadb/service/InstallShafa.kt` 的下载安装链路。

## 注入载荷并入「修复PSA工具箱」链路

载荷位于 `app/src/main/assets/modapk/com.cusc.misc/`，由 `ModApk` 在「修复核心组件」时从原厂 APK 选择性 baksmali 后注入。

载荷包含 8 个 smali 片段：

| 文件 | 作用 |
| --- | --- |
| `psa_handle.smali` | `psaHandle(KeyEventEmitter, int keycode, boolean isLong)`：11 个键码的属性解析与启动分发 |
| `psa_launch.smali` | `psaLaunch(Context, String)`：解析 `包名/Activity` 并启动，失败回落原厂 |
| `psa_stopavm.smali` | `CarPropertyManager.stopAVMDisplay()`：原厂没有，用来关 360 |
| `psa_boot.smali` | `psaBoot(Context)`：开机 startService 拉起工具箱、清日志、按开关打开 WiFi |
| `psa_onkeyclick.smali` / `psa_onkeylongclick.smali` | 分别插进 `onKeyClick` / `onKeyLongClick` 开头，调用 `psaHandle(..., false/true)`，接管则提前 return |
| `psa_d4g_off.smali` | 插进 `Data4gImpl.showNetState()` 开头执行 `return-void`，屏蔽「车机未连接到服务器」弹窗 |
| `psa_boot_ins.smali` | 插进 `BootReceiver.onReceive` 开头，调用 `psaBoot` |

`config.json` 共 11 条修改条目、19 条 operation；其中按键映射相关的 8 条 operation 为 KeyEventEmitter 4 条、CarPropertyManager 1 条、Data4gImpl 1 条、BootReceiver 2 条，其余为音乐 / 地图等既有补丁配置。

音乐键 / 地图键的长按统一到注入层：`PSAToolBoxService` 中经由 MCU 键信息属性（`PROP_MCU_KEY_INFO = 0x2141F04B`）收到 `"11"` / `"13"` 后延迟 2 秒读取目标包并启动的旧实现已删除，改由 `psa_handle.smali` 的 `0x0b` / `0x0d` 分支在长按时读取 `.longclick` 属性后启动，避免与 Hook 重复启动两次。

注入是幂等的：`config.json` 的插入操作带 `skipIfContains` 标记，重复执行「修复核心组件」时会跳过已注入的片段。

## 支撑改动

- **`SmaliMod`**（`core/src/main/java/com/bandwa/openadb/util/apktools/SmaliMod.kt`）：提供 `insertBeforeMethod`、`insertAfterRegisters`、`appendToClass` 三种文本插入方法。原有 `DexInjector` 只能表达 `INVOKE_STATIC` / `IGET` / `SGET` / `CONST_STRING`，无法表达「早退分支」。
- **`ModApk`**（`service/src/main/java/com/bandwa/openadb/service/install/ModApk.kt`）：`applyModifications` 支持 `insertBeforeMethod` / `insertAfterRegisters` / `appendToClass` 三种新 type 与通用 `skipIfContains`。
- **`BootReceiver`**（`service/src/main/java/com/bandwa/openadb/receiver/BootReceiver.kt`）：支持「不进界面（后台唤醒）」分支（`BootWake.wakeAll`），开机拉起应用时可选不弹界面（默认关）；内存 / 存储自动清理在 `user.sh` 执行后立即进行，不随 10 秒延时等待。
- **界面**：Tab 加分组（`TabItem.group`，组间画分隔线、去掉逐项 alpha 动画、高度 44dp、去掉写死的 240dp 宽度）；四级字号（28 / 21 / 18 / 14）；深浅双主题；`Glassmorphism` 把整页大面积阴影拆成独立静态层，修掉滚动掉帧。
- **Tab 顺序**（`MainActivity`）：系统设置 / 个性设置 / 音乐助手 / 导航助手 / 按键映射 / 仪表投屏 ‖ 油耗账本 ‖ 应用管理 / 维护诊断 / 专家模式 ‖ 关于软件。
- **关于软件页**：展示贡献者头像、版权行与致谢（GPL-3.0 §5(b)），提供修改说明（§5(a)）与 GPL-3.0 许可证要点入口（§5(d)）。赞赏码属原作者个人收款用途，本发行版已移除。
- **hook**：`Hook.VERSION` 由 `"4"` 升到 `"5"`，注入版本不一致时提示重装核心件。
- **core 工具**：`MemGuard`（内存清理）、`StorageClean`（存储清理）、`CrashCollect`（崩溃 / 诊断包）、`SysInfo`（系统与车况信息）、`AppManage`（应用列表与卸载类型）、`AppPick`、`PsaExt`、`BootWake`。
- **UI 组件**：`PageTitle`、`SectionTitle`、`AppPickDialog`、`AppPickerTwoPane`、`KeyPalette`，以及 `ic_apps` / `ic_key_map` 图标。
- **维护诊断页**：拆出 `DiagSections`（运行状态 / 内存清理 / 存储清理 / 崩溃诊断 / 工程模式五段）；关于页的声明文本集中在 `LegalNotices`。
- **测试**：`service/src/test/java/com/bandwa/openadb/util/apktools/PsaKeyMapPackTest.kt` 用真实原包完整打一遍补丁，校验 `config.json` 的修改条目全部落地、目标方法存在，且 `Data4gImpl.showNetState` 首指令为 `return-void`；未提供 `-Dpsa.apk` / `-Dpsa.out` 时整组用例跳过。

## 在线更新配置

检查更新、公告栏、HiCar / CarLife / 多媒体插件下载都通过 `psa.otaBase` / `psa.infoBase` 两个构建期属性指向外部静态托管，代码中没有硬编码地址：

```properties
psa.otaBase=https://raw.githubusercontent.com/band-wa/psa-toolbox/main/ota
psa.infoBase=https://raw.githubusercontent.com/band-wa/psa-toolbox/main/ota
```

- 配置方式：`-Ppsa.otaBase=…` / `local.properties` / 环境变量 `PSA_OTA_BASE`、`PSA_INFO_BASE`（优先级见 [README](../README.md)「部署配置」）。
- 代码里的路径自带子目录（`/releases/psaboxlite/lasted.apk`、`/tools/hicar/hicar_fix.apk`、`/psatoolbox.html`），`otaBase + path` 直接拼成完整地址，无需改代码。
- 不配置时构建仍成功：检查更新提示「未配置远程下载服务」，公告显示「暂无新公告」，沙发管家提示「未配置沙发管家下载地址」。
- 版本文件格式：`*_version` 一行版本号（与目标包 `versionName` 完全相等才算最新）、`*_md5` 一行小写 hex。

## 未实现与限制

- **实体空调键（风量 / 温度 / AC）没有统一到注入载荷**：它们走 MCU 属性 `0x2141F04B` 通道，`com.cusc.misc` 侧不注册该属性，注入层收不到事件；要统一需让 misc 注册该属性或改走 HVAC 属性直写，工作量大且未经实车验证，目前仍由工具箱 `PSAToolBoxService` 处理。
- **`tools/` 下三个修复包（HiCar / CarLife / 多媒体插件）本体为空**：这两条链路需要「已经修好」的原厂改版包，需在车机上 `adb pull` 对应包后自行托管，并把 `*_version` 写成目标包自己的 `versionName`。
- **沙发管家下载地址未配置**（`psa.shafaEndpoint`）：应用管理页保留入口与链路代码，未配置时点击提示「未配置沙发管家下载地址」，不会发起下载。
