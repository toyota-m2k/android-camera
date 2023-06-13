package io.github.toyota32k.secureCamera.client

import okhttp3.MediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.ForwardingSink
import okio.Sink
import okio.buffer

class ProgressRequestBody(private val delegate: RequestBody, val progressCallback:(current:Long, total:Long)->Unit) : RequestBody() {
    val totalLength:Long = delegate.contentLength()
    var sent:Long = 0L

    override fun contentType(): MediaType? {
        return delegate.contentType()
    }

    override fun contentLength(): Long {
        return totalLength
    }

    override fun writeTo(sink: BufferedSink) {
        val progressSink = ProgressSink(sink)
        val bufferedSink = progressSink.buffer()
        delegate.writeTo(bufferedSink)
        bufferedSink.flush()
    }

    inner class ProgressSink(delegate: Sink) : ForwardingSink(delegate) {
        override fun write(source: Buffer, byteCount: Long) {
            super.write(source, byteCount)
            sent += byteCount
            progressCallback.invoke(sent, totalLength)
        }
    }
}