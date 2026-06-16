package io.github.toyota32k.secureCamera.client.auth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.lang.AutoCloseable
import kotlin.time.Duration.Companion.minutes

class AuthKeeper: AutoCloseable {
    private class Keeper(var authHost:AuthHost? = Authentication.currentHost) {
        private suspend fun ensure(): AuthHost? {
            val host = authHost
            if (host == null) {
                authHost = Authentication.autoAuth()
            } else {
                if (host.authenticate() == AuthHost.AuthResult.OFFLINE) {
                    if (Authentication.authenticate().message()) {
                        authHost = Authentication.currentHost
                    } else {
                        authHost = null
                    }
                }
            }
            return authHost
        }

        private var paused = false
        var job: Job? = null
        fun keepAlive() {
            if (job != null) return
            job = CoroutineScope(Dispatchers.IO).launch {
                while (currentCoroutineContext().isActive) {
                    if (!paused) {
                        ensure()
                    }
                    delay(1.minutes)
                }
            }
        }

        fun pause() {
            paused = true
        }
        fun resume() {
            paused = false
        }

        fun close() {
            job?.cancel()
            job = null
        }
    }

    private var keeper: Keeper? = null
    val authHost:AuthHost? = keeper?.authHost
    fun start() {
        synchronized(this) {
            if (keeper != null) return
            keeper = Keeper().apply { keepAlive() }
        }
    }
    fun pause() {
        synchronized(this) {
            keeper?.pause()
        }
    }
    fun resume() {
        synchronized(this) {
            keeper?.resume()
        }
    }
    override fun close() {
        synchronized(this) {
        keeper?.close()
        keeper = null
        }
    }
}