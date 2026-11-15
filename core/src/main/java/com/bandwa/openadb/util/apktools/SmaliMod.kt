package com.bandwa.openadb.util.apktools

import java.io.File

class SmaliMod {
    companion object {
        private fun readNormalized(file: File): String =
            file.readText().replace("\r\n", "\n")

        fun replaceValue(file: File, oldValue: String, newValue: String): Boolean {
            if (!file.exists() || !file.isFile) {
                return false
            }

            return try {
                val content = readNormalized(file)
                if (!content.contains(oldValue)) {
                    return false
                }
                val modifiedContent = content.replace(oldValue, newValue)
                file.writeText(modifiedContent)
                true
            } catch (e: Exception) {
                false
            }
        }

        fun replaceValueIfExists(file: File, checkValue: String, oldValue: String, newValue: String): Boolean {
            if (!file.exists() || !file.isFile) {
                return false
            }

            return try {
                val content = readNormalized(file)
                if (content.contains(checkValue)) {
                    val modifiedContent = content.replace(oldValue, newValue)
                    file.writeText(modifiedContent)
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                false
            }
        }

        fun checkValueExist(file: File, value: String): Boolean {
            if (!file.exists() || !file.isFile) {
                return false
            }

            return try {
                val content = readNormalized(file)
                content.contains(value)
            } catch (e: Exception) {
                false
            }
        }

        fun replaceBetween(file: File, startMarker: String, endMarker: String, newContent: String): Boolean {
            if (!file.exists() || !file.isFile) {
                return false
            }

            return try {
                val content = readNormalized(file)
                val startIdx = content.indexOf(startMarker)
                if (startIdx == -1) return false

                val endIdx = content.indexOf(endMarker, startIdx + startMarker.length)
                if (endIdx == -1) return false

                val modifiedContent = content.replaceRange(startIdx + startMarker.length, endIdx, newContent)

                file.writeText(modifiedContent)
                true
            } catch (e: Exception) {
                false
            }
        }

        fun replaceRegex(file: File, pattern: String, newValue: String): Boolean {
            if (!file.exists() || !file.isFile) {
                return false
            }

            return try {
                val content = readNormalized(file)
                val regex = Regex(pattern)
                if (!regex.containsMatchIn(content)) {
                    return false
                }
                file.writeText(regex.replace(content, newValue))
                true
            } catch (e: Exception) {
                false
            }
        }

        fun replaceFile(file: File, newContent: String): Boolean {
            if (file.exists() && !file.isFile) {
                return false
            }

            return try {
                file.writeText(newContent)
                true
            } catch (e: Exception) {
                false
            }
        }
    }
}
