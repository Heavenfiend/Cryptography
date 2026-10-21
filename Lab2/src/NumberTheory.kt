import java.math.BigInteger
import kotlin.math.PI
import kotlin.math.round

data class BezoutResult(val gcd: BigInteger, val x: BigInteger, val y: BigInteger)
data class PrimeFactor(val prime: BigInteger, val exponent: Int)

// Сервис не хранит изменяемого состояния: все промежуточные значения локальны.
object NumberTheory {
    fun gcd(a: BigInteger, b: BigInteger): BigInteger {
        var x = a.abs()
        var y = b.abs()
        while (y != BigInteger.ZERO) {
            val remainder = x % y
            x = y
            y = remainder
        }
        return x
    }

    fun extendedGcd(a: BigInteger, b: BigInteger): BezoutResult {
        var oldR = a.abs()
        var r = b.abs()
        var oldX = BigInteger.ONE
        var x = BigInteger.ZERO
        var oldY = BigInteger.ZERO
        var y = BigInteger.ONE
        while (r != BigInteger.ZERO) {
            val quotient = oldR / r
            val nextR = oldR - quotient * r
            val nextX = oldX - quotient * x
            val nextY = oldY - quotient * y
            oldR = r
            r = nextR
            oldX = x
            x = nextX
            oldY = y
            y = nextY
        }
        if (a.signum() < 0) oldX = -oldX
        if (b.signum() < 0) oldY = -oldY
        return BezoutResult(oldR, oldX, oldY)
    }

    fun binaryGcd(a: BigInteger, b: BigInteger): BigInteger {
        var x = a.abs()
        var y = b.abs()
        if (x == BigInteger.ZERO) return y
        if (y == BigInteger.ZERO) return x
        var commonTwos = 0
        while (!x.testBit(0) && !y.testBit(0)) {
            x = x.shiftRight(1)
            y = y.shiftRight(1)
            commonTwos++
        }
        while (!x.testBit(0)) x = x.shiftRight(1)
        while (y != BigInteger.ZERO) {
            while (!y.testBit(0)) y = y.shiftRight(1)
            if (x > y) {
                val temporary = x
                x = y
                y = temporary
            }
            y -= x
        }
        return x.shiftLeft(commonTwos)
    }

    fun normalize(value: BigInteger, modulus: BigInteger): BigInteger {
        require(modulus > BigInteger.ZERO) { "Модуль должен быть положительным" }
        var result = value % modulus
        if (result.signum() < 0) result += modulus
        return result
    }

    fun modPow(base: BigInteger, exponent: BigInteger, modulus: BigInteger): BigInteger {
        require(exponent.signum() >= 0) { "Показатель степени не может быть отрицательным" }
        require(modulus > BigInteger.ZERO) { "Модуль должен быть положительным" }
        var power = exponent
        var current = normalize(base, modulus)
        var result = BigInteger.ONE % modulus
        while (power > BigInteger.ZERO) {
            if (power.testBit(0)) result = result * current % modulus
            current = current * current % modulus
            power = power.shiftRight(1)
        }
        return result
    }

    fun inverse(value: BigInteger, modulus: BigInteger): BigInteger {
        require(modulus > BigInteger.ONE) { "Модуль должен быть больше 1" }
        val result = extendedGcd(value, modulus)
        require(result.gcd == BigInteger.ONE) { "Обратного элемента не существует" }
        return normalize(result.x, modulus)
    }

    fun legendre(a: BigInteger, p: BigInteger): Int {
        require(p > BigInteger.TWO && p.testBit(0) && isPrimeByTrialDivision(p)) {
            "Знаменатель символа Лежандра должен быть нечётным простым числом"
        }
        val result = modPow(a, (p - BigInteger.ONE) / BigInteger.TWO, p)
        if (result == BigInteger.ZERO) return 0
        if (result == BigInteger.ONE) return 1
        return -1
    }

    fun jacobi(a: BigInteger, n: BigInteger): Int {
        require(n > BigInteger.ZERO && n.testBit(0)) { "Знаменатель Якоби должен быть положительным и нечётным" }
        var numerator = normalize(a, n)
        var denominator = n
        var sign = 1
        while (numerator != BigInteger.ZERO) {
            while (!numerator.testBit(0)) {
                numerator = numerator.shiftRight(1)
                val remainder = (denominator % BigInteger.valueOf(8)).toInt()
                if (remainder == 3 || remainder == 5) sign = -sign
            }
            val temporary = numerator
            numerator = denominator
            denominator = temporary
            if (numerator % BigInteger.valueOf(4) == BigInteger.valueOf(3) &&
                denominator % BigInteger.valueOf(4) == BigInteger.valueOf(3)) {
                sign = -sign
            }
            numerator %= denominator
        }
        if (denominator == BigInteger.ONE) return sign
        return 0
    }

