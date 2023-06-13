package io.github.toyota32k.secureCamera.client

import okhttp3.Call

class Canceller {
    private var cancelled:Boolean = false
    private var call:Call? = null
    fun setCall(call: Call) {
        synchronized(this) {
            if(cancelled) {
                call.cancel()
            } else {
                this.call = call
            }
        }
    }
    fun cancel() {
        synchronized(this) {
            cancelled = true
            call?.cancel()
            call = null
        }
    }
}