package io.github.toyota32k.secureCamera.client.auth

import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.showConfirmMessageBox
import io.github.toyota32k.secureCamera.settings.Settings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object Authentication {
    private val logger = AuthHost.logger

    val hosts = mutableListOf<AuthHost>()

    fun resetWithSettings() {
        hosts.clear()
        currentHost = null
        Settings.SecureArchive.primaryHost?.also { primary ->
            hosts.add(AuthHost(primary, "Primary"))
        }
        Settings.SecureArchive.secondaryHost?.also { secondary ->
            hosts.add(AuthHost(secondary, "Secondary"))
        }
    }

    suspend fun connectableHosts():List<AuthHost> {
        return hosts.filter { it.isConnectable() }
    }

    var currentHost: AuthHost? = null
    val anyHost: AuthHost? = hosts.firstOrNull()
    private val mutex = Mutex()

    enum class Result(val msg:String, val succeeded:Boolean=false, val error:Boolean=false) {
        OK("ok",true),
        NO_HOST("No host is registered.", false, true),
        NO_ACTIVE_HOST("No active host is found.", false, true),
        CANCELLED("Cancelled by user.")
        ;
        suspend fun message():Boolean {
            if (error) {
                mutex.withLock {
                    UtImmortalTask.awaitTaskResult("authenticateAndMessage") {
                        showConfirmMessageBox(null, msg)
                        true
                    }
                }
            }
            return succeeded
        }
    }

    /**
     * primary/secondaryのうち、接続可能なホストに対して認証い、エラーメッセージも表示する。
     */
    suspend fun authAndMessage():AuthHost? {
        return if (authenticate().message()) {
            currentHost
        } else null
    }

    /**
     * primary/secondaryのうち、接続可能なホストに対して認証を行う。
     */
    suspend fun authenticate():Result {
        when (currentHost?.authenticate()) {
            AuthHost.AuthResult.AUTHORIZED-> return Result.OK
            AuthHost.AuthResult.CANCELLED-> return Result.CANCELLED
            else -> {}
        }
        if (hosts.isEmpty()) return Result.NO_HOST
        for(host in hosts) {
            if (host == currentHost) continue
            val r = authenticate(host)
            if (r.error) continue
            return r
        }
        return Result.NO_ACTIVE_HOST
    }

    /**
     * 指定したホストに対して認証を行う
     */
    suspend fun authenticate(authHost:AuthHost):Result {
        return mutex.withLock {
            when (authHost.authenticate()) {
                AuthHost.AuthResult.AUTHORIZED -> {
                    logger.info("select ${authHost.displayName}")
                    currentHost = authHost
                    Result.OK
                }

                AuthHost.AuthResult.OFFLINE -> Result.NO_ACTIVE_HOST
                AuthHost.AuthResult.CANCELLED -> Result.CANCELLED
            }
        }
    }
}