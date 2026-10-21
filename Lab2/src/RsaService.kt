import java.io.ByteArrayOutputStream
import java.math.BigInteger

class RsaService(testType: TestType, minProbability: Double, bitLength: Int) {
    enum class TestType { FERMAT, SOLOVAY_STRASSEN, MILLER_RABIN }

    // В Kotlin класс без inner является nested: не хранит ссылку на RsaService.
    class KeyGenerator(
        testType: TestType,
        private val minProbability: Double,
        private val bitLength: Int
    ) {
        private val random = RandomNumbers()
        private val test: PrimalityTest

        init {
            require(minProbability >= 0.5 && minProbability < 1.0) { "Вероятность должна быть в [0.5, 1)" }
            require(bitLength >= 16) { "Битовая длина кандидата должна быть не меньше 16" }
            test = when (testType) {
                TestType.FERMAT -> FermatTest()
                TestType.SOLOVAY_STRASSEN -> SolovayStrassenTest()
                TestType.MILLER_RABIN -> MillerRabinTest()
            }
        }

        fun generatePrime(): BigInteger {
            while (true) {
                val candidate = random.oddWithBitLength(bitLength)
                if (test.isProbablePrime(candidate, minProbability)) return candidate
            }
        }

        fun minimumFermatSteps(): BigInteger {
            return BigInteger.ONE.shiftLeft(bitLength - 8)
        }

        fun generate(): RsaKeyPair {
            // 65537 — стандартная открытая экспонента, а не заранее готовый ключ.
            val e = BigInteger.valueOf(65537)
            while (true) {
                val p = generatePrime()
                val q = generatePrime()
                if (p == q) continue
                val n = p * q
                if (fermatSteps(p, q) < minimumFermatSteps()) continue
                val phi = (p - BigInteger.ONE) * (q - BigInteger.ONE)
                if (NumberTheory.gcd(e, phi) != BigInteger.ONE) continue
                val d = NumberTheory.inverse(e, phi)
                if (d <= NumberTheory.sqrtFloor(n)) continue
                val publicKey = RsaPublicKey(n, e)
                // Дополнительно проверяем сам реализованный вариант атаки Винера.
                if (WienerAttack.attack(publicKey).privateExponent != null) continue
                return RsaKeyPair(publicKey, RsaPrivateKey(n, d), p, q)
            }
        }

        fun fermatSteps(p: BigInteger, q: BigInteger): BigInteger {
            require(p > BigInteger.TWO && q > BigInteger.TWO && p.testBit(0) && q.testBit(0)) {
                "Нужны нечётные p и q больше 2"
            }
            val n = p * q
            var start = NumberTheory.sqrtFloor(n)
            if (start * start < n) start++
            // A идёт от ceil(sqrt(n)) до (p+q)/2. Это точное число приращений.
            return (p + q) / BigInteger.TWO - start
        }
    }

    val keyGenerator = KeyGenerator(testType, minProbability, bitLength)
    var keyPair: RsaKeyPair = keyGenerator.generate()
        private set

    fun generateNewKeyPair(): RsaKeyPair {
        keyPair = keyGenerator.generate()
        return keyPair
    }

    fun encrypt(message: BigInteger, key: RsaPublicKey = keyPair.publicKey): BigInteger {
        require(key.n > BigInteger.ONE && key.e > BigInteger.ONE) { "Некорректный открытый ключ" }
        require(message >= BigInteger.ZERO && message < key.n) { "Сообщение должно быть в [0, n)" }
        return NumberTheory.modPow(message, key.e, key.n)
    }

    fun decrypt(ciphertext: BigInteger, key: RsaPrivateKey = keyPair.privateKey): BigInteger {
        require(key.n > BigInteger.ONE && key.d > BigInteger.ZERO) { "Некорректный закрытый ключ" }
        require(ciphertext >= BigInteger.ZERO && ciphertext < key.n) { "Шифротекст должен быть в [0, n)" }
        return NumberTheory.modPow(ciphertext, key.d, key.n)
    }

    fun encrypt(data: ByteArray, key: RsaPublicKey = keyPair.publicKey): ByteArray {
        require(key.n > BigInteger.ONE && key.e > BigInteger.ONE) { "Некорректный открытый ключ" }
        val payloadSize = (key.n.bitLength() - 1) / 8 - 1
        require(payloadSize > 0) { "Модуль слишком мал для шифрования байтов" }
        val blockSize = (key.n.bitLength() + 7) / 8
        val output = ByteArrayOutputStream()
        var offset = 0
        while (offset < data.size) {
            var length = data.size - offset
            if (length > payloadSize) length = payloadSize
            // Маркер 1 сохраняет ведущие нули и длину последнего блока.
            val block = ByteArray(length + 1)
            block[0] = 1
            for (i in 0 until length) block[i + 1] = data[offset + i]
            val encrypted = encrypt(BigInteger(1, block), key)
            output.write(toFixedBytes(encrypted, blockSize))
            offset += length
        }
        return output.toByteArray()
    }

    fun decrypt(data: ByteArray, key: RsaPrivateKey = keyPair.privateKey): ByteArray {
        require(key.n > BigInteger.ONE && key.d > BigInteger.ZERO) { "Некорректный закрытый ключ" }
        val payloadSize = (key.n.bitLength() - 1) / 8 - 1
        require(payloadSize > 0) { "Модуль слишком мал для шифрования байтов" }
        val blockSize = (key.n.bitLength() + 7) / 8
        require(data.size % blockSize == 0) { "Шифротекст содержит неполный блок" }
        val output = ByteArrayOutputStream()
        var offset = 0
        while (offset < data.size) {
            val encrypted = BigInteger(1, data.copyOfRange(offset, offset + blockSize))
            val block = decrypt(encrypted, key).toByteArray()
            require(block.size in 2..payloadSize + 1 && block[0] == 1.toByte()) {
                "Неверный формат блока или неверный ключ"
            }
            if (offset + blockSize < data.size) {
                require(block.size == payloadSize + 1) { "Укороченный блок допустим только в конце" }
            }
            output.write(block, 1, block.size - 1)
            offset += blockSize
        }
        return output.toByteArray()
    }

    private fun toFixedBytes(value: BigInteger, length: Int): ByteArray {
        val bytes = value.toByteArray()
        var start = 0
        if (bytes.size > 1 && bytes[0] == 0.toByte()) start = 1
        val count = bytes.size - start
        require(count <= length) { "Число не помещается в блок" }
        val result = ByteArray(length)
        for (i in 0 until count) result[length - count + i] = bytes[start + i]
        return result
    }
}
