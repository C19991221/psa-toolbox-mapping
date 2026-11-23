# ota —— 在线更新与工具包分发目录

这个目录是**给 App 下载用的静态文件**，通过 GitHub raw 直链对外提供，不进构建。

## 地址怎么配

构建时把 `psa.otaBase` / `psa.infoBase` 指向本目录的 raw 地址：

```properties
# local.properties（已 gitignore，不进仓库）
sdk.dir=D:\\cusc_build_temp\\toolbox_sdk
psa.otaBase=https://raw.githubusercontent.com/C19991221/psa-toolbox-mapping/main/ota
psa.infoBase=https://raw.githubusercontent.com/C19991221/psa-toolbox-mapping/main/ota
```

`psa.otaBase` 和 `psa.infoBase` 都指向 `ota/` —— 因为代码里的路径本身就带子目录
（`/releases/psaboxlite/lasted.apk`、`/tools/hicar/hicar_fix.apk`、`/psatoolbox.html`），
所以 `otaBase + path` 正好拼成 `ota/releases/psaboxlite/lasted.apk` 这种仓库内路径。

## 目录结构（与代码里的路径一一对应）

```
ota/
├── psatoolbox.html                     # 系统设置页「公告栏」正文（纯文本，非 HTML 渲染）
├── releases/
│   └── psaboxlite/
│       ├── lasted_version              # 一行 versionName，如 工具箱开发版5.3.16 (build 28)（与本地字符串相等＝已最新）
│       ├── lasted.apk                  # 最新安装包本体
│       ├── lasted_md5                  # 一行 md5 小写十六进制，用于下载校验
│       ├── lasted_info                 # 最新一版的更新日志（纯文本，老逻辑回落用）
│       └── lasted_history              # 最近 10 个版本的更新日志，条目以「### 版本号」开头
└── tools/
    ├── hicar/
    │   ├── hicar_fix.apk               # HiCar 方控修复包（标致用）
    │   ├── hicar_md5
    │   ├── hicar_version
    │   └── citroen/
    │       ├── hicar_fix.apk           # 雪铁龙用
    │       └── hicar_md5
    ├── mediaplugin/
    │   ├── mediaplugin.apk             # 多媒体插件
    │   ├── mediaplugin_md5
    │   └── mediaplugin_version
    └── carlife/
        ├── carlife_fix.apk             # CarLife 方控修复包
        ├── carlife_md5
        └── carlife_version
```

## 校验规则（照 `VersionUtils` / `ApkDownloader` 的实际实现写）

| 文件 | 内容 | 判定 |
| --- | --- | --- |
| `*_version` | 一行版本号纯文本 | 与设备上该包的 `versionName` **字符串相等**＝已最新；**读不到（404/超时）一律当作有新版本** |
| `*_md5` | 一行 md5，小写十六进制即可（比较时忽略大小写） | 下载后校验；**空文件会直接抛「校验文件为空」** |
| `lasted_info` | 任意纯文本 | 最新一版的更新日志；`lasted_history` 读不到时回落用它 |
| `lasted_history` | 纯文本，条目以 `### 版本号` 开头，条目间空行 | 「检查更新」弹窗优先读它，显示最近 10 个版本的日志；发布脚本会按 `HISTORY_MAX=10` 自动截断 |

## 怎么发一版新的

用本目录里的 `publish_ota.py`（即仓库根的 `ota/publish_ota.py`）：

```powershell
python ota\publish_ota.py --apk app\build\outputs\apk\release\app-release.apk --info 更新日志.txt
```

版本号**不用传**：脚本自动读仓库根的 `version.properties`（`marketing` + `build`），拼成
`工具箱开发版<marketing> (build <build>)`，与 `app/build.gradle.kts` 用同一份数据。
它会写出 `lasted_version` / `lasted_md5` / `lasted.apk`（复制）/ `lasted_info` / `lasted_history`，
并自动在正文前加一行 `发布时间：YYYY-MM-DD HH:MM`。

⚠ 版本号必须与 `app/build.gradle.kts` 算出的 `versionName` **完全一致**（含空格与中文），
否则设备永远判「有新版本」并反复下载 —— 检查更新是字符串相等比较。
出包前先 `python bump_build.py` 递增 build 号，两边就自动一致了。

## 注意

- **raw 直链有 CDN 缓存**（约 5 分钟），刚发完立刻在车机上查可能还是旧内容。
- **单文件 ≤ 100 MB**、仓库建议 ≤ 1 GB。`lasted.apk` 约 2.6 MB，HiCar/CarLife 修复包通常几十 MB，都在范围内。
- `tools/` 下三个修复包**目前是空的**：手头没有这三个包本体，要等连上车机把已修好的
  `com.huawei.hicar.demoapp` / `com.baidu.carlifevehicle` / `com.cusc.media` 用 `adb pull` 拉下来才有得传。
  在补齐之前，这三个按钮会走到「下载失败」，这是预期行为，不是 bug。
