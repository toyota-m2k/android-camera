package io.github.toyota32k.secureCamera.utils

import java.io.File

object FileUtil {
    fun safeDeleteFile(file:File) {
        try {
            if (file.exists()) {
                file.delete()
            }
        } catch (_:Throwable) {
        }
    }

    fun File.safeDelete() {
        safeDeleteFile(this)
    }

    fun deleteAllFilesInDir(dir: File) {
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { child ->
                deleteDir(child)
            }
        }
    }

    fun deleteDir(file: File) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { child ->
                deleteDir(child)
            }
        }
        try {
            file.delete()
        } catch(_:Throwable) {
        }
    }
}