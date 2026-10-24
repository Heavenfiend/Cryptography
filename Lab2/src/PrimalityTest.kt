import java.math.BigInteger

interface PrimalityTest {
    fun isProbablePrime(number: BigInteger, minProbability: Double): Boolean
}

abstract class AbstractPrimalityTest(
    private val errorPerRound: Double,
    private val random: RandomNumbers = RandomNumbers()
) : PrimalityTest {
    init {
        require(errorPerRound > 0.0 && errorPerRound < 1.0) { "Ошибка одного раунда должна быть между 0 и 1" }
    }

    final override fun isProbablePrime(number: BigInteger, minProbability: Double): Boolean {
        val rounds = iterationCount(minProbability)
        if (number < BigInteger.TWO) return false
        if (number == BigInteger.TWO || number == BigInteger.valueOf(3)) return true
        if (!number.testBit(0)) return false
        for (i in 0 until rounds) {
            val base = random.below(number - BigInteger.valueOf(3)) + BigInteger.TWO
            if (!testIteration(number, base)) return false
        }
        return true
    }

    fun iterationCount(minProbability: Double): Int {
        require(minProbability >= 0.5 && minProbability < 1.0) {
            "Вероятность должна быть в диапазоне [0.5, 1)"
        }
        var error = 1.0
        var count = 0
        while (error > 1.0 - minProbability) {
            error *= errorPerRound
            count++
        }
        return count
    }

    fun testIteration(number: BigInteger, base: BigInteger): Boolean {
        require(number >= BigInteger.valueOf(5) && number.testBit(0)) { "Нужно нечётное n >= 5" }
        require(base >= BigInteger.TWO && base <= number - BigInteger.TWO) { "Нужно 2 <= a <= n - 2" }
        if (NumberTheory.gcd(base, number) != BigInteger.ONE) return false
        return performIteration(number, base)
    }

    protected abstract fun performIteration(number: BigInteger, base: BigInteger): Boolean

}

class FermatTest : AbstractPrimalityTest(0.5) {
    override fun performIteration(number: BigInteger, base: BigInteger): Boolean {
        return NumberTheory.modPow(base, number - BigInteger.ONE, number) == BigInteger.ONE
    }

}

class SolovayStrassenTest : AbstractPrimalityTest(0.5) {
    override fun performIteration(number: BigInteger, base: BigInteger): Boolean {
        val symbol = NumberTheory.jacobi(base, number)
        if (symbol == 0) return false
        val expected = NumberTheory.normalize(BigInteger.valueOf(symbol.toLong()), number)
        val actual = NumberTheory.modPow(base, (number - BigInteger.ONE) / BigInteger.TWO, number)
        return actual == expected
    }
}

class MillerRabinTest : AbstractPrimalityTest(0.25) {
    override fun performIteration(number: BigInteger, base: BigInteger): Boolean {
        val minusOne = number - BigInteger.ONE
        var oddPart = minusOne
        var twos = 0
        while (!oddPart.testBit(0)) {
            oddPart = oddPart.shiftRight(1)
            twos++
        }
        var value = NumberTheory.modPow(base, oddPart, number)
        if (value == BigInteger.ONE || value == minusOne) return true
        for (i in 1 until twos) {
            value = value * value % number
            if (value == minusOne) return true
            if (value == BigInteger.ONE) return false
        }
        return false
    }
}
