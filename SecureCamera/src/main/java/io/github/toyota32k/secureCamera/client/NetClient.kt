package io.github.toyota32k.secureCamera.client

import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.secureCamera.SCApplication
import io.github.toyota32k.secureCamera.client.auth.AuthKeeper
import io.github.toyota32k.secureCamera.settings.SecureArchiveHost
import io.github.toyota32k.secureCamera.settings.Settings
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

object NetClient {
    val motherClient : OkHttpClient by lazy {
        val tm = CompositeTrustManager { CompositeTrustManager.fingerprintsFromSettings() }
        val sslContext = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf<TrustManager>(tm), SecureRandom())
        }
        OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
            .sslSocketFactory(sslContext.socketFactory, tm)
            .hostnameVerifier(CompositeTrustManager.makeHostnameVerifier())
            .build()
    }

    private val shortClient:OkHttpClient by lazy {
        motherClient
            .newBuilder()
            .callTimeout(1.seconds)
            .connectTimeout(1.seconds)
            .readTimeout(1.seconds)
            .writeTimeout(1.seconds).build()
    }

    val logger = UtLog("Net",null,this::class.java)

    /**
     * リトライ付き executeAsync
     */
    private suspend fun tryExecuteAsync(client: OkHttpClient, req: Request, canceller: Canceller?, host: SecureArchiveHost):Response {
        return try {
            client.newCall(req).executeAsync(canceller)
        } catch(e:IOException) {
            if (!isReResolvableFailure(e)) throw e
            val rebuilt = tryRebuildWithFreshAddress(req, host) ?: throw e
            logger.warn("Connection failed (${e.javaClass.simpleName}); retrying with refreshed address ${rebuilt.url}")
            client.newCall(rebuilt).executeAsync(canceller)
        }
    }

    /** ネットワーク層レベルの失敗 (≒ IP/port が変わって繋がらない可能性) のみリトライ対象とする。 */
    private fun isReResolvableFailure(e: IOException): Boolean = when (e) {
        is UnknownHostException, is ConnectException,
        is SocketTimeoutException, is NoRouteToHostException -> true
        else -> false
    }

    /**
     * active host が mDNS で発見されたエントリ (`serviceName != null`) の場合、現在の IP を再解決し、
     * 変わっていれば設定を更新したうえでリクエスト URL を新 IP に書き換えて返す。
     * 変わっていない、もしくは解決失敗の場合は null。
     */
    private suspend fun tryRebuildWithFreshAddress(req: Request, host: SecureArchiveHost): Request? {
        val ctx = SCApplication.instance.applicationContext
        val svc = host.serviceName ?: return null
        val resolved = BooTubeDiscovery.resolveOnce( svc) ?: return null
        val newAddr = "${resolved.host}:${resolved.port}"
        if (newAddr == host.address) return null

        val updated = SecureArchiveHost(
            address = newAddr,
            fingerprint = resolved.fingerprint ?: host.fingerprint,
            isHttps = resolved.isHttps || host.isHttps,
            hostname = resolved.hostname ?: host.hostname,
        )
        Settings.SecureArchive.updateHost(host, updated)
        val newUrl = req.url.newBuilder()
            .host(resolved.host)
            .port(resolved.port)
            .build()
        return req.newBuilder().url(newUrl).build()
    }

    suspend fun executeAsync(req: Request, host: SecureArchiveHost, canceller: Canceller?=null): Response {
        logger.debug("${req.url}")
        return AuthKeeper.globalPause().use {
            tryExecuteAsync(motherClient, req, canceller, host)
        }
    }

    suspend fun shortCallAsync(req:Request, host: SecureArchiveHost): Response? {
        return try {
            tryExecuteAsync(shortClient, req, null, host)
        } catch(e:Throwable) {
            logger.error(e)
            null
        }
    }

    suspend fun executeAndGetJsonAsync(req: Request, host: SecureArchiveHost): JSONObject {
        return tryExecuteAsync(motherClient, req, null, host).use { res ->
            if (res.code != 200) throw IllegalStateException("Server Response Error (${res.code})")
            val body = res.body.use { it.string() }
            JSONObject(body)
        }
    }

    /**
     * Coroutineを利用し、スレッドをブロックしないで同期的な通信を可能にする拡張メソッド
     * OkHttpのnewCall().execute()を置き換えるだけで使える。
     */
    suspend fun Call.executeAsync(canceller: Canceller?) : Response {
        return suspendCancellableCoroutine { cont ->
            try {
                canceller?.setCall(this)
                enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        logger.error("NetClient: error: ${e.localizedMessage}")
                        cont.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        logger.debug("NetClient: completed (${response.code}): ${call.request().url}")
                        cont.resume(response)
                    }
                })
            } catch(e:Throwable) {
                logger.error("NetClient: exception: ${e.localizedMessage}")
                cont.resumeWithException(e)
            }
        }
    }
}