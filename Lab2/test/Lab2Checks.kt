import java.math.BigInteger
import java.util.Random

object Lab2Checks {
    private var checks = 0
    private val random = Random(20261021)

    @JvmStatic
    fun main(args: Array<String>) {
        arithmetic()
        symbolsAndFunctions()
        primality()
        rsaAndAttacks()
        invalidArguments()
        println("Успешно: $checks проверок")
    }

    private fun verify(condition: Boolean, description: String) {
        check(condition) { description }
        checks++
    }

    private fun number(n: Int): BigInteger = BigInteger.valueOf(n.toLong())

    private fun bruteGcd(a: Int, b: Int): Int {
        var result = 0
        for (d in 1..maxOf(kotlin.math.abs(a), kotlin.math.abs(b))) {
            if (a % d == 0 && b % d == 0) result = d
        }
        return result
    }

    private fun brutePower(base: Int, exponent: Int, modulus: Int): Int {
        var result = 1 % modulus
        val normalized = (base % modulus + modulus) % modulus
        for (i in 0 until exponent) result = result * normalized % modulus
        return result
    }

    private fun arithmetic() {
        for (a in -35..35) {
            for (b in -35..35) {
                val expected = number(bruteGcd(a, b))
                val x = number(a)
                val y = number(b)
                val bezout = NumberTheory.extendedGcd(x, y)
                verify(NumberTheory.gcd(x, y) == expected, "НОД $a, $b")
                verify(NumberTheory.binaryGcd(x, y) == expected, "Бинарный НОД $a, $b")
                verify(bezout.gcd == expected && x * bezout.x + y * bezout.y == expected, "Безу $a, $b")
            }
        }
        for (i in 0 until 1000) {
            val a = random.nextInt(201) - 100
            val e = random.nextInt(80)
            val m = random.nextInt(100) + 1
            verify(NumberTheory.modPow(number(a), number(e), number(m)) == number(brutePower(a, e, m)), "Степень")
        }
        val largeA = BigInteger("123456789012345678901234567890")
        val largeB = BigInteger("987654321098765432109876543210")
        verify(NumberTheory.gcd(largeA, largeB) == BigInteger("9000000000900000000090"), "Большой НОД")
        for (i in 0..1000) {
            val value = number(i)
            val root = NumberTheory.sqrtFloor(value)
            verify(root * root <= value && (root + BigInteger.ONE) * (root + BigInteger.ONE) > value, "Корень $i")
        }
        for (i in 0 until 100) {
            val root = BigInteger(512, random)
            val square = root * root
            verify(NumberTheory.sqrtFloor(square) == root, "Большой квадрат")
            verify(NumberTheory.sqrtFloor(square - BigInteger.ONE) == root - BigInteger.ONE, "Перед квадратом")
            verify(NumberTheory.sqrtFloor(square + BigInteger.ONE) == root, "После квадрата")
        }
        verify(NumberTheory.inverse(number(17), number(3120)) == number(2753), "Обратный элемент")
        verify(NumberTheory.inverse(number(-3), number(11)) == number(7), "Отрицательный обратный элемент")
        println("Арифметика и граничные значения: OK")
    }

    private fun symbolsAndFunctions() {
        for (p in 3..150) {
            if (!NumberTheory.isPrimeByTrialDivision(number(p))) continue
            for (a in -p..p) {
                var expected = -1
                val residue = (a % p + p) % p
                if (residue == 0) expected = 0
                else {
                    for (x in 1 until p) if (x * x % p == residue) expected = 1
                }
                verify(NumberTheory.legendre(number(a), number(p)) == expected, "Лежандр $a/$p")
            }
        }
        for (n in 1..151 step 2) {
            for (a in -30..30) {
                var expected = 1
                for (factor in NumberTheory.factorize(number(n))) {
                    for (i in 0 until factor.exponent) expected *= NumberTheory.legendre(number(a), factor.prime)
                }
                verify(NumberTheory.jacobi(number(a), number(n)) == expected, "Якоби $a/$n")
            }
        }
        for (n in 1..250) {
            var expectedPhi = 0
            for (k in 1..n) if (bruteGcd(k, n) == 1) expectedPhi++
            verify(NumberTheory.eulerByDefinition(number(n)) == number(expectedPhi), "Определение phi($n)")
            verify(NumberTheory.eulerByFactorization(number(n)) == number(expectedPhi), "Разложение phi($n)")
            verify(NumberTheory.eulerByFourier(n) == number(expectedPhi), "Фурье phi($n)")
            var expectedLambda = 1
            while (true) {
                var allOne = true
                for (a in 1..n) {
                    if (bruteGcd(a, n) == 1 && brutePower(a, expectedLambda, n) != 1 % n) {
                        allOne = false
                        break
                    }
                }
                if (allOne) break
                expectedLambda++
            }
            verify(NumberTheory.carmichael(number(n)) == number(expectedLambda), "Кармайкл $n")
        }
        for (n in intArrayOf(997, 1024, 9999, 10000, 99991, 100000, 999983, 1000000)) {
            verify(NumberTheory.eulerByFourier(n) == NumberTheory.eulerByFactorization(number(n)), "Точность Фурье $n")
        }
        println("Символы, три способа вычисления phi и функция Кармайкла: OK")
    }

