
import java.math.BigInteger

// хранит числитель и знаменатель подходящей дроби
data class Convergent(val numerator: BigInteger, val denominator: BigInteger)

// хранит результат атаки Винера и найденные подходящие дроби
data class WienerResult(
    val privateExponent: BigInteger?,
    val phi: BigInteger?,
    val convergents: List<Convergent>
)

object WienerAttack {

    // раскладываем обычную дробь в цепную через алгоритм Евклида
    fun continuedFraction(numerator: BigInteger, denominator: BigInteger): List<BigInteger> {
        require(numerator >= BigInteger.ZERO && denominator > BigInteger.ZERO) { "Нужны числитель >= 0, знаменатель > 0" }
        val coefficients = ArrayList<BigInteger>()
        var a = numerator
        var b = denominator
        while (b != BigInteger.ZERO) {
            coefficients.add(a / b)
            val remainder = a % b
            a = b
            b = remainder
        }
        return coefficients
    }

    // строим подходящие дроби по коэффициентам цепной дроби
    fun convergents(coefficients: List<BigInteger>): List<Convergent> {
        require(coefficients.isNotEmpty()) { "Цепная дробь не должна быть пустой" }
        val result = ArrayList<Convergent>()
        var previousNumerator = BigInteger.ONE
        var olderNumerator = BigInteger.ZERO
        var previousDenominator = BigInteger.ZERO
        var olderDenominator = BigInteger.ONE
        for (i in coefficients.indices) {
            val coefficient = coefficients[i]
            require(coefficient >= BigInteger.ZERO && (i == 0 || coefficient > BigInteger.ZERO)) {
                "Некорректный коэффициент цепной дроби"
            }
            val numerator = coefficient * previousNumerator + olderNumerator
            val denominator = coefficient * previousDenominator + olderDenominator
            result.add(Convergent(numerator, denominator))
            olderNumerator = previousNumerator
            previousNumerator = numerator
            olderDenominator = previousDenominator
            previousDenominator = denominator
        }
        return result
    }

    // выполняем атаку Винера по открытому ключу RSA
    fun attack(key: RsaPublicKey): WienerResult {
        require(key.n > BigInteger.valueOf(3) && key.e > BigInteger.ONE && key.e < key.n) {
            "Для атаки нужен открытый RSA-ключ с 1 < e < n"
        }

        // получаем подходящие дроби для e / n
        val fractions = convergents(continuedFraction(key.e, key.n))

        // проверяем каждую дробь как кандидата на k / d
        for (fraction in fractions) {
            val k = fraction.numerator
            val d = fraction.denominator
            if (k == BigInteger.ZERO) continue

            // пытаемся восстановить функцию Эйлера
            val edMinusOne = key.e * d - BigInteger.ONE
            if (edMinusOne % k != BigInteger.ZERO) continue
            val phi = edMinusOne / k
            if (phi <= BigInteger.ZERO || phi >= key.n) continue

            // находим сумму p + q и дискриминант квадратного уравнения
            val sum = key.n - phi + BigInteger.ONE
            val discriminant = sum * sum - BigInteger.valueOf(4) * key.n
            if (discriminant.signum() < 0) continue

            // проверяем, что дискриминант является полным квадратом
            val root = NumberTheory.sqrtFloor(discriminant)
            if (root * root != discriminant) continue
            if ((sum + root).testBit(0)) continue

            // восстанавливаем множители p и q
            val p = (sum + root) / BigInteger.TWO
            val q = (sum - root) / BigInteger.TWO

            // если множители подходят, возвращаем найденный закрытый показатель
            if (p > BigInteger.ONE && q > BigInteger.ONE && p != q && p * q == key.n) {
                return WienerResult(d, phi, fractions)
            }
        }

        // если подходящий ключ не найден, возвращаем null и список дробей
        return WienerResult(null, null, fractions)
    }
}
