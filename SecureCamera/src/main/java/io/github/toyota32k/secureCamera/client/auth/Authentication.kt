package io.github.toyota32k.secureCamera.client.auth

import io.github.toyota32k.secureCamera.client.auth.HashUtils.encodeBase64
import io.github.toyota32k.secureCamera.client.auth.HashUtils.encodeHex
import io.github.toyota32k.dialog.task.UtImmortalSimpleTask
import io.github.toyota32k.secureCamera.client.NetClient
import io.github.toyota32k.secureCamera.dialog.PasswordDialog
import io.github.toyota32k.secureCamera.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

object Authentication {
    const val PWD_SEED = "y6c46S/PBqd1zGFwghK2AFqvSDbdjl+YL/DKXgn/pkECj0x2fic5hxntizw5"

    var authToken:String? = null
        private set
    var challenge:String? = null
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
        get() = "http://${Settings.SecureArchive.address}/auth"

    fun authUrlWithToken(token:String):String
        = "$authUrl/$token"

    private suspend fun getChallenge():String {
        val req = Request.Builder()
            .url(authUrl)
            .get()
            .build()
        return challengeFromResponse(NetClient.executeAsync(req))
    }

    private fun getPassPhrase(password:String, challenge:String) : String {
        val hashedPassword = HashUtils.sha256(password, PWD_SEED).encodeHex()
        return HashUtils.sha256(challenge, hashedPassword).encodeBase64()
    }

    suspend fun authWithPassword(password:String) : Boolean {
        return withContext(Dispatchers.IO) {
            val challenge = challenge ?: getChallenge()
            val passPhrase = getPassPhrase(password, challenge)
            val req = Request.Builder()
                .url(authUrl)
                .put(passPhrase.toRequestBody("text/plain".toMediaType()))
                .build()
            val res = NetClient.executeAsync(req)
            if(res.code==200) {
                // OK
                authTokenFromResponse(res)
                true
            } else {
                val c = challengeFromResponse(res)
                if(c!=challenge) {
                    authWithPassword(password)
                } else {
                    false
                }
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
            val res = NetClient.executeAsync(req)
            if (res.code == 200) {
                // OK
                true
            } else {
                challengeFromResponse(res)
                false
            }
        }

    }

    suspend fun authentication(force:Boolean=false):Boolean {
        if(!force && checkAuthToken()) return true
        return PasswordDialog.authenticate()
    }
}