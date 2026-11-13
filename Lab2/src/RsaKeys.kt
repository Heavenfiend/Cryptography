import java.math.BigInteger

// открытый ключ RSA: n - модуль, e - показатель для шифрования
data class RsaPublicKey(val n: BigInteger, val e: BigInteger)

// закрытый ключ RSA: n - модуль, d - показатель для расшифрования
data class RsaPrivateKey(val n: BigInteger, val d: BigInteger)

// хранит пару ключей и простые числа p и q, из которых они получены
data class RsaKeyPair(
    val publicKey: RsaPublicKey,
    val privateKey: RsaPrivateKey,
    val p: BigInteger,
    val q: BigInteger
) {
    // вычисляет функцию Эйлера для n = p * q
    val phi: BigInteger
        get() = (p - BigInteger.ONE) * (q - BigInteger.ONE)
}
