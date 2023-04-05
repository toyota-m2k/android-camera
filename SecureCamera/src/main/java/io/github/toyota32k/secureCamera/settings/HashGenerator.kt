package io.github.toyota32k.secureCamera.settings

import java.nio.charset.Charset
import java.security.MessageDigest

object HashGenerator {
    private val seed:String = "JigI78#bfiU&%fpq@xe+QZsk?<ww=S24Zr4-d041"

    fun hash(input:String):String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update(seed.toByteArray(Charsets.US_ASCII))
        md.update(input.toByteArray())
        val result: ByteArray = md.digest()
        val sb = StringBuffer(result.size*2)
        for (i in result.indices) {
            sb.append(((result[i].toInt() and 0xff) + 0x100).toString(16).substring(1))
        }
        return sb.toString()
    }
}