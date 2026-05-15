package io.github.toyota32k.secureCamera.server

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.ExtendedKeyUsage
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.GeneralName
import org.bouncycastle.asn1.x509.GeneralNames
import org.bouncycastle.asn1.x509.KeyPurposeId
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.cert.Certificate
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import kotlin.time.Duration

/**
 * 自己署名 PFX (PKCS#12) ファイルの生成/読み込みを担うクラス。
 *
 * SecureArchive (C#) 側の CertificateGenerator.cs と同等の証明書を生成する:
 *   - RSA 2048, SHA256withRSA
 *   - BasicConstraints(CA=false), SubjectKeyIdentifier
 *   - KeyUsage(digitalSignature + keyEncipherment)
 *   - ExtendedKeyUsage(serverAuth)
 *   - SubjectAlternativeName (DNS + IP)
 *
 * Fingerprint は SHA-256(DER) を "AB:CD:..." (大文字 hex, コロン区切り) で返す。
 * これは SecureArchive の ComputeSha256Fingerprint と同じ形式で、
 * SecureCamera 側の [io.github.toyota32k.secureCamera.client.CompositeTrustManager]
 * は ':' / '-' を除去して比較するため、双方の表記で互換になる。
 */
class PFXLoader private constructor(
    private val keyStore: KeyStore,
    private val password: CharArray,
    private val alias: String,
) {
    /** ロード済み PFX から TLS サーバ用の SSLContext を構築する。 */
    fun createSSLContext(): SSLContext {
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
            init(keyStore, password)
        }
        return SSLContext.getInstance("TLS").apply {
            init(kmf.keyManagers, null, SecureRandom())
        }
    }

    /** SHA-256 fingerprint を "AB:CD:EF:..." 形式で返す (SecureArchive と同形式)。 */
    fun getFingerprint(): String {
        val cert = keyStore.getCertificate(alias) as X509Certificate
        val sha = MessageDigest.getInstance("SHA-256").digest(cert.encoded)
        return sha.joinToString(":") { "%02X".format(it) }
    }

    /** ロード済み証明書 (公開) を取得。.cer 配布や検証用に。 */
    fun getCertificate(): X509Certificate = keyStore.getCertificate(alias) as X509Certificate

    companion object {
        private const val DEFAULT_ALIAS = "secureCamera"
        private const val KEY_ALGORITHM = "RSA"
        private const val KEY_SIZE = 2048
        private const val SIG_ALGORITHM = "SHA256withRSA"

        // 注意: Android には "BC" という名前で機能限定版の BouncyCastle が同梱されており、
        // Security.addProvider(BouncyCastleProvider()) で上書きしようとしても無視される。
        // したがって setProvider("BC") で得られるのは縮小版で、SHA256withRSA の ContentSigner
        // を生成できない。ここでは provider 名を指定せず、JCA に算法名から探させる
        // (Android では AndroidOpenSSL/Conscrypt が SHA256withRSA を実装している)。
        // bcpkix の X509v3CertificateBuilder 等のクラス自体は provider 登録なしで使える。

        /**
         * 自己署名証明書 + 秘密鍵を生成し、PKCS#12 (PFX) として [path] に保存する。
         *
         * @param hostnames SAN に入れる DNS 名のリスト (例: ["localhost", "device.local"])。空要素は無視。
         * @param ipAddresses SAN に入れる IP アドレスのリスト (例: ["127.0.0.1", "192.168.1.10"])。
         */
        fun createPfxFile(
            path: Path,
            cn: String,
            hostnames: List<String>,
            ipAddresses: List<String>,
            pfxPassword: String,
            validity: Duration,
            alias: String = DEFAULT_ALIAS,
        ) {
            val kp = KeyPairGenerator.getInstance(KEY_ALGORITHM).apply {
                initialize(KEY_SIZE)
            }.generateKeyPair()

            val nowMs = System.currentTimeMillis()
            val notBefore = Date(nowMs - 24L * 60 * 60 * 1000)
            val notAfter = Date(nowMs + validity.inWholeMilliseconds)
            val subject = X500Name("CN=$cn")
            val serial = BigInteger(64, SecureRandom()).abs()

            val builder = JcaX509v3CertificateBuilder(
                subject, serial, notBefore, notAfter, subject, kp.public
            )

            val extUtils = JcaX509ExtensionUtils()
            builder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
            builder.addExtension(
                Extension.subjectKeyIdentifier, false,
                extUtils.createSubjectKeyIdentifier(kp.public)
            )
            builder.addExtension(
                Extension.keyUsage, true,
                KeyUsage(KeyUsage.digitalSignature or KeyUsage.keyEncipherment)
            )
            builder.addExtension(
                Extension.extendedKeyUsage, true,
                ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth)
            )

            val sanEntries = buildList {
                hostnames.asSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { add(GeneralName(GeneralName.dNSName, it)) }
                ipAddresses.asSequence()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .forEach { add(GeneralName(GeneralName.iPAddress, it)) }
            }
            if (sanEntries.isNotEmpty()) {
                builder.addExtension(
                    Extension.subjectAlternativeName, false,
                    GeneralNames(sanEntries.toTypedArray())
                )
            }

            val signer = JcaContentSignerBuilder(SIG_ALGORITHM).build(kp.private)
            val cert: X509Certificate = JcaX509CertificateConverter()
                .getCertificate(builder.build(signer))

            val pw = pfxPassword.toCharArray()
            val keyStore = KeyStore.getInstance("PKCS12").apply {
                load(null, null)
                setKeyEntry(alias, kp.private, pw, arrayOf<Certificate>(cert))
            }
            path.parent?.let { Files.createDirectories(it) }
            Files.newOutputStream(path).use { os ->
                keyStore.store(os, pw)
            }
        }

        /** PFX ファイルを読み込み、最初に見つかった秘密鍵エントリを使って [PFXLoader] を返す。 */
        fun loadPfxFile(path: Path, pfxPassword: String): PFXLoader {
            val pw = pfxPassword.toCharArray()
            val keyStore = KeyStore.getInstance("PKCS12").apply {
                Files.newInputStream(path).use { input -> load(input, pw) }
            }
            val alias = keyStore.aliases().toList().firstOrNull { keyStore.isKeyEntry(it) }
                ?: throw IllegalStateException("No private key entry in PFX: $path")
            return PFXLoader(keyStore, pw, alias)
        }
    }
}
