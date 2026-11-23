package com.bandwa.openadb.util

import java.util.concurrent.ConcurrentHashMap

/**
 * 宿主（被注入的 `com.cusc.misc` / 桌面）回报的 Hook 版本，纯内存态。
 *
 * [reportCount] 是「回报次数」，不是版本号：注入装完之后要判断**新进程**是否真的
 * 起来了，光看 [versionOf] 会被上一次运行留下的旧值骗过去（同版本重装时旧值一样）。
 * 所以装前记一次计数，装后要求计数变大，才算「新进程活过来了」。
 */
object HookState {
    private val versions = ConcurrentHashMap<String, String>()
    private val counts = ConcurrentHashMap<String, Long>()

    fun report(packageName: String, version: String) {
        versions[packageName] = version
        counts.merge(packageName, 1L, Long::plus)
    }

    fun forget(packageName: String) {
        versions.remove(packageName)
        counts.remove(packageName)
    }

    fun versionOf(packageName: String): String? = versions[packageName]

    fun reportCount(packageName: String): Long = counts[packageName] ?: 0L
}
