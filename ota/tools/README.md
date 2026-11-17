# tools —— 三个「修复包」的分发目录

这里放的是**由 App 下载并安装的第三方/系统应用修复包**，不参与本仓库构建：

| 目录 | 目标包名 | 谁在用 |
| --- | --- | --- |
| `hicar/hicar_fix.apk` | `com.huawei.hicar.demoapp` | 音乐助手 → 修复Hicar方控（标致） |
| `hicar/citroen/hicar_fix.apk` | `com.huawei.hicar.demoapp` | 音乐助手 → 修复Hicar方控（雪铁龙） |
| `mediaplugin/mediaplugin.apk` | `com.cusc.media` | 音乐助手 → 安装多媒体插件 |
| `carlife/carlife_fix.apk` | `com.baidu.carlifevehicle` | 音乐助手 → 修复Carlife方控 |

## 当前状态

| 目录 | 状态 |
| --- | --- |
| `hicar/` | ✅ 已补：`hicar_fix.apk` 8,405,924 B / versionName `11.5 fix2` / md5 `287d99d50f581792d292a9060a118ee4` |
| `carlife/` | ✅ 已补：`carlife_fix.apk` 7,295,261 B / versionName `4.4.4 fix4` / md5 `50a7f977f16c645ddef448c2173d4def`（含「修改 Carlife 分辨率」入口 `com.baidu.carlifevehicle/.SettingsActivity`） |
| `hicar/citroen/` | ⬜ 空：车上没有雪铁龙的修复包，界面入口已隐藏 |
| `mediaplugin/` | ⬜ 空：暂时没有可提取的修复版，界面入口已隐藏 |

两个已补的包是 2026-11-16 从实车（408 / `3VS121`）`adb pull` 出来的**已修复版**，不是我们做的。
仍需补齐（或恢复界面入口）的是雪铁龙 HiCar 与多媒体插件。

```powershell
adb connect <车机IP>:5555
adb shell pm path com.huawei.hicar.demoapp      # 得到 /data/app/.../base.apk
adb pull /data/app/.../base.apk D:\cusc_build_temp\hicar_fix.apk
adb shell pm path com.baidu.carlifevehicle
adb pull /data/app/.../base.apk D:\cusc_build_temp\carlife_fix.apk
adb shell pm path com.cusc.media
adb pull /data/app/.../base.apk D:\cusc_build_temp\mediaplugin.apk
```

拉到之后按上表放进对应目录，再补三个 `*_md5` 与 `*_version`：

- `*_md5`：`Get-FileHash -Algorithm MD5` 出来的小写十六进制，一行。
- `*_version`：**必须写这个包自己的 `versionName`**（不是本 App 的版本），
  因为 `VersionUtils.isRemoteVersionDiff(context, versionUrl, "com.huawei.hicar.demoapp")`
  比的是**目标包**的 versionName。写错了会导致「每次都提示有新版本」或「永远提示已修复」。

取 versionName：

```powershell
adb shell dumpsys package com.huawei.hicar.demoapp | Select-String versionName
```
