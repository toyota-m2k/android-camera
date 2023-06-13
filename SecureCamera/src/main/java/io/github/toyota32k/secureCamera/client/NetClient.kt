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
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

object NetClient {
    private val motherClient : OkHttpClient =
        OkHttpClient.Builder()
//            .readTimeout(120, TimeUnit.SECONDS)
//            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    val logger = UtLog("NetClient",null,this::class.java)

    suspend fun executeAsync(req: Request, canceller: Canceller?=null): Response {
        logger.debug("${req.url}")
        return motherClient.newCall(req).executeAsync(canceller)
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
    private suspend fun Call.executeAsync(canceller: Canceller?) : Response {
        return suspendCoroutine {cont ->
            try {
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
                canceller?.setCall(this)
            } catch(e:Throwable) {
                UtLogger.error("NetClient: exception: ${e.localizedMessage}")
                cont.resumeWithException(e)
            }
        }
    }
}