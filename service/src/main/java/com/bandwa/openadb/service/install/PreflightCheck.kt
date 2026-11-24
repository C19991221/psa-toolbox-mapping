package com.bandwa.openadb.service.install

import android.content.Context
import android.os.StatFs
import android.util.Log
import com.bandwa.openadb.service.BuildConfig
import com.bandwa.openadb.util.Utils
import com.bandwa.openadb.util.apktools.ApkInspector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * 注入（修复）**开工前**的只读检查。
 *
 * 为什么要有它：原来点「修复」直接就开始打包、卸载、安装 —— 空间不够、载荷缺文件、
 * 车机上的组件版本和补丁对不上，都要等到几十秒之后才炸，而且炸出来的是一句
 * `补丁包缺少 Hook 类` 之类的技术错误。用户（2026-11-23 m05704）要求「注入前预检报告」，
 * 并且**必须让不熟悉技术的人看得懂**，所以这里的每一项都只回答两个问题：
 * 「有没有问题」+「我该怎么办」，不出现 smali / dex / 锚点这类词。
 *
 * 检查项（顺序＝用户关心的顺序）：
 * 1. 车机存储空间够不够（打包要临时占掉约 3 份原包大小）。
 * 2. 车机自带的核心组件在不在（`/system` 里那份，我们的补丁是从它改出来的）。
 * 3. 本软件里带的修复文件全不全（`hook/hook.dex`、`config.json`、各 smali 片段）。
 * 4. 车机上那份组件的版本能不能改（要改的类在不在它的 dex 里）。
 * 5. 现在是什么状态（原厂 / 已修复），纯告知，不影响能否继续。
 *
 * 全部为 [Status.PASS]/[Status.WARN] 才允许开工；任一 [Status.FAIL] 就拦住，
 * 因为继续下去的结局一定是失败，只是要多等一分钟并让用户看到一句看不懂的报错。
 */
object PreflightCheck {

    private const val TAG = "PreflightCheck"

    /** 除原包副本之外的富余空间要求。 */
    private const val EXTRA_ROOM_BYTES = 64L * 1024 * 1024

    private const val CORE = CoreComponent.CORE_PACKAGE

    enum class Status { PASS, WARN, FAIL }

    /** 一条检查结果。[title] 是一句人话，[detail] 是补充说明或建议。 */
    data class Item(val title: String, val detail: String, val status: Status)

    data class Report(val items: List<Item>) {
        val canProceed: Boolean get() = items.none { it.status == Status.FAIL }
        val headline: String get() = if (canProceed) "检查通过，可以开始修复" else "暂时不能修复"

        /**
         * 给对话框用的纯文本。
         *
         * 用 ✓ / ！ / × 三个符号做前缀，比「通过 / 警告 / 失败」更省地方，
         * 也不需要用户理解任何技术名词。
         */
        fun asText(): String = buildString {
            for (item in items) {
                append(
                    when (item.status) {
                        Status.PASS -> "✓ "
                        Status.WARN -> "！ "
                        Status.FAIL -> "× "
                    }
                )
                append(item.title)
                append('\n')
                if (item.detail.isNotBlank()) {
                    append("    ")
                    append(item.detail)
                    append('\n')
                }
                append('\n')
            }
            if (canProceed) {
                append("修复过程大约几分钟，期间请勿操作车机。")
            } else {
                append("上面打 × 的项目需要先解决，否则修复一定会失败。")
            }
        }
    }

    suspend fun run(context: Context): Report = withContext(Dispatchers.IO) {
        val items = ArrayList<Item>(6)
        val stockPath = ModApk.stockApkPathOf(CORE)
        val stockFile = stockPath?.let { File(it) }
        val stockExists = stockFile?.isFile == true

        items += storageCheck(context, stockFile?.length() ?: 0L)
        items += stockCheck(stockPath, stockExists)
        items += payloadCheck(context)
        items += versionCheck(context, stockPath, stockExists)
        items += currentStateCheck(context)
        items += launcherCheck(context)
        Report(items).also { log(it) }
    }

    // ── 1. 存储空间 ──────────────────────────────────────────────────────

