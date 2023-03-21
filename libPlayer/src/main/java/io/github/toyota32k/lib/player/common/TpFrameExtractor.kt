package io.github.toyota32k.lib.player.common

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Size
import io.github.toyota32k.lib.player.TpLib
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.*
import kotlin.math.min
import kotlin.math.roundToInt

class TpFrameExtractor(context: Context, uri: Uri) : AutoCloseable {
    // private val analizer = MediaMetadataRetriever().apply { setDataSource(context, uri) }
    private val analyzer = TpAndroidUri(uri).use { MediaMetadataRetriever().apply { setDataSource(it.open(context)) }}

    data class BasicProperties(
        var creationDate: Date?,
        var duration: Long,
        val size: Size
    )

    val size: Size
        get() {
            var height = analyzer.getLong(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT).toInt()
            var width = analyzer.getLong(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH).toInt()
            val rotate = analyzer.getLong(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
            if (rotate == 90L || rotate == 270L) {
                val v = height
                height = width
                width = v
            }
            return Size(width, height)
        }

    val properties: BasicProperties
        get() {
            return BasicProperties(
                analyzer.getDate(MediaMetadataRetriever.METADATA_KEY_DATE),
                analyzer.getLong(MediaMetadataRetriever.METADATA_KEY_DURATION),
                size
            )
        }

    fun thumbnailSize(fitter: UtFitter): Size {
        return fitter.fit(size).resultSize
    }

    fun calcHD720Size(width: Int, height: Int): Size {
        var r = if (width > height) { // 横長
            min(1280f / width, 720f / height)
        } else { // 縦長
            min(720f / width, 1280f / height)
        }
        if (r > 1) { // 拡大はしない
            r = 1f
        }
        return Size((width * r).roundToInt(), (height * r).roundToInt())
    }

    val hd720Fitter: UtFitter by lazy {
        UtFitter(FitMode.Fit, calcHD720Size(size.width, size.height))
    }

    suspend fun extractFrame(pos: Long, fitter: UtFitter? = null): Bitmap? {
        return withContext(Dispatchers.IO) {
            val pos2 = if (pos >= 0 && pos < properties.duration) pos else min(
                2000L,
                properties.duration / 100
            )
            try {
                if(fitter!=null) {
                    val size = thumbnailSize(fitter)
                    analyzer.getBitmapAt(pos2, exactFrame = true, size.width, size.height)
                } else {
                    analyzer.getBitmapAt(pos, exactFrame = true)
                }
            } catch (e: Throwable) {
                TpLib.logger.stackTrace(e)
                null
            }
        }
    }

    override fun close() {
        analyzer.close()
    }
}