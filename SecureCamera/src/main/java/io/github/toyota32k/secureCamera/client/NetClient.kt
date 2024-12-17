package io.github.toyota32k.secureCamera.client

import io.github.toyota32k.utils.UtLog
import io.github.toyota32k.utils.UtLogger
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import kotlin.time.Duration.Companion.seconds
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.time.toJavaDuration

object NetClient {
    val motherClient : OkHttpClient =
        OkHttpClient.Builder()
            .readTimeout(30, TimeUnit.SECONDS)
//            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    val logger = UtLog("Net",null,this::class.java)

    suspend fun executeAsync(req: Request, canceller: Canceller?=null): Response {
        logger.debug("${req.url}")
        return motherClient.newCall(req).executeAsync(canceller)
    }

    private val shortClient:OkHttpClient by lazy { motherClient.newBuilder().connectTimeout(1.seconds.toJavaDuration()).readTimeout(1.seconds.toJavaDuration()).writeTimeout(1.seconds.toJavaDuration()).build()}
    suspend fun shortCallAsync(req:Request): Response? {
        return try {
            shortClient.newCall(req).executeAsync(null)
        } catch(e:Throwable) {
            logger.error(e)
            null
        }
    }

    suspend fun executeAndGetJsonAsync(req: Request): JSONObject {
        return executeAsync(req, null).use { res ->
            if (res.code != 200) throw IllegalStateException("Server Response Error (${res.code})")
            val body = res.body?.use { it.string() } ?: throw IllegalStateException("Server Response No Data.")
            JSONObject(body)
        }
    }

    /**
     * Coroutineを利用し、スレッドをブロックしないで同期的な通信を可能にする拡張メソッド
     * OkHttpのnewCall().execute()を置き換えるだけで使える。
     */
    suspend fun Call.executeAsync(canceller: Canceller?) : Response {
        return suspendCoroutine {cont ->
            try {
                canceller?.setCall(this)
                enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        UtLogger.error("NetClient: error: ${e.localizedMessage}")
                        cont.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        UtLogger.debug("NetClient: completed (${response.code}): ${call.request().url}")
                        cont.resume(response)
                    }
                })
            } catch(e:Throwable) {
                UtLogger.error("NetClient: exception: ${e.localizedMessage}")
                cont.resumeWithException(e)
            }
        }
    }
}