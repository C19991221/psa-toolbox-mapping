package com.bandwa.openadb

import android.app.Application
import com.bandwa.openadb.util.CleanDefaults
import com.bandwa.openadb.util.CompatDefaults
import com.bandwa.openadb.util.CrashCollect
import com.bandwa.openadb.util.Utils.PREFS_NAME
import com.bandwa.openadb.util.apk.ApkDownloader
import com.umeng.commonsdk.UMConfigure
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@HiltAndroidApp
class PSAHelperApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        CoroutineScope(Dispatchers.IO).launch {
            ApkDownloader.cleanupDownloads(this@PSAHelperApplication)
        }

        // 清理类开关的一次性默认值迁移：首次安装 / 下次在线更新都跑一遍，全部置「关」。
        runCatching { CleanDefaults.applyIfNeeded(this) }

        // 兼容模式的一次性默认值迁移：开发版统一默认打开（用户 m06708）。
        runCatching { CompatDefaults.applyIfNeeded(this) }

        // 未捕获异常只落盘，随后原样交回上一个处理器，不改变崩溃行为
        runCatching { CrashCollect.installHandler(this) }

        CoroutineScope(Dispatchers.IO).launch {
            runCatching { CrashCollect.autoCollect(this@PSAHelperApplication) }
        }

        val sharedPreferences = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val termsAgreed = !sharedPreferences.getBoolean("first_launch", true)

        // 友盟 key 由构建注入（见 README「部署配置」），未配置时跳过失常上报。
        val umengKey = BuildConfig.UMENG_APP_KEY
        if (termsAgreed && umengKey.isNotEmpty()) {
            UMConfigure.preInit(this, umengKey, "Umeng")
            UMConfigure.init(this, umengKey, "Umeng", UMConfigure.DEVICE_TYPE_BOX, "")
        }
    }
}
