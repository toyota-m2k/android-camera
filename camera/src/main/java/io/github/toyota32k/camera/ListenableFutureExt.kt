package io.github.toyota32k.camera

import android.content.Context
import androidx.core.content.ContextCompat
import com.google.common.util.concurrent.ListenableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

suspend fun <T> ListenableFuture<T>.await(context: Context) : T {
    return suspendCoroutine { cont ->
        addListener({
            cont.resume(get())
        }, ContextCompat.getMainExecutor(context.applicationContext))
    }
}
