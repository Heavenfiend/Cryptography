import java.math.BigInteger

fun main(args: Array<String>) {
    try {
        var bitLength = 128
        var probability = 0.999999999999
        var type = RsaService.TestType.MILLER_RABIN
        var message = "Вторая лабораторная по криптографии. Kotlin и RSA."
        require(args.size <= 4) { "Параметры: биты вероятность тест текст" }
        if (args.isNotEmpty()) bitLength = args[0].toInt()
        if (args.size > 1) probability = args[1].toDouble()
        if (args.size > 2) type = RsaService.TestType.valueOf(args[2].uppercase())
        if (args.size > 3) message = args[3]
        require(bitLength >= 16 && probability >= 0.5 && probability < 1.0) { "Нужны биты >= 16 и вероятность в [0.5, 1)" }
        demonstrateArithmetic()
        demonstratePrimality(probability)
        demonstrateRsa(bitLength, probability, type, message)
        demonstrateWiener(bitLength, probability)
    } catch (error: IllegalArgumentException) {
        System.err.println("Ошибка: ${error.message}")
        kotlin.system.exitProcess(1)
    }
}

private fun demonstrateArithmetic() {
    println("1. Арифметический сервис")
    val a = BigInteger.valueOf(240)
    val b = BigInteger.valueOf(46)
    val bezout = NumberTheory.extendedGcd(a, b)
    println("НОД($a, $b): ${NumberTheory.gcd(a, b)}")
    println("Бинарный НОД: ${NumberTheory.binaryGcd(a, b)}")
    println("Безу: $a * (${bezout.x}) + $b * (${bezout.y}) = ${bezout.gcd}")
    println("Символ Лежандра (5/11): ${NumberTheory.legendre(BigInteger.valueOf(5), BigInteger.valueOf(11))}")
    println("Символ Якоби (5/21): ${NumberTheory.jacobi(BigInteger.valueOf(5), BigInteger.valueOf(21))}")
    println("7^128 mod 13: ${NumberTheory.modPow(BigInteger.valueOf(7), BigInteger.valueOf(128), BigInteger.valueOf(13))}")
    println("n | Эйлер: определение / разложение / Фурье | Кармайкл")
    for (n in intArrayOf(1, 8, 9, 12, 36, 97)) {
        val number = BigInteger.valueOf(n.toLong())
        val first = NumberTheory.eulerByDefinition(number)
        val second = NumberTheory.eulerByFactorization(number)
        val third = NumberTheory.eulerByFourier(n)
        check(first == second && second == third)
        println("$n | $first / $second / $third | ${NumberTheory.carmichael(number)}")
    }
}

private fun demonstratePrimality(probability: Double) {
    println("\n2. Тесты простоты")
    val tests = arrayOf(FermatTest(), SolovayStrassenTest(), MillerRabinTest())
    val names = arrayOf("Ферма + подтверждение МР", "Соловей–Штрассен", "Миллер–Рабин")
    for (i in tests.indices) {
        print("${names[i]} (итераций: ${tests[i].iterationCount(probability)}): ")
        for (value in intArrayOf(1, 2, 17, 21, 561, 1105, 7919)) {
            val result = tests[i].isProbablePrime(BigInteger.valueOf(value.toLong()), probability)
            print("$value=$result ")
        }
        println()
    }
    val carmichael = BigInteger.valueOf(561)
    val base = BigInteger.TWO
    println("561 составное, но итерация Ферма при основании 2: ${FermatTest().testIteration(carmichael, base)}")
    println("Для этого основания итерация Миллера–Рабина: ${MillerRabinTest().testIteration(carmichael, base)}")
}

private fun demonstrateRsa(bits: Int, probability: Double, type: RsaService.TestType, message: String) {
    println("\n3. RSA: выбран $type, кандидаты по $bits бит")
    val rsa = RsaService(type, probability, bits)
    for (generation in 1..2) {
        if (generation > 1) rsa.generateNewKeyPair()
        val pair = rsa.keyPair
        println("Ключевая пара $generation: n=${pair.publicKey.n}, e=${pair.publicKey.e}")
        val original = message.toByteArray(Charsets.UTF_8)
        val encrypted = rsa.encrypt(original)
        val restored = rsa.decrypt(encrypted)
        check(original.contentEquals(restored))
        println("Шифротекст (hex): ${hex(encrypted)}")
        println("Восстановленный текст: ${String(restored, Charsets.UTF_8)}")
        val binary = byteArrayOf(0, 0, -1, -128, 1, 0, 0)
        check(rsa.decrypt(rsa.encrypt(binary)).contentEquals(binary))
        check(rsa.decrypt(rsa.encrypt(ByteArray(0))).isEmpty())
        println("Текст, пустой массив и двоичные данные: восстановлены побайтно")
        println("Приращений в методе Ферма до множителей: ${rsa.keyGenerator.fermatSteps(pair.p, pair.q)}")
        val attack = WienerAttack.attack(pair.publicKey)
        check(attack.privateExponent == null)
        println("Атака Винера на эту пару: d не найдено")
    }
}

private fun demonstrateWiener(bits: Int, probability: Double) {
    println("\n4. Атака Винера на сгенерированные уязвимые ключи")
    val generator = VulnerableKeyGenerator(bits, probability)
    for (i in 1..3) {
        val pair = generator.generate()
        val result = WienerAttack.attack(pair.publicKey)
        check(result.privateExponent == pair.privateKey.d)
        check(result.phi == pair.phi)
        println("Пример $i: n=${pair.publicKey.n}, e=${pair.publicKey.e}")
        println("Исходное d=${pair.privateKey.d}; найденное d=${result.privateExponent}")
        println("Найденное phi(n)=${result.phi}")
        println("Подходящие дроби k/d для e/n:")
        for (fraction in result.convergents) println("  ${fraction.numerator}/${fraction.denominator}")
        val message = BigInteger.valueOf(42)
        val ciphertext = NumberTheory.modPow(message, pair.publicKey.e, pair.publicKey.n)
        val recovered = NumberTheory.modPow(ciphertext, result.privateExponent, pair.publicKey.n)
        check(recovered == message)
        println("Сообщение $message после расшифрования найденным d: $recovered")
    }
}

private fun hex(data: ByteArray): String {
    val digits = "0123456789ABCDEF"
    val result = StringBuilder()
    for (byte in data) {
        val value = byte.toInt() and 255
        result.append(digits[value ushr 4])
        result.append(digits[value and 15])
    }
    return result.toString()
}
