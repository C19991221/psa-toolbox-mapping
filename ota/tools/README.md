# tools —— 三个「修复包」的分发目录

这里放的是**由 App 下载并安装的第三方/系统应用修复包**，不参与本仓库构建：

| 目录 | 目标包名 | 谁在用 |
| --- | --- | --- |
| `hicar/hicar_fix.apk` | `com.huawei.hicar.demoapp` | 音乐助手 → 修复Hicar方控（标致） |
| `hicar/citroen/hicar_fix.apk` | `com.huawei.hicar.demoapp` | 音乐助手 → 修复Hicar方控（雪铁龙） |
| `mediaplugin/mediaplugin.apk` | `com.cusc.media` | 音乐助手 → 安装多媒体插件 |
| `carlife/carlife_fix.apk` | `com.baidu.carlifevehicle` | 音乐助手 → 修复Carlife方控 |

## 现在是空的（预期行为）

这三个包的本体目前**没有**，所以 `music.hicar.*` / `mediaplugin.*` / `carlife.*` 这四条
下载链路即使地址配好了也会下载失败。补齐办法只有一个：连上车机，把**已经修好的**那三个应用拉下来。

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
