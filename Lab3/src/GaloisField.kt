import java.math.BigInteger

data class PolynomialFactor(val polynomial: BigInteger, val exponent: Int)

object GaloisField {
    fun add(a: Byte, b: Byte): Byte {
        return (a.toInt() xor b.toInt()).toByte()
    }

    fun multiply(a: Byte, b: Byte, modulus: Byte): Byte {
        require(isIrreducible(modulus)) { "Модуль должен быть неприводимым полиномом степени 8" }
        return multiplyUnchecked(a.toInt() and 255, b.toInt() and 255, modulus.toInt() and 255).toByte()
    }

    fun inverse(value: Byte, modulus: Byte): Byte {
        require(isIrreducible(modulus)) { "Модуль должен быть неприводимым полиномом степени 8" }
        require(value != 0.toByte()) { "У нуля нет обратного элемента" }
        // у ненулевых элементов поля a^255 = 1, поэтому обратный элемент равен a^254
        var power = 254
        var current = value.toInt() and 255
        var result = 1
        while (power > 0) {
            if ((power and 1) != 0) result = multiplyUnchecked(result, current, modulus.toInt() and 255)
            current = multiplyUnchecked(current, current, modulus.toInt() and 255)
            power = power ushr 1
        }
        return result.toByte()
    }

    fun isIrreducible(modulus: Byte): Boolean {
        // старший коэффициент x^8 подразумевается; у приводимого полинома степени 8 есть делитель степени не больше 4
        val polynomial = 256 or (modulus.toInt() and 255)
        if ((polynomial and 1) == 0) return false
        for (divisor in 3..31 step 2) {
            if (remainder(polynomial, divisor) == 0) return false
        }
        return true
    }

    fun irreducibleModuli(): List<Byte> {
        val result = ArrayList<Byte>()
        for (value in 0..255) {
            if (isIrreducible(value.toByte())) result.add(value.toByte())
        }
        return result
    }

    fun factorize(polynomial: BigInteger): List<PolynomialFactor> {
        require(polynomial > BigInteger.ZERO) { "Для разложения нужен ненулевой неотрицательный полином" }
        val result = ArrayList<PolynomialFactor>()
        var remaining = polynomial
        var divisor = BigInteger.TWO
        // перебираем полиномы от меньших степеней к большим, пока у остатка ещё может быть делитель
        while (divisor.bitLength() - 1 <= (remaining.bitLength() - 1) / 2) {
            var exponent = 0
            while (true) {
                val quotient = divideExactly(remaining, divisor) ?: break
                remaining = quotient
                exponent++
            }
            if (exponent > 0) result.add(PolynomialFactor(divisor, exponent))
            divisor += BigInteger.ONE
        }
        if (remaining != BigInteger.ONE) result.add(PolynomialFactor(remaining, 1))
        return result
    }

    private fun multiplyUnchecked(a: Int, b: Int, modulus: Int): Int {
        var left = a
        var right = b
        var result = 0
        while (right != 0) {
            if ((right and 1) != 0) result = result xor left
            val highBit = left and 128
            left = (left shl 1) and 255
            if (highBit != 0) left = left xor modulus
            right = right ushr 1
        }
        return result
    }

    private fun remainder(value: Int, divisor: Int): Int {
        var result = value
        val divisorDegree = 31 - Integer.numberOfLeadingZeros(divisor)
        while (result != 0) {
            val shift = 31 - Integer.numberOfLeadingZeros(result) - divisorDegree
            if (shift < 0) break
            result = result xor (divisor shl shift)
        }
        return result
    }

    // делим полиномы столбиком, сокращая старший член сдвигом и xor
    private fun divideExactly(value: BigInteger, divisor: BigInteger): BigInteger? {
        var rest = value
        var quotient = BigInteger.ZERO
        while (rest != BigInteger.ZERO && rest.bitLength() >= divisor.bitLength()) {
            val shift = rest.bitLength() - divisor.bitLength()
            quotient = quotient.setBit(shift)
            rest = rest.xor(divisor.shiftLeft(shift))
        }
        return if (rest == BigInteger.ZERO) quotient else null
    }
}
