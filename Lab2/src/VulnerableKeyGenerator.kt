import java.math.BigInteger

class VulnerableKeyGenerator(bitLength: Int, minProbability: Double) {
    private val primeGenerator = RsaService.KeyGenerator(
        RsaService.TestType.MILLER_RABIN, minProbability, bitLength
    )
    private val random = RandomNumbers()

    // генерируем RSA-ключи с маленьким d для демонстрации атаки Винера
    fun generate(): RsaKeyPair {
        while (true) {
            val p = primeGenerator.generatePrime()
            val q = primeGenerator.generatePrime()
            if (p == q) continue

            val n = p * q
            val phi = (p - BigInteger.ONE) * (q - BigInteger.ONE)

            // вычисляем верхнюю границу для маленького d
            val fourthRoot = NumberTheory.sqrtFloor(NumberTheory.sqrtFloor(n))
            val upper = fourthRoot / BigInteger.valueOf(3)
            if (upper <= BigInteger.valueOf(3)) continue

            // выбираем d взаимно простым с phi
            var d: BigInteger
            do {
                d = random.below(upper - BigInteger.valueOf(3)) + BigInteger.valueOf(3)
            } while (NumberTheory.gcd(d, phi) != BigInteger.ONE)

            // получаем открытый показатель e по известному d
            val e = NumberTheory.inverse(d, phi)
            val publicKey = RsaPublicKey(n, e)

            return RsaKeyPair(publicKey, RsaPrivateKey(n, d), p, q)
        }
    }
}
