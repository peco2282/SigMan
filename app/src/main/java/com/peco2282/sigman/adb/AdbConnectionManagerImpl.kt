package com.peco2282.sigman.adb

import android.annotation.SuppressLint
import android.content.Context
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.Certificate
import java.util.*

class AdbConnectionManagerImpl(private val context: Context) : AbsAdbConnectionManager() {
  private val privateKey: PrivateKey
  private val certificate: Certificate

  init {
    // 簡易的な鍵ペア生成 (本来は永続化すべき)
    val keyGen = KeyPairGenerator.getInstance("RSA")
    keyGen.initialize(2048)
    val keyPair = keyGen.generateKeyPair()
    privateKey = keyPair.private

    // 自己署名証明書の生成 (BouncyCastleを使用)
    val dnName = X500Name("CN=SigMan")
    val certBuilder = JcaX509v3CertificateBuilder(
      dnName,
      BigInteger.valueOf(System.currentTimeMillis()),
      Date(System.currentTimeMillis() - 1000L * 60 * 60 * 24),
      Date(System.currentTimeMillis() + 1000L * 60 * 60 * 24 * 365),
      dnName,
      keyPair.public
    )
    val contentSigner = JcaContentSignerBuilder("SHA256WithRSA").build(privateKey)
    certificate = JcaX509CertificateConverter().getCertificate(certBuilder.build(contentSigner))
  }

  override fun getPrivateKey(): PrivateKey = privateKey
  override fun getCertificate(): Certificate = certificate
  override fun getDeviceName(): String = "SigMan-ADB"

  companion object {
    @SuppressLint("StaticFieldLeak")
    @Volatile
    private var INSTANCE: AdbConnectionManagerImpl? = null

    fun getInstance(context: Context): AdbConnectionManagerImpl {
      return INSTANCE ?: synchronized(this) {
        INSTANCE ?: AdbConnectionManagerImpl(context.applicationContext).also { INSTANCE = it }
      }
    }
  }
}
