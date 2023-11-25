package io.github.toyota32k.secureCamera.client.auth

import android.util.Log
import io.github.toyota32k.dialog.task.UtImmortalSimpleTask
import io.github.toyota32k.dialog.task.showConfirmMessageBox
import io.github.toyota32k.secureCamera.client.auth.HashUtils.encodeBase64
import io.github.toyota32k.secureCamera.client.auth.HashUtils.encodeHex
import io.github.toyota32k.secureCamera.client.NetClient
import io.github.toyota32k.secureCamera.client.TcClient
import io.github.toyota32k.secureCamera.dialog.PasswordDialog
import io.github.toyota32k.secureCamera.settings.Settings
import io.github.toyota32k.utils.UtLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

object Authentication {
    private val logger = UtLog("Auth", TcClient.logger, this::class.java)
    const val PWD_SEED = "y6c46S/PBqd1zGFwghK2AFqvSDbdjl+YL/DKXgn/pkECj0x2fic5hxntizw5"

    var authToken:String? = null
        private set
    var challenge:String? = null
        private set
    var activeHostAddress:String? = null
        private set


    fun reset() {
        authToken = null
        challenge = null
    }

    private fun challengeFromResponse(res: Response):String {
        if(res.code != 401 || res.body?.contentType() != "application/json".toMediaType()) {
            throw IllegalStateException("unknown response from the server.")
        }
        val body = res.body?.use { it.string() } ?: throw IllegalStateException("response has no data.")
        val j =  JSONObject(body)
        return j.optString("challenge").apply { challenge = this }
    }

    private fun authTokenFromResponse(res:Response):String {
        if(res.code!=200 || res.body?.contentType() != "application/json".toMediaType()) {
            throw IllegalStateException("unknown response from the server.")
        }
        val body = res.body?.use { it.string() } ?: throw IllegalStateException("response has no data.")
        val j =  JSONObject(body)
        return j.optString("token").apply { authToken = this }
    }

    val authUrl:String
        get() = "http://$activeHostAddress/auth"

    fun authUrlWithToken(token:String):String
        = "$authUrl/$token"

    private suspend fun getChallenge():String? {
        val req = Request.Builder()
            .url(authUrlWithToken(""))
            .get()
            .build()
        return try {
            NetClient.executeAsync(req).use { res ->
                challengeFromResponse(res)
            }
        } catch (e:Throwable) {
            logger.error(e)
            null
        }
    }

    private fun getPassPhrase(password:String, challenge:String) : String {
        val hashedPassword = HashUtils.sha256(password, PWD_SEED).encodeHex()
        return HashUtils.sha256(challenge, hashedPassword).encodeBase64()
    }

    suspend fun authWithPassword(password:String) : Boolean {
        return withContext(Dispatchers.IO) {
            val challenge = challenge ?: getChallenge() ?: return@withContext false
            val passPhrase = getPassPhrase(password, challenge)
            val req = Request.Builder()
                .url(authUrl)
                .put(passPhrase.toRequestBody("text/plain".toMediaType()))
                .build()
            try {
                NetClient.executeAsync(req).use { res ->
                    if (res.code == 200) {
                        // OK
                        authTokenFromResponse(res)
                        true
                    } else {
                        val c = challengeFromResponse(res)
                        if (c != challenge) {
                            null// to be retried.
                        } else {
                            false
                        }
                    }
                } ?: authWithPassword(password)
            } catch (e:Throwable) {
                logger.error(e)
                false
            }
        }
    }

    private suspend fun checkAuthToken():Boolean {
        val token = authToken ?: return false
        return withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(authUrlWithToken(token))
                .get()
                .build()
            try {
                NetClient.executeAsync(req).use { res ->
                    if (res.code == 200) {
                        // OK
                        true
                    } else {
                        challengeFromResponse(res)
                        false
                    }
                }
            } catch(e:Throwable) {
                logger.error(e)
                false
            }
        }

    }

    enum class Result(val msg:String, val succeeded:Boolean=false) {
        OK("ok",true),
        NO_HOST("No host is registered."),
        NO_ACTIVE_HOST("No active host is found."),
        CANCELLED("Cancelled by user.")
    }

    private var lastCheckTime:Long = 0L

    /**
     * 指定されたホストがアクセス可能かチェックする。
     */
    private suspend fun checkOneHost(host:String):Boolean {
        val url = "http://$host/nop"
        val req = Request.Builder().url(url).get().build()
        val result = NetClient.shortCallAsync(req)
        return result?.isSuccessful == true
    }

    /**
     * アクセス可能なホストを primary, secondary の順にチェックし、最初に見つかったホストを activeHostAddress に設定する。
     */
    private suspend fun checkHost():Result {
        var empty = true

        val currentHost = activeHostAddress
        if(currentHost!=null) {
            if(System.currentTimeMillis() - lastCheckTime < 5000) {
                // 5秒以内の連続呼び出しならチェックしない。
                return Result.OK
            }
            if(checkOneHost(currentHost)) {
                return Result.OK
            }
        }

        for(host in Settings.SecureArchive.hosts) {
            if(currentHost!=host && checkOneHost(host)) {
                activeHostAddress = host
                lastCheckTime = System.currentTimeMillis()
                return Result.OK
            }
            empty = false
        }
        activeHostAddress = null
        return if(empty) Result.NO_HOST else Result.NO_ACTIVE_HOST
    }

    val activeHostLabel:String
        get() = when (activeHostAddress) {
                Settings.SecureArchive.primaryAddress -> "Primary $activeHostAddress"
                Settings.SecureArchive.secondaryAddress -> "Secondary $activeHostAddress"
                else -> "NO HOST"
            }
    val isPrimaryActive:Boolean
        get() = activeHostAddress == Settings.SecureArchive.primaryAddress

//    /**
//     * アクセス可能なホストのリストを返す。
//     */
//    suspend fun enumerateHosts():List<String> {
//        val list = mutableListOf<String>()
//        for(host in Settings.SecureArchive.hosts) {
//            if(checkOneHost(host)) {
//                list.add(host)
//            }
//        }
//        return list
//    }

    /**
     * アクセス可能なホストを見つけて認証する。
     */
    private suspend fun authenticate():Result {
        checkHost().let { if(!it.succeeded) return it }
        if(checkAuthToken()) return Result.OK
        return if(PasswordDialog.authenticate(activeHostLabel)) Result.OK else Result.CANCELLED
    }

    suspend fun authenticateAndMessage():Boolean {
        suspend fun showMessage(msg:String):Boolean {
            UtImmortalSimpleTask.runAsync {
                showConfirmMessageBox(null, msg)
                true
            }
            return false
        }
        return logger.chronos(level = Log.INFO) {
            when(authenticate()) {
                Result.OK -> true
                Result.NO_HOST -> showMessage("No hosts are registered.")
                Result.NO_ACTIVE_HOST -> showMessage("No hosts are active.")
                Result.CANCELLED -> false
            }
        }
    }
}