    private fun storageCheck(context: Context, stockSize: Long): Item {
        val dir = runCatching { Utils.workspaceDir(context) }.getOrNull() ?: context.dataDir
        val free = runCatching {
            StatFs(dir.absolutePath).let { it.blockSizeLong * it.availableBlocksLong }
        }.getOrDefault(-1L)
        if (free < 0) {
            return Item(
                title = "查不到车机剩余空间",
                detail = "不影响修复，继续时如果空间不够会提示你。",
                status = Status.WARN,
            )
        }
        // 打包过程同时存在：原包副本、改过的未签名包、签名后的包 ⇒ 约 3 份。
        val need = (if (stockSize > 0) stockSize * 3 else 200L * 1024 * 1024) + EXTRA_ROOM_BYTES
        return when {
            free < need -> Item(
                title = "车机存储空间不够",
                detail = "还剩 ${size(free)}，修复大约需要 ${size(need)}。" +
                    "建议先到「维护诊断 → 存储清理」清一点空间再试。",
                status = Status.FAIL,
            )
            free < need * 2 -> Item(
                title = "车机存储空间偏紧",
                detail = "还剩 ${size(free)}，修复大约需要 ${size(need)}，够用但不宽裕。",
                status = Status.WARN,
            )
            else -> Item(
                title = "车机存储空间够用",
                detail = "还剩 ${size(free)}，修复大约需要 ${size(need)}。",
                status = Status.PASS,
            )
        }
    }

    // ── 2. 车机自带的核心组件 ────────────────────────────────────────────

    private fun stockCheck(stockPath: String?, exists: Boolean): Item = when {
        stockPath == null -> Item(
            title = "系统里没有找到这个组件",
            detail = "本软件不认识这台车机的核心组件，无法修复。",
            status = Status.FAIL,
        )
        !exists -> Item(
            title = "车机自带的核心组件不见了",
            detail = "它是这台车机出厂自带的一个系统应用，正常情况下删不掉。" +
                "可能被清理软件删了，也可能是这台车机的系统版本不一样。",
            status = Status.FAIL,
        )
        else -> Item(
            title = "找到了车机自带的核心组件",
            detail = "修复时会以它为基础改出一份新的，原件不动。",
            status = Status.PASS,
        )
    }

    // ── 3. 本软件自带的修复文件 ──────────────────────────────────────────

    private fun payloadCheck(context: Context): Item {
        val missing = ArrayList<String>()
        // hook.dex 是注入进宿主的那段桥接代码，缺了它装上去必崩（审计 H2）。
        val hookOk = runCatching {
            context.assets.open("hook/hook.dex").use { it.read() > 0 }
        }.getOrDefault(false)
        if (!hookOk) missing.add("hook.dex")

        val configText = runCatching {
            context.assets.open("modapk/$CORE/config.json").use { it.reader().readText() }
        }.getOrNull()
        if (configText.isNullOrBlank()) {
            missing.add("config.json")
        } else {
            val files = runCatching {
                val root = JSONObject(configText)
                val mods = root.getJSONArray("modifications")
                buildList {
                    for (i in 0 until mods.length()) {
                        val ops = mods.getJSONObject(i).getJSONArray("operations")
                        for (j in 0 until ops.length()) {
                            ops.getJSONObject(j).optString("contentFile")
                                .takeIf { it.isNotBlank() }
                                ?.let { add(it) }
                        }
                    }
                }.distinct()
            }.getOrDefault(emptyList())
            if (files.isEmpty()) missing.add("补丁内容")
            for (name in files) {
                val ok = runCatching {
                    context.assets.open("modapk/$CORE/$name").use { it.read() > 0 }
                }.getOrDefault(false)
                if (!ok) missing.add(name)
            }
        }

        return if (missing.isEmpty()) {
            Item(
                title = "修复需要的文件都在",
                detail = "本软件自带的修复文件完整。",
                status = Status.PASS,
            )
        } else {
            Item(
                title = "修复需要的文件缺失",
                detail = "缺少 ${missing.size} 个文件，本软件的安装包可能不完整。" +
                    "请卸载后重新安装本软件再试。",
                status = Status.FAIL,
            )
        }
    }

    // ── 4. 车机上那份组件的版本能不能改 ─────────────────────────────────

