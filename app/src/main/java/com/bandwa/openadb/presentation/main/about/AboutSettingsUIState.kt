package com.bandwa.openadb.presentation.main.about

/**
 * 「检查更新」查到结果后先弹的提示框内容。
 *
 * 用户 2026-11-23 要求：查到新版本**不要直接下载**，先显示更新日志，
 * 让他自己选「保持当前版本」还是「立即更新」；已经是最新版本时也把日志显示出来。
 */
data class UpdatePrompt(
    /** 远端版本串（原样，含日期时间以外的全部信息）。 */
    val remoteVersion: String,
    /** 更新日志正文（含发布日期时间）。 */
    val info: String,
    /** true = 远端版本与本机不同（可以更新）。 */
    val hasNew: Boolean,
)

data class AboutSettingsUIState(
    val appVersionName: String = "未知版本",
    /** 本机信息卡正文（多行，见 `PsaExt.identityText`）。 */
    val identity: String = "",
    /** 检查更新下载进度，< 0 表示没有在下载。 */
    val downloadProgress: Float = -1f,
    val downloadProgressText: String = "",
    /**
     * 下载进度弹窗是否显示。
     *
     * 与 [downloadProgress] 解耦：用户点「后台下载」后进度继续走，
     * 只是弹窗关掉、进度改在「检查更新」按钮上显示。
     */
    val downloadDialogVisible: Boolean = false,
    /** 查到版本后先展示的「更新日志 + 是否更新」提示框；null = 不显示。 */
    val updatePrompt: UpdatePrompt? = null,
)
