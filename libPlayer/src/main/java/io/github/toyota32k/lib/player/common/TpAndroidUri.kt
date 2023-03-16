package io.github.toyota32k.lib.player.common

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.net.Uri
import io.github.toyota32k.lib.player.TpLib
import java.io.File
import java.io.FileDescriptor

class TpAndroidUri(val uri:Uri):AutoCloseable {
    private var afd: AssetFileDescriptor? = null

    /**
     * @param mode mode – The string representation of the file mode. Can be "r", "w", "wt", "wa", "rw" or "rwt".
     */
    fun open(context: Context, mode:String="r"): FileDescriptor? {
        if(afd==null) {
            try {
                afd = context.contentResolver.openAssetFileDescriptor(uri, mode)
            } catch (e:Throwable) {
                TpLib.logger.stackTrace(e)
            }
        }
        return afd?.fileDescriptor
    }

    /**
     * ファイルの長さ
     */
    val length:Long
        get() {
            val afd = this.afd ?: throw IllegalStateException("file not opened.")
            return afd.length
        }

    fun detach(): FileDescriptor? {
        val r = afd?.fileDescriptor
        afd = null
        return r
    }

    override fun close() {
        afd?.close()
        afd = null
    }

    fun getAsTempFile(context: Context, prefix:String, suffix:String): TpTempFile? {
        return context.contentResolver.openInputStream(uri)?.use { input->
            TpTempFile(context, prefix, suffix).apply {
                file.outputStream().use {output->
                    input.copyTo(output)
                    output.flush()
                }
            }
        }
    }
}