package io.github.toyota32k.secureCamera.client.auth

import java.security.MessageDigest
import java.util.Base64

object HashUtils {

    fun encodeHexString(buff:ByteArray):String {
        val sb = StringBuilder(2*buff.size)
        for(b in buff) {
            sb.append(String.format("%02x", b.toInt().and(0xff)))
        }
        return sb.toString()
    }

    fun ByteArray.encodeBase64():String {
        return Base64.getEncoder().encodeToString(this)
    }
    fun ByteArray.encodeHex():String {
        return encodeHexString(this)
    }

    fun sha256(target:String, seed:String):ByteArray {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(seed.toByteArray(Charsets.UTF_8))
        md.update(target.toByteArray(Charsets.UTF_8))
        return md.digest()
    }
}