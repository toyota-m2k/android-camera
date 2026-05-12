package io.github.toyota32k.boodroid.data

import android.net.Uri
import io.github.toyota32k.secureCamera.settings.SecureArchiveHost

/**
 * `bootube://<host>:<port>?fp=...&name=...&svc=...&https=1` 形式の
 * ペアリング URI を [HostAddressEntity] に変換するためのユーティリティ。
 *
 * BooTube 側の PairingQrDialog が同じ形式の URI を QR にエンコードしている。
 */
object PairingUri {
    const val SCHEME = "bootube"

    data class Pairing(
        val host: String,
        val port: Int,
        val fingerprint: String?,
        val hostname: String,
        val serviceName: String?,
        val isHttps: Boolean,
    ) {
        fun toHost() = SecureArchiveHost(
            address = "${host}:${port}",
            serviceName = serviceName,
            fingerprint = fingerprint,
            isHttps = isHttps,
            hostname = hostname
        )
    }

    /** URI を解析。スキーム不一致やホスト欠落なら null。 */
    fun parse(uri: Uri): Pairing? {
        if (uri.scheme != SCHEME) return null
        val host = uri.host ?: return null
        if (uri.getQueryParameter("app")!="SA") return null     // SA以外は受け付けない

        // Uri.getPort() は未指定なら -1 を返す。HTTPS 既定 3501 にフォールバック。
        val isHttps = uri.getQueryParameter("https") == "1"
        val port = uri.port.takeIf { it > 0 } ?: if (isHttps) 3801 else 3800
        return Pairing(
            host = host,
            port = port,
            fingerprint = uri.getQueryParameter("fp")?.takeIf { it.isNotEmpty() },
            hostname = uri.getQueryParameter("name")?.takeIf { it.isNotEmpty() } ?: host,
            serviceName = uri.getQueryParameter("svc")?.takeIf { it.isNotEmpty() },
            isHttps = isHttps,
        )
    }
}