    private fun primality() {
        val fermat = FermatTest()
        val solovay = SolovayStrassenTest()
        val miller = MillerRabinTest()
        val tests = arrayOf(fermat, solovay, miller)
        verify(miller.iterationCount(0.5) == 1, "Одна итерация МР")
        verify(miller.iterationCount(0.99) == 4, "Четыре итерации МР")
        verify(solovay.iterationCount(0.99) == 7, "Семь итераций СШ")
        verify(fermat.iterationCount(0.99) == 7, "Число итераций Ферма по принятому правилу")
        verify(miller.iterationCount(Math.nextDown(1.0)) == 27, "Вероятность около 1")
        for (n in -5..500) {
            val expected = NumberTheory.isPrimeByTrialDivision(number(n))
            for (test in tests) {
                verify(test.isProbablePrime(number(n), 0.999999999999999) == expected, "Тест простоты $n")
            }
        }
        for (n in 5..199 step 2) {
            val prime = NumberTheory.isPrimeByTrialDivision(number(n))
            for (test in tests) {
                var witnessFound = false
                for (base in 2..n - 2) {
                    val passed = test.testIteration(number(n), number(base))
                    if (!passed) witnessFound = true
                    if (prime) verify(passed, "Простое $n, основание $base")
                }
                if (!prime) verify(witnessFound, "Свидетель составности $n")
            }
        }
        verify(fermat.testIteration(number(561), number(2)), "Ферма пропускает 561 при a=2")
        verify(!miller.testIteration(number(561), number(2)), "МР отклоняет 561 при a=2")

        for (base in 2..559) {
            val coprime = bruteGcd(base, 561) == 1
            verify(fermat.testIteration(number(561), number(base)) == coprime,
                "Чистый Ферма: число Кармайкла 561, основание $base")
        }
        verify(miller.testIteration(number(2047), number(2)), "Сильное псевдопростое 2047")
        verify(!miller.testIteration(number(2047), number(3)), "Свидетель 3 для 2047")
        verify(!solovay.testIteration(number(561), number(5)), "Свидетель СШ для 561")
        println("Тесты простоты, свидетели и числа Кармайкла: OK")
    }

    private fun rsaAndAttacks() {
        val probability = 0.999999999999999
        var lastRsa: RsaService? = null
        for (type in RsaService.TestType.entries) {
            val rsa = RsaService(type, probability, 16)
            lastRsa = rsa
            for (generation in 1..3) {
                if (generation > 1) rsa.generateNewKeyPair()
                val pair = rsa.keyPair
                verify(pair.p.bitLength() == 16 && pair.q.bitLength() == 16, "Битовая длина")
                verify(NumberTheory.isPrimeByTrialDivision(pair.p) && NumberTheory.isPrimeByTrialDivision(pair.q), "Точная проверка p,q")
                verify(pair.p != pair.q && pair.p * pair.q == pair.publicKey.n, "Модуль")
                verify(pair.publicKey.e * pair.privateKey.d % pair.phi == BigInteger.ONE, "ed mod phi")
                verify(pair.privateKey.d > NumberTheory.sqrtFloor(pair.publicKey.n), "Большое d")
                verify(rsa.keyGenerator.fermatSteps(pair.p, pair.q) >= rsa.keyGenerator.minimumFermatSteps(), "Расстояние Ферма")
                verify(WienerAttack.attack(pair.publicKey).privateExponent == null, "Защита от Винера")
                for (message in arrayOf(BigInteger.ZERO, BigInteger.ONE, pair.p, pair.q, pair.publicKey.n - BigInteger.ONE)) {
                    verify(rsa.decrypt(rsa.encrypt(message)) == message, "RSA, включая невзаимно простые сообщения")
                }
                for (length in 0..33) {
                    val data = ByteArray(length)
                    random.nextBytes(data)
                    if (length > 0) data[0] = 0
                    if (length > 1) data[length - 1] = 0
                    verify(rsa.decrypt(rsa.encrypt(data)).contentEquals(data), "Байты, длина $length")
                }
                val allBytes = ByteArray(256)
                for (i in allBytes.indices) allBytes[i] = i.toByte()
                verify(rsa.decrypt(rsa.encrypt(allBytes)).contentEquals(allBytes), "Все значения байтов")
            }
        }
        val rsa = lastRsa!!
        verify(rsa.encrypt(number(65), RsaPublicKey(number(3233), number(17))) == number(2790), "Учебный вектор RSA")
        verify(rsa.decrypt(number(2790), RsaPrivateKey(number(3233), number(2753))) == number(65), "Обратный вектор RSA")
        val knownAttack = WienerAttack.attack(RsaPublicKey(number(90581), number(17993)))
        verify(knownAttack.privateExponent == number(5) && knownAttack.phi == number(89964), "Известный пример Винера")
        val coefficients = WienerAttack.continuedFraction(number(415), number(93))
        verify(coefficients == listOf(number(4), number(2), number(6), number(7)), "Цепная дробь")
        val fractions = WienerAttack.convergents(coefficients)
        verify(fractions == listOf(Convergent(number(4), number(1)), Convergent(number(9), number(2)),
            Convergent(number(58), number(13)), Convergent(number(415), number(93))), "Подходящие дроби")
        for (bits in intArrayOf(16, 32, 64, 128)) {
            val generator = VulnerableKeyGenerator(bits, probability)
            for (i in 1..5) {
                val pair = generator.generate()
                val recovered = WienerAttack.attack(pair.publicKey)
                verify(recovered.privateExponent == pair.privateKey.d && recovered.phi == pair.phi, "Винер $bits бит")
                val threeD = pair.privateKey.d * number(3)
                verify(threeD * threeD * threeD * threeD < pair.publicKey.n, "Граница Винера")
                verify(recovered.convergents.last().numerator * pair.publicKey.n ==
                    recovered.convergents.last().denominator * pair.publicKey.e, "Последняя дробь")
                val encrypted = rsa.encrypt(number(42), pair.publicKey)
                verify(rsa.decrypt(encrypted, RsaPrivateKey(pair.publicKey.n, recovered.privateExponent!!)) == number(42), "Атака расшифровывает")
            }
        }
        for (bits in intArrayOf(32, 64, 128, 256)) {
            val largeRsa = RsaService(RsaService.TestType.MILLER_RABIN, probability, bits)
            val oldPair = largeRsa.keyPair
            val original = ByteArray(1024)
            random.nextBytes(original)
            val encrypted = largeRsa.encrypt(original)
            verify(largeRsa.decrypt(encrypted).contentEquals(original), "Многоблочное RSA $bits")
            largeRsa.generateNewKeyPair()
            verify(largeRsa.decrypt(encrypted, oldPair.privateKey).contentEquals(original), "Сохранённый старый ключ $bits")
            verify(largeRsa.decrypt(largeRsa.encrypt(original)).contentEquals(original), "Новый ключ $bits")
        }
        println("RSA, повторная генерация, форматы блоков и атака Винера: OK")
    }

