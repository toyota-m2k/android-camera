package io.github.toyota32k.secureCamera.client.auth

import io.github.toyota32k.dialog.task.UtImmortalTask
import io.github.toyota32k.dialog.task.showConfirmMessageBox
import io.github.toyota32k.secureCamera.settings.Settings

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

    enum class Result(val msg:String, val succeeded:Boolean=false, val error:Boolean=false) {
        OK("ok",true),
        NO_HOST("No host is registered.", false, true),
        NO_ACTIVE_HOST("No active host is found.", false, true),
        CANCELLED("Cancelled by user.")
        ;
        suspend fun message():Boolean {
            if (error) {
                UtImmortalTask.awaitTaskResult("authenticateAndMessage") {
                    showConfirmMessageBox(null, msg)
                    true
                }
            }
            return succeeded
        }
    }

    suspend fun autoAuth():AuthHost? {
        authenticate().message()
        return currentHost
    }

    /**
     * primary/secondaryのうち、接続可能なホストに対して認証を行う。
     */
    suspend fun authenticate():Result {
        if (currentHost?.authenticate()==AuthHost.AuthResult.AUTHORIZED) {
            return Result.OK
        }
        if (hosts.isEmpty()) return Result.NO_HOST
        for(host in hosts) {
            val r = authenticate(host)
            if (r == Result.NO_ACTIVE_HOST) continue
            return r
        }
        return Result.NO_ACTIVE_HOST
    }

    /**
     * 指定したホストに対して認証を行う
     */
    suspend fun authenticate(authHost:AuthHost):Result {
        return when (authHost.authenticate()) {
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