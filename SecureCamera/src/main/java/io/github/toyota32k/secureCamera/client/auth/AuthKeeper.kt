package io.github.toyota32k.secureCamera.client.auth

import android.app.Activity
import androidx.annotation.MainThread
import io.github.toyota32k.utils.GenericCloseable
import io.github.toyota32k.utils.IDisposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.Closeable
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration.Companion.minutes

object AuthKeeper {
    private val globalPauseCount = AtomicLong(0L)

    private class Keeper() {
        var authHost:AuthHost? = Authentication.currentHost

        companion object {
            suspend fun tryAuth(host: AuthHost?): AuthHost? {
                return if (host == null) {
                    Authentication.authAndMessage()
                } else if (host.authenticate() == AuthHost.AuthResult.OFFLINE) {
                    Authentication.authAndMessage()
                } else {
                    host
                }
            }
        }
        suspend fun tryAuth(): AuthHost? {
            return tryAuth(authHost).apply {
                authHost = this
                lastAuth = System.currentTimeMillis()
            }
        }

        private var paused = false
        private var lastAuth:Long = 0L
        private val interval = 1.minutes
        var job: Job? = null
        suspend fun start():AuthHost? {
            paused = false
            val host = tryAuth() ?: return null
            if (job == null) {
                job = CoroutineScope(Dispatchers.IO).launch {
                    while (currentCoroutineContext().isActive) {
                        if (!paused && globalPauseCount.get() <= 0L && System.currentTimeMillis()-lastAuth>interval.inWholeMilliseconds) {
                            if (tryAuth() == null) {
                                break // 監視終了
                            }
                        }
                        delay(interval)
                    }
                }
            }
            return host
        }

        fun touch(host:AuthHost?) {
            if (host!=null && authHost===host) {
                lastAuth = System.currentTimeMillis()
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
    private var ownerRef:WeakReference<Any>? = null
    private var owner
        get() = ownerRef?.get()
        set(v) { ownerRef = if (v!=null) WeakReference(v) else null }
    val authHost:AuthHost? = keeper?.authHost
    val isActive:Boolean get() = authHost!=null

    /**
     * 指定されたホストに対して Keeper による監視を開始する。
     */
    suspend fun start():AuthHost? {
        val currentKeeper = synchronized(this) { keeper ?: Keeper().apply { keeper=this }}
        return currentKeeper.start()
    }

    /**
     * 監視は開始しないで認証だけ実行する
     */
    suspend fun tryAuth():AuthHost? {
        val currentKeeper = synchronized(this) { keeper ?: Keeper().apply { keeper=this }}
        return currentKeeper.tryAuth()
    }

    /**
     * Activity#onCreate --> attach
     * オーナーを入れ替えて close() するだけ。
     * 実際にhttp通信を行うときに、start()する必要がある。
     * @param owner アタッチするオーナー
     */
    fun attach(owner: Activity) {
        synchronized(this) {
            if (this.owner !== owner) {
                this.owner = owner
                close()
            }
        }
    }

    /**
     * Activity#onDestroy --> detach
     * @param owner デタッチするオーナー
     */
    fun detach(owner:Activity) {
        synchronized(this) {
            if (this.owner === owner) {
                this.owner = null
                close()
            }
        }
    }

    /**
     * 監視を一時停止
     * @param owner オーナー（カレントオーナー以外からの要求は無視）
     */
    fun pause(owner:Activity) {
        synchronized(this) {
            if (owner !== this.owner) return
            keeper?.pause()
        }
    }
    /**
     * 監視を一時停止
     * @param owner オーナー（カレントオーナー以外からの要求は無視）
     */
    fun resume(owner:Activity) {
        synchronized(this) {
            if (owner !== this.owner) return
            keeper?.resume()
        }
    }

    fun close() {
        synchronized(this) {
            keeper?.close()
            keeper = null
        }
    }

    fun touch(host:AuthHost) {
        keeper?.touch(host)
    }

    fun globalPause(): Closeable {
        globalPauseCount.getAndIncrement()
        return GenericCloseable {
            globalPauseCount.decrementAndGet()
        }
    }
}