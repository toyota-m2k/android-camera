package io.github.toyota32k.lib.player.common

import android.content.Context
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class TpTempFile(val file: File) : AutoCloseable {
    constructor(context: Context, prefix:String, suffix:String):this(File.createTempFile(prefix, suffix, context.cacheDir))

    private var detached = AtomicBoolean(false)

    fun detach():File? {
        return if(!detached.getAndSet(true)) {
            file
        } else null
    }

    override fun close() {
        try {
            detach()?.delete()
        } catch(_:Throwable) {
            // ignore error
        }
    }
}