package io.github.toyota32k.secureCamera.settings

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SecureArchiveHost(
    val address: String,    // ip:port
    val serviceName: String? = null,
    val fingerprint: String? = null,
    val isHttps: Boolean = false,
    /** mDNS TXT hostname= から取得 (例 "TOYOTA-PC.local")。表示用。接続には [address] を使う。 */
    val hostname: String? = null,
) {
    fun toJson(): String {
        return Json.encodeToString(this)
    }
    fun isSameHost(dst: SecureArchiveHost):Boolean
            = isSameHost(this, dst)
    fun makeUrl(path:String):String
        = if (isHttps) {
        "https://$address/$path"
    } else {
        "http://$address/$path"
    }
    val isPairedHost get() = !serviceName.isNullOrBlank()
    val displayName get() = if (isPairedHost) "$serviceName - $address" else address
    val isValid get() = address.isNotBlank()

    companion object {
        /**
         * 同一のホストを指しているかの確認用
         * ペアリングした（mDNSから取得した）ホストは、DHCPの場合 ipアドレスが変化するので、serviceName で比較。
         * （serviceNameはPC毎にユニークである必要がある。）
         * 直接ipアドレスを指定して登録したホストは、address を比較
         */
        fun isSameHost(h1: SecureArchiveHost, h2: SecureArchiveHost):Boolean {
            return if (h1.serviceName!=null) {
                // pairing されたホスト
                h1.serviceName == h2.serviceName
            } else if (h2.serviceName==null) {
                h1.address == h2.address
            } else false
        }

        fun fromJson(json: String?): SecureArchiveHost? {
            if (json==null) return null
            return try {
                Json.decodeFromString<SecureArchiveHost>(json)
            } catch (e: Throwable) {
                null
            }
        }
    }
}
