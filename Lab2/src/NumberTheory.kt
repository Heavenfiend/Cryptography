import java.math.BigInteger
import kotlin.math.PI
import kotlin.math.round

data class BezoutResult(val gcd: BigInteger, val x: BigInteger, val y: BigInteger)
data class PrimeFactor(val prime: BigInteger, val exponent: Int)

object NumberTheory {
    // находит наибольший общий делитель: алгоритм евклида заменяет пару (x, y) на (y, остаток от x / y), пока остаток не станет нулём
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

    // находит нод и коэффициенты безу x и y, для которых a * x + b * y = нод(a, b)
    fun extendedGcd(a: BigInteger, b: BigInteger): BezoutResult {
        var oldR = a.abs()
        var r = b.abs()
        var oldX = BigInteger.ONE
        var x = BigInteger.ZERO
        var oldY = BigInteger.ZERO
        var y = BigInteger.ONE
        while (r != BigInteger.ZERO) {
            val quotient = oldR / r
            // вместе с остатками пересчитываем их коэффициенты тем же вычитанием, чтобы равенство безу сохранялось
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
        // считали для модулей чисел, поэтому возвращаем коэффициентам знаки исходных a и b
        if (a.signum() < 0) oldX = -oldX
        if (b.signum() < 0) oldY = -oldY
        return BezoutResult(oldR, oldX, oldY)
    }

    // бинарный алгоритм евклида находит нод через деление чётных чисел на 2 и вычитание меньшего из большего
    fun binaryGcd(a: BigInteger, b: BigInteger): BigInteger {
        var x = a.abs()
        var y = b.abs()
        if (x == BigInteger.ZERO) return y
        if (y == BigInteger.ZERO) return x
        var commonTwos = 0
        // общие множители 2 временно убираем и считаем, чтобы в конце вернуть их сдвигом влево
        while (!x.testBit(0) && !y.testBit(0)) {
            x = x.shiftRight(1)
            y = y.shiftRight(1)
            commonTwos++
        }
        // после удаления общих двоек хотя бы одно число нечётное; у второго можно убирать двойки без изменения нод
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

    // приводит число к остатку от 0 до modulus - 1: к отрицательному остатку прибавляем модуль
    fun normalize(value: BigInteger, modulus: BigInteger): BigInteger {
        require(modulus > BigInteger.ZERO) { "Модуль должен быть положительным" }
        var result = value % modulus
        if (result.signum() < 0) result += modulus
        return result
    }

    // считает base^exponent по модулю: раскладываем показатель на степени двойки, например 13 = 8 + 4 + 1
    fun modPow(base: BigInteger, exponent: BigInteger, modulus: BigInteger): BigInteger {
        require(exponent.signum() >= 0) { "Показатель степени не может быть отрицательным" }
        require(modulus > BigInteger.ZERO) { "Модуль должен быть положительным" }
        var power = exponent
        var current = normalize(base, modulus)
        var result = BigInteger.ONE % modulus
        while (power > BigInteger.ZERO) {
            // current по очереди хранит степени основания с показателями 1, 2, 4, 8...; единичный бит выбирает нужный множитель
            if (power.testBit(0)) result = result * current % modulus
            // квадрат даёт следующую степень, а сдвиг показателя переводит нас к следующему биту; остатки не дают числам разрастаться
            current = current * current % modulus
            power = power.shiftRight(1)
        }
        return result
    }

    // находит обратное число x: value * x даёт остаток 1 по модулю modulus
    fun inverse(value: BigInteger, modulus: BigInteger): BigInteger {
        require(modulus > BigInteger.ONE) { "Модуль должен быть больше 1" }
        val result = extendedGcd(value, modulus)
        // при нод = 1 равенство безу имеет вид value * x + modulus * y = 1, поэтому его коэффициент x и есть обратное число
        require(result.gcd == BigInteger.ONE) { "Обратного элемента не существует" }
        return normalize(result.x, modulus)
    }

    // символ лежандра равен 0, если a делится на p; иначе 1, если a — квадрат по модулю p, и -1, если такого квадрата нет
    fun legendre(a: BigInteger, p: BigInteger): Int {
        require(p > BigInteger.TWO && p.testBit(0) && isPrimeByTrialDivision(p)) {
            "Знаменатель символа Лежандра должен быть нечётным простым числом"
        }
        // критерий эйлера: для нечётного простого p остаток a^((p - 1) / 2) равен 0, 1 или p - 1; последнее означает -1
        val result = modPow(a, (p - BigInteger.ONE) / BigInteger.TWO, p)
        if (result == BigInteger.ZERO) return 0
        if (result == BigInteger.ONE) return 1
        return -1
    }

    // символ якоби — произведение символов лежандра для простых множителей n с учётом повторений; значение 1 ещё не гарантирует, что a — квадрат по модулю n
    fun jacobi(a: BigInteger, n: BigInteger): Int {
        require(n > BigInteger.ZERO && n.testBit(0)) { "Знаменатель Якоби должен быть положительным и нечётным" }
        var numerator = normalize(a, n)
        var denominator = n
        var sign = 1
        while (numerator != BigInteger.ZERO) {
            // убираем множители 2: каждый меняет знак, только если знаменатель даёт остаток 3 или 5 при делении на 8
            while (!numerator.testBit(0)) {
                numerator = numerator.shiftRight(1)
                val remainder = (denominator % BigInteger.valueOf(8)).toInt()
                if (remainder == 3 || remainder == 5) sign = -sign
            }
            // по закону взаимности меняем числитель и знаменатель местами: знак меняется, если оба дают остаток 3 по модулю 4, затем берём остаток
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

    // раскладывает n на простые множители со степенями: например, 12 = 2^2 * 3; перебираем 2, затем нечётные делители
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
        // если делитель уже больше корня из оставшегося числа, остаток больше 1 может быть только простым
        if (remaining > BigInteger.ONE) result.add(PrimeFactor(remaining, 1))
        return result
    }

    // считает функцию эйлера по определению: сколько чисел от 1 до n взаимно просты с n, то есть имеют с ним нод = 1
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

    // считает функцию эйлера по формуле φ(n) = n * произведение (1 - 1/p), где каждый простой делитель p берётся один раз
    fun eulerByFactorization(n: BigInteger): BigInteger {
        var result = n
        for (factor in factorize(n)) {
            result = result / factor.prime * (factor.prime - BigInteger.ONE)
        }
        return result
    }

    // считает функцию эйлера через сумму фурье: φ(n) = сумма нод(k, n) * cos(2πk/n) для k от 1 до n
    fun eulerByFourier(n: Int): BigInteger {

        require(n in 1..1_000_000) { "Для вычисления через Double нужно 1 <= n <= 1 000 000" }
        var sum = 0.0
        var compensation = 0.0
        val number = BigInteger.valueOf(n.toLong())
        for (k in 1..n) {
            val divisor = gcd(BigInteger.valueOf(k.toLong()), number).toDouble()
            val term = divisor * cosine(2.0 * PI * k / n)

            // при сложении учитываем потерянную точность предыдущего шага, чтобы уменьшить ошибку вычислений с double
            val corrected = term - compensation
            val next = sum + corrected
            compensation = (next - sum) - corrected
            sum = next
        }
        // точный ответ целый, поэтому округляем сумму; ограничение на n выше нужно из-за погрешности double
        return BigInteger.valueOf(round(sum).toLong())
    }

    // вычисляет косинус без готовой функции: ряд тейлора имеет вид 1 - x^2/2! + x^4/4! -
    private fun cosine(angle: Double): Double {
        // косинус повторяется через 2π, поэтому приводим угол к отрезку от -π до π, где ряд сходится быстрее
        var x = angle % (2.0 * PI)
        if (x > PI) x -= 2.0 * PI
        var sum = 1.0
        var term = 1.0

        for (k in 1..24) {
            // следующий член получаем из предыдущего, чтобы не считать степени и факториалы заново
            term *= -x * x / ((2.0 * k - 1.0) * (2.0 * k))
            sum += term
        }
        return sum
    }

    // функция кармайкла — наименьшее положительное t, при котором a^t даёт остаток 1 по модулю n для всех a, взаимно простых с n
    fun carmichael(n: BigInteger): BigInteger {
        var result = BigInteger.ONE
        for (factor in factorize(n)) {
            var primePower = BigInteger.ONE
            for (i in 1 until factor.exponent) primePower *= factor.prime
            // для простой степени p^k берём p^(k-1) * (p - 1); исключение — 2^k при k >= 3, где результат вдвое меньше
            var component = primePower * (factor.prime - BigInteger.ONE)
            if (factor.prime == BigInteger.TWO && factor.exponent >= 3) {
                component /= BigInteger.TWO
            }
            // объединяем ответы для простых степеней через нок — наименьшее общее кратное: нок(a, b) = a / нод(a, b) * b
            result = result / gcd(result, component) * component
        }
        return result
    }

    // проверяет простоту перебором делителей: после проверки 2 достаточно нечётных чисел до корня из n
    fun isPrimeByTrialDivision(n: BigInteger): Boolean {
        if (n < BigInteger.TWO) return false
        if (n == BigInteger.TWO) return true
        if (!n.testBit(0)) return false
        var divisor = BigInteger.valueOf(3)
        // у составного числа хотя бы один из двух множителей не больше корня, поэтому дальше проверять не нужно
        while (divisor * divisor <= n) {
            if (n % divisor == BigInteger.ZERO) return false
            divisor += BigInteger.TWO
        }
        return true
    }

    // находит целую часть квадратного корня: ответ x удовлетворяет x^2 <= n < (x + 1)^2
    fun sqrtFloor(n: BigInteger): BigInteger {
        require(n.signum() >= 0) { "Корень отрицательного числа не определён" }
        if (n < BigInteger.TWO) return n
        // метод ньютона: начинаем с оценки сверху и уточняем её формулой (x + n / x) / 2, пока целая оценка уменьшается
        var x = BigInteger.ONE.shiftLeft((n.bitLength() + 1) / 2)
        var next = (x + n / x) / BigInteger.TWO
        while (next < x) {
            x = next
            next = (x + n / x) / BigInteger.TWO
        }
        return x
    }
}
