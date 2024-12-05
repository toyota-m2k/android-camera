package io.github.toyota32k.secureCamera.utils

import android.media.MediaMetadataRetriever
import android.os.ParcelFileDescriptor
import io.github.toyota32k.media.lib.converter.AndroidFile
import io.github.toyota32k.media.lib.format.getDuration
import io.github.toyota32k.secureCamera.db.MetaDB
import kotlinx.coroutines.delay
import java.io.File
import java.io.FileDescriptor

object VideoUtil {
    private const val WAIT_DURATION = 1000L

    private fun rawOpenFileDescriptor(file:File):ParcelFileDescriptor? {
        return try {
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode("r"))
        } catch (e: Throwable) {
            null
        }
    }

    private suspend fun openFileDescriptor(file: File, retry:Int): ParcelFileDescriptor? {
        var i = 0
        while(true) {
            val fd = rawOpenFileDescriptor(file)
            if(fd!=null) {
                MetaDB.logger.debug("fd retrieved after ${i + 1} trial")
                return fd
            }
            if(i>=retry) {
                return null
            }
            delay(WAIT_DURATION)
            i++
        }
    }

    private fun rawGetDuration(fd:FileDescriptor):Long? {
        return try {
            MediaMetadataRetriever().run {
                try {
                    setDataSource(fd)
                    extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong()
                } finally {
                    release()
                }
            }
        } catch(e:Throwable) {
            MetaDB.logger.error(e)
            null
        }
    }

    /**
     * カメラで撮影した直後は（まだファイルの書き込みが終わっていないため？）ParcelFileDescriptor.open()や、
     * MediaMetadataRetrieverの操作に失敗したり、extractMetadata()が null を返したりするので、
     * １秒間隔でリトライする。
     * @param retry リトライ回数（＝待ち合わせる秒数）, 0 ならリトライしない
     */
    suspend fun getDurationOriginal(file:File, retry:Int):Long {
        var i = 0
        var retryFd = retry
        while(true) {
            val p = openFileDescriptor(file, retryFd) ?: return 0L
            p.use { pfd->
                val d = rawGetDuration(pfd.fileDescriptor)
                if(d!=null) {
                    return d.toLong()
                }
            }
            if(i>=retry) {
                return 0L
            }
            delay(WAIT_DURATION)
            i++
            retryFd = 0
        }
    }

    suspend fun getDuration(file:File, retry:Int):Long {
        var i = 0
        while(true) {
            val d = try {
                AndroidFile(file).openMetadataRetriever().use {
                    it.obj.getDuration()
                }
            } catch (e:Throwable) {
                MetaDB.logger.error(e)
                null
            }
            if(d!=null) {
                return d.toLong()
            }
            if(i>=retry) {
                return 0L
            }
            delay(WAIT_DURATION)
            i++
        }
    }
}