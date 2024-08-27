package io.github.toyota32k.secureCamera.client

import android.content.Context
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import androidx.annotation.WorkerThread
import io.github.toyota32k.media.lib.converter.CloseableExtractor
import io.github.toyota32k.media.lib.converter.CloseableMediaMetadataRetriever
import io.github.toyota32k.media.lib.converter.IHttpStreamSource
import io.github.toyota32k.media.lib.converter.IInputMediaFile
import io.github.toyota32k.secureCamera.client.NetClient.executeAsync
import io.github.toyota32k.utils.FlowableEvent
import io.github.toyota32k.utils.UtLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.internal.headersContentLength
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

class OkHttpStreamSource(val url: String) : IHttpStreamSource {
    companion object {
        val motherClient : OkHttpClient =
            OkHttpClient.Builder()
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
    }

    override var length: Long = -1L
    private var call: Call? = null

    override fun close() {
        call?.cancel()
        call = null
    }

    override fun open(): InputStream {
        val request = Request.Builder()
            .url(url)
            .build()
        call = motherClient.newCall(request)
        val response = call!!.execute()
        length = response.headersContentLength()
        return response.body!!.byteStream()
    }
}

/**
 * android-media-processor の Converterに、直接 httpのURLを渡すことができるが、どういうわけか、トリミング（シーク）を要求すると、
 * Extractorが正しく読み込めなくなる。いろいろ試行錯誤したがうまくいかないので、自前でhttpからデータを読み込んでキャッシュする仕掛けを用意してみた。
 * 作戦
 * - read要求とは独立して、一時ファイル(CacheDir) にデータをダウンロードする。
 * - read要求が来たときに、
 *      position + size までダウンロード済みなら、そのデータを返す。
 *      まだダウンロードされていなければ、ダウンロードされるまで待機 (suspend)
 * - Extractor#setDataSource() を呼んだだけで、closeが呼ばれるので、close()は無視する。
 * - MediaMetadataRetriever, MediaExtractor で共用したいので、CloseableTに、IInputMediaFile#close()は渡さない
 * - その代わり、利用する側(EditorActivity）で close() する。
 *
 * この実装を android-media-processor に入れたいのはやまやまだが、okhttp 依存になっているので躊躇中。
 */
private class HttpMediaDataSource(context: Context, val url: String) : MediaDataSource() {
    val tempFile = File.createTempFile("tmp_", ".tmp", context.cacheDir)
    var canceller: Canceller? = null
    val logger = UtLog("HMD", NetClient.logger)
    var totalLength: Long = -1
    var currentLength: Long = -1
    var error: Throwable? = null
    val completed = FlowableEvent()

    data class WaitingEvent(val requiredLength:Long) {
        private val event = FlowableEvent()
        suspend fun notify(length: Long) {
            if (length >= requiredLength) {
                event.set()
            }
        }
        suspend fun error() {
            event.set()
        }
        suspend fun wait() {
            event.waitOne()
        }
    }
    val waitingEvents = mutableListOf<WaitingEvent>()

    init {
        startLoading()
    }

    private suspend fun onError(e:Throwable) {
        val events = synchronized(this) {
            error = e
            waitingEvents.toList()
        }
        events.forEach {
            it.error()
        }
    }
    private suspend fun onReceived(length: Long) {
        val total:Long
        val events = synchronized(this) {
            currentLength += length
            total = currentLength
            waitingEvents.toList()
        }
//        logger.debug("onReceived $length ($total")
        events.forEach {
            it.notify(total)
        }
    }

    private fun startLoading() {
        canceller = Canceller()
        val request = Request.Builder()
            .url(url)
            .build()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                tempFile.outputStream().use { output ->
                    val response = executeAsync(request, canceller)
                    if (!response.isSuccessful) {
                        throw Exception("response is not successful")
                    }
                    totalLength = response.headersContentLength()
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    response.body?.use { body ->
                        body.byteStream().use { input ->
                            var bytes = input.read(buffer)
                            while(bytes>0) {
                                output.write(buffer, 0, bytes)
                                bytes = input.read(buffer)
                                onReceived(bytes.toLong())
                            }
                        }
                    } ?: throw Exception("response body is null")
                }
            } catch (e: Throwable) {
                logger.error(e)
                onError(e)
            } finally {
                canceller = null
                completed.set()
            }
        }
    }

    private fun checkError() {
        synchronized(this) {
            if (error != null) {
                throw error!!
            }
        }
    }

    private fun waitFor(position:Long, size: Long) : Long {
        val total = getSize()
        val length = if (total < position+size) {
            total
        } else {
            position+size
        }
        val awaiter = synchronized(this) {
            checkError()
            if (currentLength >= length) {
                return length - position
            } else {
                WaitingEvent(length).apply {
                    waitingEvents.add(this)
                }
            }
        }
        runBlocking { awaiter.wait() }
        checkError()
        return length - position
    }

    override fun close() {
        logger.debug()
    }

    fun dispose() {
        canceller?.cancel()
        runBlocking { completed.waitOne() }
        try {
            tempFile.delete()
        } catch (e: Throwable) {
            logger.error(e)
        }
    }

    @WorkerThread
    override fun readAt(position: Long, buffer: ByteArray?, offset: Int, size: Int): Int {
        logger.debug("readAt $position $size")
        val length = waitFor(position, size.toLong())
        checkError()
        return tempFile.inputStream().use { input ->
                input.skip(position)
                input.read(buffer, offset, length.toInt())
            }
    }

    @WorkerThread
    override fun getSize(): Long {
        val awaiter = synchronized(this) {
            checkError()
            if(totalLength > 0) {
                return totalLength
            } else {
                WaitingEvent(0).apply {
                    waitingEvents.add(this)
                }
            }
        }
        runBlocking { awaiter.wait() }
        checkError()
        return totalLength
    }
}

class OkHttpInputFile(context:Context, url: String) : IInputMediaFile, Closeable {
    override val seekable: Boolean = true
    private val dataSource = HttpMediaDataSource(context, url)

    override fun getLength(): Long {
        return dataSource.getSize()
    }

    override fun openExtractor(): CloseableExtractor {
        val extractor = MediaExtractor().apply {
            setDataSource(dataSource)
        }
        return CloseableExtractor(extractor, null)
    }

    override fun openMetadataRetriever(): CloseableMediaMetadataRetriever {
        val retriever = MediaMetadataRetriever().apply {
            setDataSource(dataSource)
        }
        return CloseableMediaMetadataRetriever(retriever, null)
    }

    override fun close() {
        dataSource.dispose()
    }
}