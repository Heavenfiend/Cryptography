import java.math.BigInteger

data class RsaPublicKey(val n: BigInteger, val e: BigInteger)
data class RsaPrivateKey(val n: BigInteger, val d: BigInteger)
data class RsaKeyPair(
    val publicKey: RsaPublicKey,
    val privateKey: RsaPrivateKey,
    val p: BigInteger,
    val q: BigInteger
) {
    val phi: BigInteger
        get() = (p - BigInteger.ONE) * (q - BigInteger.ONE)
}
