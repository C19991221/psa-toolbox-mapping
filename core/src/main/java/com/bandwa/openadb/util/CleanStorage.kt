package com.bandwa.openadb.util

import android.annotation.SuppressLint
import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

class CleanStorage {
    companion object {
        @SuppressLint("SdCardPath")
        fun cleanStorage(context: Context) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val sdcardDir = File("/sdcard/")
                    if (!sdcardDir.exists() || !sdcardDir.isDirectory) return@launch

                    // 永久保留的 /sdcard 目录（2026-11-22 用户要求：这个按钮不许再删用户文件）。
                    // 除了下面这些，/sdcard 根下其余目录都会被整目录删掉，所以"看起来像缓存"的
                    // 也要谨慎——宁可少清，不可误删。
                    val hardcodedPreservedDirs = setOf(
                        // 车机自带、删了会出问题
                        "amapauto9",      // 高德离线地图
                        "launcherPic",    // 桌面图片
                        "mtklog",         // 厂商日志
                        // 用户文件 / 我们自己的东西
                        "Download",       // 浏览器与诊断包下载
                        "Documents",
                        "Music",
                        "Pictures",
                        "DCIM",
                        "Movies",
                        "PSA",            // 我们的崩溃日志与诊断包
                        "psa_iptheme",    // 仪表小屏导出的日志
                        "netease",        // 网易云音乐缓存
                    )

                    val hardcodedDirsToClean = setOf(
                        "/data/aee_exp",
                    )

                    val sharedPrefs = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE)
                    val userWhitelist = sharedPrefs.getStringSet("cacheWhitelist", emptySet()) ?: emptySet()

                    sdcardDir.listFiles { file ->
                        !hardcodedPreservedDirs.contains(file.name) && file.name != "Android"
                    }?.forEach { fileToDelete ->
                        deleteFile(fileToDelete)
                    }

                    val androidDir = File(sdcardDir, "Android")
                    if (androidDir.exists() && androidDir.isDirectory) {
                        val dataDir = File(androidDir, "data")
                        if (dataDir.exists() && dataDir.isDirectory) {
                            dataDir.listFiles { file ->
                                !userWhitelist.contains(file.name)
                            }?.forEach { appDir ->
                                deleteFile(appDir)
                            }
                        }

                        val obbDir = File(androidDir, "obb")
                        if (obbDir.exists() && obbDir.isDirectory) {
                            obbDir.listFiles { file ->
                                !userWhitelist.contains(file.name)
                            }?.forEach { appDir ->
                                deleteFile(appDir)
                            }
                        }
                    }

                    hardcodedDirsToClean.forEach { dirPath ->
                        val dirToClean = File(dirPath)
                        if (dirToClean.exists() && dirToClean.isDirectory) {
                            deleteDirectoryContents(dirToClean)
                        }
                    }
                } catch (e: Exception) {
                }
            }
        }

        private fun deleteFile(file: File) {
            try {
                if (file.exists()) {
                    if (file.isDirectory) {
                        file.walkBottomUp().forEach { it.delete() }
                    } else {
                        file.delete()
                    }
                }
            } catch (e: Exception) {
            }
        }

        private fun deleteDirectoryContents(directory: File) {
            try {
                if (directory.exists() && directory.isDirectory) {
                    directory.listFiles()?.forEach { file ->
                        if (file.isDirectory) {
                            file.walkBottomUp().forEach { it.delete() }
                        } else {
                            file.delete()
                        }
                    }
                }
            } catch (e: Exception) {
            }
        }
    }
}
