package io.github.toyota32k.secureCamera.client

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import io.github.toyota32k.logger.UtLog
import io.github.toyota32k.secureCamera.SCApplication
import io.github.toyota32k.secureCamera.client.BooTubeDiscovery.Companion.resolveOnce
import io.github.toyota32k.secureCamera.settings.SecureArchiveHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import java.net.InetAddress
import java.util.concurrent.Executor

/**
 * mDNS-SD で BooTube プロトコル互換サーバを発見・解決するラッパ。
 *
 * サーバ側は `_booapi._tcp.` で広告する (BooTube は MdnsAdvertiser.cs、
 * winui-secure-archive 等は今後の同パターン実装)。アプリ識別は TXT の `app` キーで行う。
 * 本クラスは Android `NsdManager` でそのサービスを discover し、resolve して
 * IP/port/TXT レコードを取り出して [services] に流す。
 *
 * 使い方：
 *   - ホスト追加ダイアログのライフサイクルで [start]/[stop]
 *   - 接続前の IP 再解決には companion の [resolveOnce]
 *
 * 権限: `CHANGE_WIFI_MULTICAST_STATE` (MulticastLock 用)
 *
 * 解決 API:
 *   - API 34+: `registerServiceInfoCallback` + `NsdServiceInfo.hostAddresses`
 *   - API 26〜33: `resolveService` + `NsdServiceInfo.host` (deprecated だが互換のため使用)
 */
class BooTubeDiscovery {
    data class DiscoveredServer(
        val serviceName: String,    // mDNS instance 名 ("BooTube on DESKTOP-A12B3C")
        val host: String,           // IPv4 アドレス
        val port: Int,
        val isHttps: Boolean,
        val fingerprint: String?,   // SHA-256 ("AB:CD:..." 形式) or null
        val app: String?,           // TXT app= の値 ("bootube" / "archive" 等)。アプリ識別 / 表示ラベル用
        val hostname: String?,      // TXT hostname= の値 ("TOYOTA-PC.local" 等)。表示用
    ) {
        fun toHost(): SecureArchiveHost = SecureArchiveHost(
            address = "$host:$port",
            serviceName = serviceName,
            fingerprint = fingerprint,
            isHttps = isHttps,
            hostname = stripLocal(hostname),
        )
    }

//    private val appCtx = SCApplication.instance.applicationContext
    private val nsd = SCApplication.instance.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifi = SCApplication.instance.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var multicastLock: WifiManager.MulticastLock? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    /** API 34+ の ServiceInfoCallback はサービス毎に登録するので、停止時に解除するため保持。 */
    private val activeCallbacks = mutableListOf<Any>()

    private val _services = MutableStateFlow<List<DiscoveredServer>>(emptyList())
    val services: StateFlow<List<DiscoveredServer>> = _services.asStateFlow()

