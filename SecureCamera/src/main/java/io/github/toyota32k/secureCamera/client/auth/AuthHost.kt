package io.github.toyota32k.secureCamera.client.auth

import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.secureCamera.client.NetClient
import io.github.toyota32k.secureCamera.client.TcClient
import io.github.toyota32k.secureCamera.dialog.PasswordDialog
import io.github.toyota32k.secureCamera.settings.SecureArchiveHost
import io.github.toyota32k.secureCamera.utils.HashUtils
import io.github.toyota32k.secureCamera.utils.HashUtils.encodeBase64
import io.github.toyota32k.secureCamera.utils.HashUtils.encodeHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import kotlin.time.Duration.Companion.seconds

class AuthHost(val activeHost: SecureArchiveHost, val label:String /*Primary or Secondary*/) {
    companion object {
        val logger = UtLog("Auth", TcClient.logger, this::class.java)
        const val PWD_SEED = "y6c46S/PBqd1zGFwghK2AFqvSDbdjl+YL/DKXgn/pkECj0x2fic5hxntizw5"

        suspend fun isHostInService(host: SecureArchiveHost):Boolean {
            val scheme = if (host.isHttps) "https" else "http"
            val url = "$scheme://${host.address}/nop"
            val req = Request.Builder().url(url).get().build()
            val result = NetClient.shortCallAsync(req, host)
            return result?.isSuccessful == true
        }
    }

    var authToken:String? = null
        private set
    var challenge:String? = null
        private set
    val displayName: String
        get() = "$label-${activeHost.shortDisplayName}"

    fun makeUrl(path:String):String
            = activeHost.makeUrl(path)
    fun makeAuthUrl(cmd:String, vararg queries: Pair<String,Any?>):String {
        return StringBuilder(makeUrl(cmd))
            .append("?auth=")
            .append(authToken)
            .apply {
                queries.fold(this) { acc, pair ->
                    acc.append("&").append(pair.first)
                        .apply {
                            if (pair.second!=null) {
                                append("=").append(pair.second)
                            }
                        }
                }
            }.toString().apply {
                logger.debug(this)
            }
    }

    enum class AuthResult(val ok:Boolean) {
        AUTHORIZED(true),     // ログイン済み
        OFFLINE(false),        // 接続不可
        CANCELLED(false),      // キャンセル
    }

    suspend fun isConnectable():Boolean {
        return isHostInService(activeHost)
    }

    /**
     * 認証用の最上位API
     * - 同時（１秒以内）の認証要求を１つにまとめる
     */
    suspend fun authenticate(): AuthResult {
        var responsible: Boolean
        var authResult: MutableStateFlow<AuthResult?>

        mutex.withLock {
            if (authResultFlow == null) {
                authResultFlow = MutableStateFlow(null)
                responsible = true
            } else {
                responsible = false
            }
            authResult = authResultFlow!!
        }

        return if (responsible) {
            authenticateCore().also { result ->
                authResult.value = result
                delay(1.seconds)    // 1秒間は結果を維持する
                mutex.withLock {
                    authResultFlow = null
                }
            }
        } else {
            authResult.filterNotNull().first()
        }
    }

    /**
     * パスワードを与えて認証を試行
     * 認証ダイアログから呼び出す
     */
    suspend fun tryAuthWithPassword(password:String) : Boolean {
        return withContext(Dispatchers.IO) {
            val challenge = challenge ?: getChallenge() ?: return@withContext false
            val passPhrase = getPassPhrase(password, challenge)
            val req = Request.Builder()
                .url(authUrl)
                .put(passPhrase.toRequestBody("text/plain".toMediaType()))
                .build()
            try {
                NetClient.executeAsync(req, activeHost).use { res ->
                    if (res.code == 200) {
                        // OK
                        authTokenFromResponse(res)
                        prevPwd = password
                        true
                    } else {
                        val c = challengeFromResponse(res)
                        if (c != challenge) {
                            null// to be retried.
                        } else {
                            false
                        }
                    }
                } ?: tryAuthWithPassword(password)
            } catch (e:Throwable) {
                logger.error(e)
                false
            }
        }
    }

    fun reset() {
        authToken = null
        challenge = null
    }

    // region Internals

    private var authResultFlow: MutableStateFlow<AuthResult?>? = null
    private var mutex = Mutex()

    private var prevPwd:String? = null

    /**
     * authenticate()のコア
     * - オフラインなら即エラー(OFFLINE)
     * - 認証済みならトークン延長してAUTHORIZEDを返す。
     * - 未認証なら認証ダイアログを表示して認証する
     *   - キャンセルされたらCANCELLEDを返す。
     *   - 認証に成功したらAUTHORIZEDを返す
     */
    private suspend fun authenticateCore():AuthResult {
        if (!isHostInService(activeHost)) {
            return AuthResult.OFFLINE
        }
        if (checkAuthToken()) {
            return AuthResult.AUTHORIZED
        }
        val pwd = prevPwd
        if (!pwd.isNullOrBlank() && tryAuthWithPassword(pwd)) {
            return AuthResult.AUTHORIZED
        }
        if (PasswordDialog.authenticate(this)) {
            return AuthResult.AUTHORIZED
        }
        return AuthResult.CANCELLED
    }

    private fun challengeFromResponse(res: Response):String {
        if(res.code != 401 || res.body.contentType() != "application/json".toMediaType()) {
            throw IllegalStateException("unknown response from the server.")
        }
        val body = res.body.use { it.string() }
        val j = JSONObject(body)
        return j.optString("challenge").apply { challenge = this }
    }

    private fun authTokenFromResponse(res:Response):String {
        if(res.code!=200 || res.body.contentType() != "application/json".toMediaType()) {
            throw IllegalStateException("unknown response from the server.")
        }
        val body = res.body.use { it.string() }
        val j =  JSONObject(body)
        return j.optString("token").apply { authToken = this }
    }

    private val authUrl:String
        get() = makeUrl("auth")

    private fun authUrlWithToken(token:String):String
            = "$authUrl/$token"

    private suspend fun getChallenge():String? {
        val req = Request.Builder()
            .url(authUrlWithToken(""))
            .get()
            .build()
        return try {
            NetClient.executeAsync(req, activeHost).use { res ->
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

    private suspend fun checkAuthToken():Boolean {
        val token = authToken ?: return false
        return withContext(Dispatchers.IO) {
            val req = Request.Builder()
                .url(authUrlWithToken(token))
                .get()
                .build()
            try {
                NetClient.executeAsync(req, activeHost).use { res ->
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
    // endregion
}
