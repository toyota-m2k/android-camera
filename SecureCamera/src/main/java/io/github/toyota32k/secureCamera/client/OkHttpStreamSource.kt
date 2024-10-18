package io.github.toyota32k.secureCamera.client

import io.github.toyota32k.media.lib.converter.IHttpStreamSource
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.internal.headersContentLength
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
    private var stream: InputStream? = null

    override fun close() {
        synchronized(this) {
            try {
                stream?.close()
                stream = null
            } catch (_: Throwable) {}
            try {
                call?.cancel()
                call = null
            } catch (_: Throwable) {}
        }
    }

    override fun open(): InputStream {
        val request = Request.Builder()
            .url(url)
            .build()
        call = motherClient.newCall(request)
        val response = call!!.execute()
        length = response.headersContentLength()
        return response.body!!.byteStream().apply { stream = this }
    }
}
