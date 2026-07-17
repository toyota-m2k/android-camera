package io.github.toyota32k.secureCamera.utils

import android.content.Context
import io.github.toyota32k.secureCamera.EditorActivity.EditorViewModel.Companion.logger
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

    fun safeOverwrite(context: Context, srcFile: File, dstFile:File):Boolean {
        if (!dstFile.exists()) {
            // 出力ファイルが存在しなければ、単なるリネームでok
            return try {
                srcFile.renameTo(dstFile)
                true
            } catch(e: Throwable) {
                logger.error(e, "cannot overwrite by rename")
                false
            }
        }
        // dstをバックアップ
        val bakFile = File(context.cacheDir ?: throw java.lang.IllegalStateException("no cacheDir"), "tc-backup").apply { safeDeleteFile(this) }
        try {
            dstFile.renameTo(bakFile)
        } catch (e:Throwable) {
            logger.error(e, "cannot backup")
            return false
        }
        safeDeleteFile(dstFile) // すでに存在しないはずだが念のため

        // src を dstにリネーム
        try {
            srcFile.renameTo(dstFile)
            return true
        } catch(e:Throwable) {
            logger.error(e, "cannot rename")
            try {
                // リネームに失敗したらコピーも試す
                srcFile.copyTo(dstFile)
                return true
            } catch (e:Throwable) {
                // リネームもコピーも失敗したら、backup ファイルから復元する。
                logger.error(e, "cannot copy")
                bakFile.renameTo(dstFile)
                return false
            }
        } finally {
            safeDeleteFile(bakFile)
        }
    }

}