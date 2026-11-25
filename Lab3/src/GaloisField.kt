import java.math.BigInteger
import java.util.TreeMap

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
        val factors = TreeMap<BigInteger, Int>()
        factor(polynomial, factors)
        val result = ArrayList<PolynomialFactor>()
        for ((value, exponent) in factors) result.add(PolynomialFactor(value, exponent))
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

    private fun remainder(value: BigInteger, divisor: BigInteger): BigInteger {
        var result = value
        while (result != BigInteger.ZERO && result.bitLength() >= divisor.bitLength()) {
            result = result.xor(divisor.shiftLeft(result.bitLength() - divisor.bitLength()))
        }
        return result
    }

    private fun divide(value: BigInteger, divisor: BigInteger): BigInteger {
        var rest = value
        var quotient = BigInteger.ZERO
        while (rest != BigInteger.ZERO && rest.bitLength() >= divisor.bitLength()) {
            val shift = rest.bitLength() - divisor.bitLength()
            quotient = quotient.setBit(shift)
            rest = rest.xor(divisor.shiftLeft(shift))
        }
        check(rest == BigInteger.ZERO) { "Полиномы должны делиться без остатка" }
        return quotient
    }

    private fun gcd(a: BigInteger, b: BigInteger): BigInteger {
        var left = a
        var right = b
        while (right != BigInteger.ZERO) {
            val next = remainder(left, right)
            left = right
            right = next
        }
        return left
    }

    private fun derivative(polynomial: BigInteger): BigInteger {
        var result = BigInteger.ZERO
        for (i in 1 until polynomial.bitLength() step 2) {
            if (polynomial.testBit(i)) result = result.setBit(i - 1)
        }
        return result
    }

    private fun squareRoot(polynomial: BigInteger): BigInteger {
        var result = BigInteger.ZERO
        for (i in 0 until polynomial.bitLength() step 2) {
            if (polynomial.testBit(i)) result = result.setBit(i / 2)
        }
        return result
    }

    private fun factor(polynomial: BigInteger, factors: MutableMap<BigInteger, Int>) {
        if (polynomial == BigInteger.ONE) return
        if (polynomial.bitLength() == 2) {
            factors[polynomial] = (factors[polynomial] ?: 0) + 1
            return
        }
        val derivative = derivative(polynomial)
        if (derivative == BigInteger.ZERO) {
            // нулевая производная означает, что все степени чётные: извлекаем квадратный корень и удваиваем кратности
            val rootFactors = TreeMap<BigInteger, Int>()
            factor(squareRoot(polynomial), rootFactors)
            for ((value, exponent) in rootFactors) {
                factors[value] = (factors[value] ?: 0) + 2 * exponent
            }
            return
        }
        val repeated = gcd(polynomial, derivative)
        if (repeated != BigInteger.ONE) {
            factor(repeated, factors)
            factor(divide(polynomial, repeated), factors)
            return
        }
        val basis = berlekampBasis(polynomial)
        if (basis.size == 1) {
            factors[polynomial] = (factors[polynomial] ?: 0) + 1
            return
        }
        for (value in basis) {
            if (value == BigInteger.ONE) continue
            val divisor = gcd(polynomial, value)
            if (divisor != BigInteger.ONE && divisor != polynomial) {
                factor(divisor, factors)
                factor(divide(polynomial, divisor), factors)
                return
            }
        }
        error("Не удалось разделить полином по базису берлекэмпа")
    }

    private fun berlekampBasis(polynomial: BigInteger): List<BigInteger> {
        // ищем полиномы h, для которых h^2 = h по модулю исходного полинома, решая двоичную систему (q - i)h = 0
        val degree = polynomial.bitLength() - 1
        val rows = Array(degree) { BigInteger.ZERO }
        var column = BigInteger.ONE
        for (j in 0 until degree) {
            for (i in 0 until degree) {
                if (column.testBit(i)) rows[i] = rows[i].setBit(j)
            }
            rows[j] = rows[j].flipBit(j)
            column = remainder(column.shiftLeft(2), polynomial)
        }
        val pivotColumns = ArrayList<Int>()
        // исключаем неизвестные методом гаусса; над gf(2) вычитание строк заменяется xor
        var pivotRow = 0
        for (j in 0 until degree) {
            var found = pivotRow
            while (found < degree && !rows[found].testBit(j)) found++
            if (found == degree) continue
            val temporary = rows[pivotRow]
            rows[pivotRow] = rows[found]
            rows[found] = temporary
            for (i in 0 until degree) {
                if (i != pivotRow && rows[i].testBit(j)) rows[i] = rows[i].xor(rows[pivotRow])
            }
            pivotColumns.add(j)
            pivotRow++
        }
        val basis = ArrayList<BigInteger>()
        for (j in 0 until degree) {
            if (j in pivotColumns) continue
            var vector = BigInteger.ONE.shiftLeft(j)
            for (i in pivotColumns.indices) {
                if (rows[i].testBit(j)) vector = vector.setBit(pivotColumns[i])
            }
            basis.add(vector)
        }
        return basis
    }
}