    private fun versionCheck(context: Context, stockPath: String?, stockExists: Boolean): Item {
        if (!stockExists || stockPath == null) {
            // 上一项已经报错了，这里不重复打击用户。
            return Item(
                title = "没法确认车机组件版本",
                detail = "等上面那条解决了再看这里。",
                status = Status.WARN,
            )
        }
        val types = runCatching {
            val text = context.assets.open("modapk/$CORE/config.json")
                .use { it.reader().readText() }
            ModApk.targetTypes(ModApk.parseConfig(text, BuildConfig.DEBUG))
        }.getOrDefault(emptyList())
        if (types.isEmpty()) {
            return Item(
                title = "没法确认车机组件版本",
                detail = "本软件里的修复清单读不出来，请重新安装本软件。",
                status = Status.WARN,
            )
        }
        val missing = runCatching { ApkInspector.missingDexStrings(stockPath, types) }
            .getOrDefault(types)
        return if (missing.isEmpty()) {
            Item(
                title = "车机组件版本可以修复",
                detail = "需要改动的 ${types.size} 个位置在车机上都能找到。",
                status = Status.PASS,
            )
        } else {
            Item(
                title = "车机组件版本和本软件不匹配",
                detail = "有 ${missing.size} 个位置在车机上找不到，说明这台车机的系统版本" +
                    "和本软件对不上，这种情况不建议修复。",
                status = Status.FAIL,
            )
        }
    }

    // ── 5. 当前状态（仅告知） ───────────────────────────────────────────

    private fun currentStateCheck(context: Context): Item {
        val patched = runCatching { ModApk.isPatchedInstall(context, CORE) }.getOrDefault(false)
        return if (patched) {
            Item(
                title = "这台车机现在是修复过的版本",
                detail = "再修复一次会把现在的替换掉，功能不变。",
                status = Status.PASS,
            )
        } else {
            Item(
                title = "这台车机现在是原厂版本",
                detail = "修复会装上一份改过的组件，按键映射等功能才会生效。",
                status = Status.PASS,
            )
        }
    }

    // ── 6. 桌面（只影响桌面自定义，不影响修复本身） ─────────────────────

    /**
     * 桌面那一层是**可选**的：写 × 会让人以为修不了，所以最差也只给「！」，
     * 由界面上的「桌面要不要一起修复」那一问去决定做不做。
     */
    private suspend fun launcherCheck(context: Context): Item {
        val info = runCatching { LauncherSupport.detect(context, force = true) }.getOrNull()
            ?: return Item(
                title = "没查到桌面状态",
                detail = "不影响修复。桌面自定义（换壁纸、调卡片透明度）可能用不了。",
                status = Status.WARN,
            )
        return when (info.state) {
            LauncherSupport.State.STOCK -> Item(
                title = "桌面是原厂版本",
                detail = "修复时可以选择连桌面一起修，换壁纸和卡片透明度才能用；" +
                    "只修公共服务的话这两项暂时用不了。",
                status = Status.WARN,
            )
            LauncherSupport.State.OURS_CURRENT -> Item(
                title = "桌面已经是最新版本",
                detail = "换壁纸、调卡片透明度都可以直接用。",
                status = Status.PASS,
            )
            LauncherSupport.State.OURS_STALE -> Item(
                title = "桌面是旧版本",
                detail = "修复时会顺便把桌面更新到当前版本。",
                status = Status.PASS,
            )
            LauncherSupport.State.FOREIGN_PATCHED -> Item(
                title = "桌面是你自己装的改版",
                detail = "已经带换壁纸和调卡片透明度的能力，修复不会覆盖它。",
                status = Status.PASS,
            )
        }
    }

    private fun size(bytes: Long): String {
        if (bytes <= 0) return "0"
        val mb = bytes / (1024.0 * 1024.0)
        return if (mb >= 1024) {
            String.format(Locale.getDefault(), "%.1f GB", mb / 1024.0)
        } else {
            String.format(Locale.getDefault(), "%.0f MB", mb)
        }
    }

    private fun log(report: Report) {
        report.items.forEach { Log.i(TAG, "${it.status} ${it.title} :: ${it.detail}") }
    }
}
