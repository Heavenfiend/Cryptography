import java.math.BigInteger
import java.security.SecureRandom
// SecureRandom обеспечивает криптографически стойкую случайность
// предназначен для задач, где важно, чтобы случайные значения было трудно предсказать
class RandomNumbers(private val random: SecureRandom = SecureRandom()) {
    // случайное число меньше заданного
    fun below(limit: BigInteger): BigInteger {
        require(limit > BigInteger.ZERO) { "Верхняя граница должна быть положительной" }
        var result: BigInteger
        do {

            result = BigInteger(limit.bitLength(), random)
        } while (result >= limit)
        return result
    }

    // случайное нечётное число заданной битовой длины
    fun oddWithBitLength(bitLength: Int): BigInteger {
        require(bitLength >= 2) { "Битовая длина должна быть не меньше 2" }
        return BigInteger(bitLength, random).setBit(bitLength - 1).setBit(0)
    }
}