    fun start() {
        if (discoveryListener != null) return // 既に起動中

        multicastLock = wifi.createMulticastLock(MULTICAST_LOCK_TAG).apply {
            setReferenceCounted(false)
            acquire()
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {
                logger.debug("Discovery started: $serviceType")
            }
            override fun onDiscoveryStopped(serviceType: String) {
                logger.debug("Discovery stopped: $serviceType")
            }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                logger.error("Start discovery failed: $serviceType err=$errorCode")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                logger.error("Stop discovery failed: $serviceType err=$errorCode")
            }
            override fun onServiceFound(info: NsdServiceInfo) {
                logger.debug("Service found: ${info.serviceName}")
                resolveAndUpdate(info)
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                logger.debug("Service lost: ${info.serviceName}")
                _services.update { list -> list.filterNot { it.serviceName == info.serviceName } }
            }
        }
        discoveryListener = listener
        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (e: Exception) {
            logger.stackTrace(e)
            stop()
        }
    }

    fun stop() {
        // ServiceInfoCallback の登録解除 (API 34+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            for (cb in activeCallbacks) {
                try {
                    nsd.unregisterServiceInfoCallback(cb as NsdManager.ServiceInfoCallback)
                } catch (_: Exception) { /* 二重解除等は無視 */ }
            }
        }
        activeCallbacks.clear()

        discoveryListener?.let { l ->
            try { nsd.stopServiceDiscovery(l) } catch (e: Exception) { logger.stackTrace(e) }
        }
        discoveryListener = null
        multicastLock?.let {
            try { if (it.isHeld) it.release() } catch (e: Exception) { logger.stackTrace(e) }
        }
        multicastLock = null
        _services.value = emptyList()
    }

    /** 発見した service info を resolve して [_services] に反映する。API レベルで API を切替。 */
    private fun resolveAndUpdate(info: NsdServiceInfo, retry: Int = 0) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            resolveWithCallback(info)
        } else {
            resolveLegacy(info, retry)
        }
    }

    /** API 34+ パス: ServiceInfoCallback で IP/port/TXT を取得する。 */
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun resolveWithCallback(info: NsdServiceInfo) {
        val executor: Executor = ContextCompat.getMainExecutor(SCApplication.instance)
        val callback = object : NsdManager.ServiceInfoCallback {
            override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {
                logger.warn("ServiceInfoCallback registration failed: $errorCode")
            }
            override fun onServiceUpdated(updatedInfo: NsdServiceInfo) {
                val host = updatedInfo.hostAddresses.firstOrNull()?.hostAddress ?: return
                applyResolved(updatedInfo.serviceName, host, updatedInfo.port, updatedInfo.attributes)
            }
            override fun onServiceLost() {
                _services.update { list -> list.filterNot { it.serviceName == info.serviceName } }
            }
            override fun onServiceInfoCallbackUnregistered() {
                synchronized(activeCallbacks) { activeCallbacks.remove(this) }
            }
        }
        try {
            synchronized(activeCallbacks) { activeCallbacks.add(callback) }
            nsd.registerServiceInfoCallback(info, executor, callback)
        } catch (e: Exception) {
            logger.stackTrace(e)
            synchronized(activeCallbacks) { activeCallbacks.remove(callback) }
        }
    }

    /**
     * API 26〜33 パス: 旧来の `resolveService`。同時 1 件しか走らせられない (FAILURE_ALREADY_ACTIVE)
     * ため、失敗したらバックオフ込みで簡易リトライする。実用上 LAN なら ~1 秒で収束する。
     */
    @Suppress("DEPRECATION")
    private fun resolveLegacy(info: NsdServiceInfo, retry: Int) {
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onResolveFailed(failedInfo: NsdServiceInfo, errorCode: Int) {
                logger.warn("Resolve failed: ${failedInfo.serviceName} err=$errorCode (retry=$retry)")
                if (errorCode == NsdManager.FAILURE_ALREADY_ACTIVE && retry < MAX_RESOLVE_RETRY) {
                    Handler(Looper.getMainLooper()).postDelayed(
                        { resolveAndUpdate(info, retry + 1) }, 200L
                    )
                }
            }
            override fun onServiceResolved(resolved: NsdServiceInfo) {
                val addr: InetAddress? = resolved.host
                val host = addr?.hostAddress ?: return
                applyResolved(resolved.serviceName, host, resolved.port, resolved.attributes)
            }
        })
    }

    /** Resolve で得たデータを共通フォーマットで [_services] に反映する。 */
    private fun applyResolved(
        serviceName: String,
        host: String,
        port: Int,
        attributes: Map<String, ByteArray?>?,
    ) {
        val attrs = attributes ?: emptyMap()
        val app = attrs[TXT_APP]?.toUtf8()
        if (app != "SA") return // SA以外はペアリングしない
        val isHttps = attrs[TXT_HTTPS]?.toUtf8() == "1"
        val fp = attrs[TXT_FINGERPRINT]?.toUtf8()
        val hostname = attrs[TXT_HOSTNAME]?.toUtf8()
        val server = DiscoveredServer(
            serviceName = serviceName,
            host = host,
            port = port,
            isHttps = isHttps,
            fingerprint = fp,
            app = app,
            hostname = hostname,
        )
        logger.debug("Resolved: $server")
        _services.update { list ->
            list.filterNot { it.serviceName == server.serviceName } + server
        }
    }

    companion object {
        // 互換 REST API を提供する全アプリで共有するサービスタイプ。
        // アプリ識別は TXT の "app" キー (TXT_APP) で行う。
        const val SERVICE_TYPE = "_booapi._tcp."
        const val TXT_HTTPS = "https"
        const val TXT_FINGERPRINT = "fp"
        const val TXT_APP = "app"
        const val TXT_HOSTNAME = "hostname"
        private const val MULTICAST_LOCK_TAG = "booapi-discovery"
        private const val MAX_RESOLVE_RETRY = 5

        private val logger = UtLog("Discovery", SCApplication.logger)

        /**
         * 既知の Service Instance 名 (例 "BooTube on DESKTOP-A12B3C") に対して、現在の IP/port を
         * 取得する。タイムアウト内に見つからなければ null。
         *
         * 接続のたびに呼ぶと毎回 discovery を立ち上げる手間があるので、頻発するならインスタンスを
         * 持ち回した方が効率的だが、画面遷移程度の頻度なら都度生成で問題ない。
         */
        suspend fun resolveOnce(
            serviceInstanceName: String,
            timeoutMs: Long = 3000
        ): DiscoveredServer? {
            val discovery = BooTubeDiscovery()
            return try {
                discovery.start()
                withTimeoutOrNull(timeoutMs) {
                    discovery.services
                        .first { list -> list.any { it.serviceName == serviceInstanceName } }
                        .first { it.serviceName == serviceInstanceName }
                }
            } finally {
                discovery.stop()
            }
        }
        fun stripLocal(hostname: String?): String? {
            return hostname?.substringBefore(".local")
        }
    }
}

private fun ByteArray?.toUtf8(): String? = this?.let { String(it, Charsets.UTF_8) }
