package io.github.toyota32k.secureCamera.settings

import io.github.toyota32k.secureCamera.utils.HashUtils
import io.github.toyota32k.secureCamera.utils.HashUtils.encodeBase64
import io.github.toyota32k.secureCamera.utils.HashUtils.encodeHex

object PasswordUtil {
    private const val PWD_SEED = "y6c46S/PBqd1zGFwghK2AFqvSDbdjl+YL/DKXgn/pkECj0x2fic5hxntizw5"
    fun getHashedPassword(plainPassword:String):String {
        return HashUtils.sha256(plainPassword, PWD_SEED).encodeHex()
    }

    fun getPassPhrase(plainPassword:String, challenge:String) : String {
        val hashedPassword = HashUtils.sha256(plainPassword, PWD_SEED).encodeHex()
        return getPassPhraseWithHashedPassword(hashedPassword, challenge)
    }

    fun getPassPhraseWithHashedPassword(hashedPassword:String, challenge:String) : String {
        return HashUtils.sha256(challenge, hashedPassword).encodeBase64()
    }
//
//    private val seed:String = "JigI78#bfiU&%fpq@xe+QZsk?<ww=S24Zr4-d041"
//
//    fun hash(input:String):String {
//        val md = MessageDigest.getInstance("SHA-1")
//        md.update(seed.toByteArray(Charsets.US_ASCII))
//        md.update(input.toByteArray())
//        val result: ByteArray = md.digest()
//        val sb = StringBuffer(result.size*2)
//        for (i in result.indices) {
//            sb.append(((result[i].toInt() and 0xff) + 0x100).toString(16).substring(1))
//        }
//        return sb.toString()
//    }
}