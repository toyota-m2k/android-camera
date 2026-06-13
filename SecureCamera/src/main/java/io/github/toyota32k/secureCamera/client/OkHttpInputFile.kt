package io.github.toyota32k.secureCamera.client

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.media.lib.io.CloseableExtractor
import io.github.toyota32k.media.lib.io.CloseableMediaMetadataRetriever
import io.github.toyota32k.media.lib.io.HttpMediaDataSource
import io.github.toyota32k.media.lib.io.IHttpStreamSource
import io.github.toyota32k.media.lib.io.IInputMediaFile
import io.github.toyota32k.media.lib.legacy.converter.Converter
import io.github.toyota32k.utils.GenericCloseable
import okhttp3.Call
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.io.InputStream

/**
 * HTTP サーバー上のメディアファイルをソースとしてトランスコードを実行するため IInputMediaFile実装クラス
 */
class OkHttpInputFile(context: Context, private val streamSource: IHttpStreamSource) : IInputMediaFile {
    constructor(context: Context, url:String) : this(context, HttpStreamSource(url))

    companion object {
        /**
         * 一時ファイルの全クリア
         * 強制終了したりすると、一時ファイルがゴミとして残ることがあるので、ときどき呼ぶとよいかも。
         */
        @Suppress("unused")
        fun deleteAllTempFile(context:Context) {
            HttpMediaDataSource.deleteAllTempFile(context)
        }
        val logger:UtLog get() = NetClient.logger
    }


    private val context = context.applicationContext

    /**
     * java.net.HttpURLConnection を使った IHttpStreamSource の実装クラス
     */
    class HttpStreamSource(private val url:String): IHttpStreamSource {
        override var length: Long = -1
        private var response: Response? = null
        private var stream: InputStream? = null
        private var call: Call? = null

        override fun open(): InputStream {
            val request = Request.Builder()
                .get()
                .url(url)
                .build()
            return NetClient.motherClient.newCall(request).apply { call = this }.execute().run {
                response = this
                if (isSuccessful) {
                    length = body.contentLength()
                    body.byteStream().apply { stream = this }
                } else {
                    throw IOException("cannot retrieve data.")
                }
            }
        }

        override fun close() {
            logger.debug()
            try {
                stream?.close()
                stream = null
            } catch(e: Throwable) {
                logger.error(e)
            }
            try {
                response?.close()
                response = null
            } catch (e: Throwable) {
                logger.error(e)
            }
        }

        override fun cancel() {
            logger.debug()
            call?.cancel()
            call = null
        }
    }

    override val seekable: Boolean = true
    private var refCount  = 0
    private var dataSource: HttpMediaDataSource? = null
    private val logger = UtLog("HIF", Converter.logger)
    private var length: Long = -1

    /**
     * HttpMediaDataSource を準備する
     */
    private fun prepare(): HttpMediaDataSource {
        return synchronized(this) {
            if (dataSource == null) {
                dataSource = HttpMediaDataSource(context, streamSource)
                length = dataSource!!.getSize()
            }
            refCount++
            logger.debug("REF=$refCount")
            dataSource!!
        }
    }

    /**
     * 参照カウンタを１つ上げる
     */
    @Suppress("unused")
    fun addRef() {
        prepare()
    }

    /**
     * 参照カウンタを下げる
     */
    fun release() {
        synchronized(this) {
            if(refCount>0) {
                refCount--
                logger.debug("REF=$refCount")
                if (refCount == 0) {
//                    length = dataSource?.getSize() ?: -1
                    dataSource?.dispose()
                    dataSource = null
                }
            } else {
                logger.assert(false, "refCount is already zero")
            }
        }
    }

    override fun openExtractor(): CloseableExtractor {
        val src = prepare()
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(src)
            return CloseableExtractor(extractor, GenericCloseable { release() })
        } catch(e:Throwable) {
            logger.error(e)
            release()
            throw e
        }
    }

    override fun openMetadataRetriever(): CloseableMediaMetadataRetriever {
        val src = prepare()
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(src)
            return CloseableMediaMetadataRetriever(retriever, GenericCloseable {
                retriever.close()
                release()
            })
        } catch (e: Throwable) {
            logger.error(e)
            release()
            throw e
        }
    }

    /**
     * データサイズをベストエフォートで(w)取得
     */
    override fun getLength(): Long {
        return synchronized(this) {
            if(length>=0) {
                length
            } else {
                dataSource?.contentLength ?: -1L
            }
        }
    }

}