    fun factorize(n: BigInteger): List<PrimeFactor> {
        require(n > BigInteger.ZERO) { "Число должно быть положительным" }
        val result = ArrayList<PrimeFactor>()
        var remaining = n
        var divisor = BigInteger.TWO
        while (divisor * divisor <= remaining) {
            var exponent = 0
            while (remaining % divisor == BigInteger.ZERO) {
                remaining /= divisor
                exponent++
            }
            if (exponent > 0) result.add(PrimeFactor(divisor, exponent))
            if (divisor == BigInteger.TWO) divisor = BigInteger.valueOf(3)
            else divisor += BigInteger.TWO
        }
        if (remaining > BigInteger.ONE) result.add(PrimeFactor(remaining, 1))
        return result
    }

    fun eulerByDefinition(n: BigInteger): BigInteger {
        require(n > BigInteger.ZERO) { "Число должно быть положительным" }
        var count = BigInteger.ZERO
        var k = BigInteger.ONE
        while (k <= n) {
            if (gcd(k, n) == BigInteger.ONE) count++
            k++
        }
        return count
    }

    fun eulerByFactorization(n: BigInteger): BigInteger {
        var result = n
        for (factor in factorize(n)) {
            result = result / factor.prime * (factor.prime - BigInteger.ONE)
        }
        return result
    }

    fun eulerByFourier(n: Int): BigInteger {
        // Ограничение удерживает ошибку Double намного ниже половины единицы.
        require(n in 1..1_000_000) { "Для вычисления через Double нужно 1 <= n <= 1 000 000" }
        var sum = 0.0
        var compensation = 0.0
        val number = BigInteger.valueOf(n.toLong())
        for (k in 1..n) {
            val divisor = gcd(BigInteger.valueOf(k.toLong()), number).toDouble()
            val term = divisor * cosine(2.0 * PI * k / n)
            // Суммирование Кэхэна уменьшает потерю точности при сложении.
            val corrected = term - compensation
            val next = sum + corrected
            compensation = (next - sum) - corrected
            sum = next
        }
        return BigInteger.valueOf(round(sum).toLong())
    }

    private fun cosine(angle: Double): Double {
        var x = angle % (2.0 * PI)
        if (x > PI) x -= 2.0 * PI
        var sum = 1.0
        var term = 1.0
        // Ряд Тейлора на [-pi, pi]; 24 слагаемых достаточно для Double.
        for (k in 1..24) {
            term *= -x * x / ((2.0 * k - 1.0) * (2.0 * k))
            sum += term
        }
        return sum
    }

    fun carmichael(n: BigInteger): BigInteger {
        var result = BigInteger.ONE
        for (factor in factorize(n)) {
            var primePower = BigInteger.ONE
            for (i in 1 until factor.exponent) primePower *= factor.prime
            var component = primePower * (factor.prime - BigInteger.ONE)
            if (factor.prime == BigInteger.TWO && factor.exponent >= 3) {
                component /= BigInteger.TWO
            }
            result = result / gcd(result, component) * component
        }
        return result
    }

    fun isPrimeByTrialDivision(n: BigInteger): Boolean {
        if (n < BigInteger.TWO) return false
        if (n == BigInteger.TWO) return true
        if (!n.testBit(0)) return false
        var divisor = BigInteger.valueOf(3)
        while (divisor * divisor <= n) {
            if (n % divisor == BigInteger.ZERO) return false
            divisor += BigInteger.TWO
        }
        return true
    }

    fun sqrtFloor(n: BigInteger): BigInteger {
        require(n.signum() >= 0) { "Корень отрицательного числа не определён" }
        if (n < BigInteger.TWO) return n
        var x = BigInteger.ONE.shiftLeft((n.bitLength() + 1) / 2)
        var next = (x + n / x) / BigInteger.TWO
        while (next < x) {
            x = next
            next = (x + n / x) / BigInteger.TWO
        }
        return x
    }
}
