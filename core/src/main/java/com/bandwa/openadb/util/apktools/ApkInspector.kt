package com.bandwa.openadb.util.apktools

import java.io.File
import java.util.zip.ZipFile

object ApkInspector {
    private val HOOK_MARKER = "Lcom/bandwa/hook/Hook;".toByteArray(Charsets.US_ASCII)

    fun containsHookClasses(apkPath: String?): Boolean = containsDexString(apkPath, "Lcom/bandwa/hook/Hook;")

    /**
     * APK 的 dex 里是否有某个 ASCII 串。
     *
     * 用它做「某个具体补丁在不在」的判据：补丁插进去的方法名会进 dex 的字符串表，
     * 比 `HookState` 那套内存态更可靠 —— 内存态要等宿主回调才会被填上。
     */
    fun containsDexString(apkPath: String?, marker: String): Boolean {
        if (apkPath == null || marker.isEmpty()) return false
        val apk = File(apkPath)
        if (!apk.isFile) return false
        val bytes = marker.toByteArray(Charsets.US_ASCII)
        return try {
            ZipFile(apk).use { zip ->
                zip.entries().asSequence()
                    .filter { it.name.startsWith("classes") && it.name.endsWith(".dex") }
                    .any { entry -> zip.getInputStream(entry).use { stream -> streamContains(stream, bytes) } }
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun streamContains(stream: java.io.InputStream, marker: ByteArray): Boolean {
        val chunk = ByteArray(64 * 1024 + marker.size)
        var carry = 0
        while (true) {
            val read = stream.read(chunk, carry, chunk.size - carry)
            if (read <= 0) return false
            val length = carry + read
            var i = 0
            while (i <= length - marker.size) {
                var matched = true
                for (j in marker.indices) {
                    if (chunk[i + j] != marker[j]) {
                        matched = false
                        break
                    }
                }
                if (matched) return true
                i++
            }
            carry = minOf(marker.size - 1, length)
            System.arraycopy(chunk, length - carry, chunk, 0, carry)
        }
    }
}
