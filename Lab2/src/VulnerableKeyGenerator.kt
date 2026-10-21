import java.math.BigInteger

class VulnerableKeyGenerator(bitLength: Int, minProbability: Double) {
    private val primeGenerator = RsaService.KeyGenerator(
        RsaService.TestType.MILLER_RABIN, minProbability, bitLength
    )
    private val random = RandomNumbers()

    fun generate(): RsaKeyPair {
        while (true) {
            val p = primeGenerator.generatePrime()
            val q = primeGenerator.generatePrime()
            if (p == q) continue
            val n = p * q
            val phi = (p - BigInteger.ONE) * (q - BigInteger.ONE)
            val fourthRoot = NumberTheory.sqrtFloor(NumberTheory.sqrtFloor(n))
            val upper = fourthRoot / BigInteger.valueOf(3)
            if (upper <= BigInteger.valueOf(3)) continue
            // Одинаковая битовая длина даёт max(p,q) < 2*min(p,q).
            // Выбираем 3 <= d < floor(n^(1/4))/3, чтобы работала теорема Винера.
            var d: BigInteger
            do {
                d = random.below(upper - BigInteger.valueOf(3)) + BigInteger.valueOf(3)
            } while (NumberTheory.gcd(d, phi) != BigInteger.ONE)
            val e = NumberTheory.inverse(d, phi)
            val publicKey = RsaPublicKey(n, e)
            return RsaKeyPair(publicKey, RsaPrivateKey(n, d), p, q)
        }
    }
}
