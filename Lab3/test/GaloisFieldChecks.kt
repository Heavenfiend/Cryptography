import java.math.BigInteger
import java.util.Random

object GaloisFieldChecks {
    private var checks = 0

    fun run(): Int {
        checks = 0
        val moduli = GaloisField.irreducibleModuli()
        verify(moduli.size == 30, "Количество неприводимых модулей")
        verify(moduli.distinct().size == 30, "Повторы модулей")
        for (m in 0..255) {
            verify(GaloisField.isIrreducible(m.toByte()) == referenceIrreducible(BigInteger.valueOf((256 or m).toLong())), "Неприводимость $m")
        }
        verify(GaloisField.add(0x57, 0x83.toByte()) == 0xD4.toByte(), "Сложение")
        verify(GaloisField.multiply(0x57, 0x83.toByte(), 0x1B) == 0xC1.toByte(), "Умножение из примера aes")
        verify(GaloisField.inverse(0x53, 0x1B) == 0xCA.toByte(), "Обратный элемент из примера aes")
        val random = Random(20261114)
        for (modulus in moduli) {
            for (a in 1..255) {
                val inverse = GaloisField.inverse(a.toByte(), modulus)
                verify(GaloisField.multiply(a.toByte(), inverse, modulus) == 1.toByte(), "Обратный элемент $a/$modulus")
            }
            for (i in 0 until 400) {
                val a = random.nextInt(256)
                val b = random.nextInt(256)
                val c = random.nextInt(256)
                val product = GaloisField.multiply(a.toByte(), b.toByte(), modulus)
                verify((product.toInt() and 255) == referenceProduct(a, b, modulus.toInt() and 255), "Произведение")
                val left = GaloisField.multiply(a.toByte(), GaloisField.add(b.toByte(), c.toByte()), modulus)
                val right = GaloisField.add(product, GaloisField.multiply(a.toByte(), c.toByte(), modulus))
                verify(left == right, "Распределительный закон")
            }
        }
        for (m in 0..255) {
            if (!GaloisField.isIrreducible(m.toByte())) {
                expectInvalid { GaloisField.multiply(0, 0, m.toByte()) }
                expectInvalid { GaloisField.inverse(1, m.toByte()) }
            }
        }
        expectInvalid { GaloisField.inverse(0, 0x1B) }
        expectInvalid { GaloisField.factorize(BigInteger.ZERO) }
        expectInvalid { GaloisField.factorize(BigInteger.valueOf(-1)) }
        verify(GaloisField.factorize(BigInteger.ONE).isEmpty(), "Разложение единицы")
        for (polynomial in 2..1023) checkFactorization(BigInteger.valueOf(polynomial.toLong()))
        for (i in 0 until 80) {
            checkFactorization(BigInteger.valueOf((random.nextInt(1 shl 19) or (1 shl 19)).toLong()))
        }
        val irreducible = BigInteger.valueOf(0x100009)
        verify(referenceIrreducible(irreducible), "Независимая проверка полинома степени 20")
        verify(GaloisField.factorize(irreducible) == listOf(PolynomialFactor(irreducible, 1)), "Неприводимый полином степени 20")
        val factorX = BigInteger.TWO
        val factorXPlusOne = BigInteger.valueOf(3)
        var largeComposite = irreducible.shiftLeft(64)
        for (i in 1..45) largeComposite = multiplyPolynomials(largeComposite, factorXPlusOne)
        val expected = listOf(PolynomialFactor(factorX, 64), PolynomialFactor(factorXPlusOne, 45), PolynomialFactor(irreducible, 1))
        verify(GaloisField.factorize(largeComposite) == expected, "Разные множители и кратности у полинома степени 129")
        var repeated = BigInteger.ONE
        for (i in 1..65) repeated = multiplyPolynomials(repeated, BigInteger.valueOf(7))
        verify(GaloisField.factorize(repeated) == listOf(PolynomialFactor(BigInteger.valueOf(7), 65)), "Кратность 65 у полинома степени 130")
        return checks
    }

    private fun verify(condition: Boolean, message: String) {
        check(condition) { message }
        checks++
    }

    private fun expectInvalid(action: () -> Unit) {
        try {
            action()
        } catch (error: IllegalArgumentException) {
            checks++
            return
        }
        error("Ожидалось исключение")
    }

    private fun referenceProduct(a: Int, b: Int, modulus: Int): Int {
        var product = 0
        for (i in 0..7) {
            if ((b and (1 shl i)) != 0) product = product xor (a shl i)
        }
        for (i in 14 downTo 8) {
            if ((product and (1 shl i)) != 0) product = product xor ((256 or modulus) shl (i - 8))
        }
        return product
    }

    private fun multiplyPolynomials(a: BigInteger, b: BigInteger): BigInteger {
        var result = BigInteger.ZERO
        for (i in 0 until b.bitLength()) {
            if (b.testBit(i)) result = result.xor(a.shiftLeft(i))
        }
        return result
    }

    private fun remainder(a: BigInteger, b: BigInteger): BigInteger {
        var rest = a
        while (rest != BigInteger.ZERO && rest.bitLength() >= b.bitLength()) {
            rest = rest.xor(b.shiftLeft(rest.bitLength() - b.bitLength()))
        }
        return rest
    }

    private fun gcd(a: BigInteger, b: BigInteger): BigInteger {
        var x = a
        var y = b
        while (y != BigInteger.ZERO) {
            val rest = remainder(x, y)
            x = y
            y = rest
        }
        return x
    }

    private fun referenceIrreducible(polynomial: BigInteger): Boolean {
        val degree = polynomial.bitLength() - 1
        if (degree <= 0) return false
        if (degree == 1) return true
        val x = BigInteger.TWO
        var power = x
        for (i in 1..degree) {
            power = remainder(multiplyPolynomials(power, power), polynomial)
            if (i <= degree / 2 && gcd(power.xor(x), polynomial) != BigInteger.ONE) return false
        }
        return power == x
    }

    private fun checkFactorization(polynomial: BigInteger) {
        val factors = GaloisField.factorize(polynomial)
        var restored = BigInteger.ONE
        var previous = BigInteger.ZERO
        for (factor in factors) {
            verify(factor.polynomial > previous, "Порядок и отсутствие повторов множителей")
            verify(factor.exponent > 0, "Положительная кратность")
            verify(referenceIrreducible(factor.polynomial), "Неприводимость множителя")
            for (i in 1..factor.exponent) restored = multiplyPolynomials(restored, factor.polynomial)
            previous = factor.polynomial
        }
        verify(restored == polynomial, "Восстановление полинома после разложения")
    }
}
