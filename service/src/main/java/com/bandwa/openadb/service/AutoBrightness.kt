package com.bandwa.openadb.service

import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager.CarPropertyEventListener
import android.content.Context
import android.provider.Settings
import android.util.Log
import com.bandwa.openadb.util.CarGateway
import com.bandwa.openadb.util.PsaExt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 倒挡 / 360 全景时把屏幕调到最亮，退出后恢复原亮度。
 *
 * 两个触发条件（任一成立即算「要看影像」）：
 *  - 档位 = R（`0x2140210F`，值 `2`，与维护诊断页的 `gearConvert()` 同一套编码）；
 *  - 360 全景正在显示（`0x2140eeb4` AVM_DISPLAY，值 `2`，与 misc 的 `getAvmState()` 同义）。
 *
 * 做法：进影像前把 `screen_brightness` / `screen_brightness_mode` 原值存下来，
 * 改成手动 + 最大值；退出时原样写回（连自动亮度模式一起还原）。
 *
 * 为什么还要 1.5 秒轮询：属性回调只在**变化**时来；用户先把档位挂进 R、再打开开关，
 * 或者注入/重启后 CarService 还没连上时错过回调，都需要一次兜底判读。
 * 轮询只在真的处于「该亮/该灭」的切换点才写设置，正常行驶时不做任何写入。
 */
object AutoBrightness {
    private const val TAG = "AutoBrightness"

    /** GEAR_SELECTION_NEW，与 `ToolsSettingsViewModel.GEAR` 一致。 */
    private const val PROP_GEAR = 0x2140210F

    /** AVM_DISPLAY（ID_AVM_DISPLAY），2 = 正在显示。 */
    private const val PROP_AVM_DISPLAY = 0x2140eeb4

    private const val GEAR_R = 2
    private const val AVM_SHOWING = 2

    private const val POLL_MS = 1500L
    private const val BRIGHT_MAX = 255
    private const val MODE_MANUAL = 0

    private var started = false

    @Volatile
    private var boosted = false

    private var savedBrightness = -1
    private var savedMode = -1

    fun start(context: Context, scope: CoroutineScope) {
        if (started) return
        started = true
        val ctx = context.applicationContext

        val listener = object : CarPropertyEventListener {
            override fun onChangeEvent(value: CarPropertyValue<*>?) {
                runCatching { evaluate(ctx) }
                    .onFailure { Log.d(TAG, "onChange evaluate failed: ${it.message}") }
            }

            override fun onErrorEvent(propId: Int, areaId: Int) {
                Log.d(TAG, "onErrorEvent $propId $areaId")
            }
        }

        scope.launch(Dispatchers.IO) {
            CarGateway.register(listener, PROP_GEAR, 0f)
            CarGateway.register(listener, PROP_AVM_DISPLAY, 0f)
        }

        scope.launch(Dispatchers.IO) {
            while (true) {
                runCatching { evaluate(ctx) }
                    .onFailure { Log.d(TAG, "poll evaluate failed: ${it.message}") }
                delay(POLL_MS)
            }
        }
    }

    private fun evaluate(ctx: Context) {
        val want = runCatching {
            PsaExt.autoBrightAvmOn(ctx) && (readGear() == GEAR_R || readAvm() == AVM_SHOWING)
        }.getOrDefault(false)

        if (want == boosted) return
        if (want) {
            boost(ctx)
        } else {
            restore(ctx)
        }
    }

    private fun readGear(): Int? = runCatching {
        CarGateway.manager.value?.getIntProperty(PROP_GEAR, 0)
    }.getOrNull()

    private fun readAvm(): Int? = runCatching {
        CarGateway.manager.value?.getIntProperty(PROP_AVM_DISPLAY, 0)
    }.getOrNull()

    private fun boost(ctx: Context) {
        val cr = ctx.contentResolver
        runCatching {
            savedBrightness = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS, BRIGHT_MAX)
            savedMode = Settings.System.getInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, MODE_MANUAL)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, MODE_MANUAL)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, BRIGHT_MAX)
            boosted = true
            Log.d(TAG, "boost to $BRIGHT_MAX (saved=$savedBrightness mode=$savedMode)")
        }.onFailure { Log.w(TAG, "boost failed: ${it.javaClass.simpleName} ${it.message}") }
    }

    private fun restore(ctx: Context) {
        val cr = ctx.contentResolver
        runCatching {
            if (savedBrightness >= 0) {
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, savedBrightness)
            }
            if (savedMode >= 0) {
                Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, savedMode)
            }
            Log.d(TAG, "restore to $savedBrightness mode=$savedMode")
            boosted = false
            savedBrightness = -1
            savedMode = -1
        }.onFailure { Log.w(TAG, "restore failed: ${it.javaClass.simpleName} ${it.message}") }
    }
}
