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

    /**
     * 一次性检查多个串，返回**没找到**的那些。
     *
     * 注入前预检要用它回答「这台车机的组件版本能不能改」：要改的每个类都必须在
     * 原包的 dex 里找得到。逐条调 [containsDexString] 会把整个 zip 反复解压 N 遍
     * （`com.cusc.misc` 有 30 MB、十几个 dex），批量版只扫一遍。
     */
    fun missingDexStrings(apkPath: String?, markers: List<String>): List<String> {
        if (apkPath == null) return markers
        val apk = File(apkPath)
        if (!apk.isFile) return markers
        if (markers.isEmpty()) return emptyList()
        val bytes = markers.associateWith { it.toByteArray(Charsets.US_ASCII) }
        val found = HashSet<String>()
        try {
            ZipFile(apk).use { zip ->
                zip.entries().asSequence()
                    .filter { it.name.startsWith("classes") && it.name.endsWith(".dex") }
                    .forEach { entry ->
                        if (found.size == markers.size) return@forEach
                        val pending = bytes.filterKeys { it !in found }
                        if (pending.isEmpty()) return@forEach
                        zip.getInputStream(entry).use { stream ->
                            // 每个 dex 只读一遍，边扫边把命中的挑出去。
                            streamContainsAny(stream, pending).forEach { found.add(it) }
                        }
                    }
            }
        } catch (e: Exception) {
            return markers
        }
        return markers.filter { it !in found }
    }

    /** 扫一个流，返回 [markers] 里出现过的那些（值 → 字节）。 */
    private fun streamContainsAny(
        stream: java.io.InputStream,
        markers: Map<String, ByteArray>,
    ): List<String> {
        val maxLen = markers.values.maxOf { it.size }
        // 按首字节分桶：绝大多数位置的首字节都对不上，一次比较就能跳过，
        // 否则 30 MB × N 个串 × 每个串几十字节的比较量会明显卡住。
        val remaining = HashMap<Int, MutableList<Pair<String, ByteArray>>>()
        for ((name, marker) in markers) {
            remaining.getOrPut(marker[0].toInt() and 0xFF) { ArrayList() }.add(name to marker)
        }
        val chunk = ByteArray(64 * 1024 + maxLen)
        var carry = 0
        val hit = ArrayList<String>()
        var left = markers.size
        while (true) {
            val read = stream.read(chunk, carry, chunk.size - carry)
            if (read <= 0) return hit
            val length = read + carry
            var i = 0
            while (i < length) {
                val bucket = remaining[chunk[i].toInt() and 0xFF]
                if (bucket != null) {
                    val it = bucket.iterator()
                    while (it.hasNext()) {
                        val (name, marker) = it.next()
                        if (i + marker.size > length) continue
                        var matched = true
                        for (j in 1 until marker.size) {
                            if (chunk[i + j] != marker[j]) {
                                matched = false
                                break
                            }
                        }
                        if (matched) {
                            hit.add(name)
                            it.remove()
                            left--
                        }
                    }
                    if (bucket.isEmpty()) remaining.remove(chunk[i].toInt() and 0xFF)
                    if (left == 0) return hit
                }
                i++
            }
            carry = minOf(maxLen - 1, length)
            System.arraycopy(chunk, length - carry, chunk, 0, carry)
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
