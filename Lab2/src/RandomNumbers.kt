import java.math.BigInteger
import java.security.SecureRandom

class RandomNumbers(private val random: SecureRandom = SecureRandom()) {
    fun below(limit: BigInteger): BigInteger {
        require(limit > BigInteger.ZERO) { "Верхняя граница должна быть положительной" }
        var result: BigInteger
        do {
            // Генератор случайных битов, а не готовый генератор простых чисел.
            result = BigInteger(limit.bitLength(), random)
        } while (result >= limit)
        return result
    }

    fun oddWithBitLength(bitLength: Int): BigInteger {
        require(bitLength >= 2) { "Битовая длина должна быть не меньше 2" }
        return BigInteger(bitLength, random).setBit(bitLength - 1).setBit(0)
    }
}