    private fun invalidArguments() {

        expectInvalid { NumberTheory.modPow(number(2), number(-1), number(3)) }
        expectInvalid { NumberTheory.modPow(number(2), number(3), number(0)) }
        expectInvalid { NumberTheory.inverse(number(2), number(4)) }
        expectInvalid { NumberTheory.legendre(number(2), number(9)) }
        expectInvalid { NumberTheory.jacobi(number(2), number(0)) }
        expectInvalid { NumberTheory.jacobi(number(2), number(4)) }
        expectInvalid { NumberTheory.eulerByDefinition(number(0)) }
        expectInvalid { NumberTheory.eulerByFactorization(number(-1)) }
        expectInvalid { NumberTheory.eulerByFourier(0) }
        expectInvalid { NumberTheory.eulerByFourier(1000001) }
        expectInvalid { NumberTheory.carmichael(number(0)) }
        expectInvalid { NumberTheory.sqrtFloor(number(-1)) }
        for (p in doubleArrayOf(0.49, 1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            expectInvalid { MillerRabinTest().isProbablePrime(number(2), p) }
            expectInvalid { RsaService.KeyGenerator(RsaService.TestType.FERMAT, p, 16) }
        }
        expectInvalid { RsaService.KeyGenerator(RsaService.TestType.MILLER_RABIN, 0.99, 15) }
        expectInvalid { WienerAttack.attack(RsaPublicKey(number(10), number(1))) }
        val rsa = RsaService(RsaService.TestType.MILLER_RABIN, 0.999999999999999, 16)
        expectInvalid { rsa.encrypt(number(-1)) }
        expectInvalid { rsa.encrypt(rsa.keyPair.publicKey.n) }
        expectInvalid { rsa.decrypt(rsa.keyPair.publicKey.n) }
        expectInvalid { rsa.decrypt(byteArrayOf(1)) }
        val blockSize = (rsa.keyPair.publicKey.n.bitLength() + 7) / 8
        expectInvalid { rsa.decrypt(ByteArray(blockSize)) }
        val nBlock = rsa.keyPair.publicKey.n.toByteArray().takeLast(blockSize).toByteArray()
        expectInvalid { rsa.decrypt(nBlock) }
        expectInvalid { rsa.encrypt(ByteArray(0), RsaPublicKey(rsa.keyPair.publicKey.n, number(0))) }
        expectInvalid { rsa.decrypt(ByteArray(0), RsaPrivateKey(rsa.keyPair.publicKey.n, number(0))) }
        val larger = RsaService(RsaService.TestType.MILLER_RABIN, 0.999999999999999, 32)
        val shortBlock = larger.encrypt(byteArrayOf(7))
        expectInvalid { larger.decrypt(shortBlock + shortBlock) }
        println("Неверные аргументы и повреждённые блоки: OK")
    }

    private fun expectInvalid(action: () -> Unit) {
        try {
            action()
        } catch (error: IllegalArgumentException) {
            checks++
            return
        }
        error("Ожидалась IllegalArgumentException")
    }
}
