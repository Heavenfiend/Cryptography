import java.math.BigInteger

// неприводимый множитель и его кратность
data class PolynomialFactor(val polynomial: BigInteger, val exponent: Int)

object GaloisField {

    // сложение двоичных полиномов через xor
    fun add(a: Byte, b: Byte): Byte {
        return (a.toInt() xor b.toInt()).toByte()
    }

    // умножение элементов поля по неприводимому модулю
    fun multiply(a: Byte, b: Byte, modulus: Byte): Byte {
        require(isIrreducible(modulus)) { "Модуль должен быть неприводимым полиномом степени 8" }
        return multiplyUnchecked(a.toInt() and 255, b.toInt() and 255, modulus.toInt() and 255).toByte()
    }

    // нахождение обратного элемента в поле Галуа
    fun inverse(value: Byte, modulus: Byte): Byte {
        require(isIrreducible(modulus)) { "Модуль должен быть неприводимым полиномом степени 8" }
        require(value != 0.toByte()) { "У нуля нет обратного элемента" }

        // у ненулевых элементов поля a^255 = 1 поэтому обратный элемент равен a^254
        var power = 254
        var current = value.toInt() and 255
        var result = 1

        // быстрое возведение в степень через последовательное возведение в квадрат
        while (power > 0) {
            if ((power and 1) != 0) result = multiplyUnchecked(result, current, modulus.toInt() and 255)
            current = multiplyUnchecked(current, current, modulus.toInt() and 255)
            power = power ushr 1
        }

        return result.toByte()
    }

    // проверка неприводимости полинома восьмой степени
    fun isIrreducible(modulus: Byte): Boolean {

        // восстанавливаем старший коэффициент при x^8
        val polynomial = 256 or (modulus.toInt() and 255)

        // проверяем делимость на x
        if ((polynomial and 1) == 0) return false

        // перебираем делители степени не выше четвертой с ненулевым свободным членом
        for (divisor in 3..31 step 2) {
            if (remainder(polynomial, divisor) == 0) return false
        }

        return true
    }

    // поиск всех неприводимых полиномов восьмой степени
    fun irreducibleModuli(): List<Byte> {
        val result = ArrayList<Byte>()

        // перебираем все 256 вариантов младших восьми коэффициентов
        for (value in 0..255) {
            if (isIrreducible(value.toByte())) result.add(value.toByte())
        }

        return result
    }

    // разложение двоичного полинома на неприводимые множители
    fun factorize(polynomial: BigInteger): List<PolynomialFactor> {
        require(polynomial > BigInteger.ZERO) { "Для разложения нужен ненулевой неотрицательный полином" }

        val result = ArrayList<PolynomialFactor>()
        var remaining = polynomial
        var divisor = BigInteger.TWO

        // перебираем делители от меньших степеней к большим
        while (divisor.bitLength() - 1 <= (remaining.bitLength() - 1) / 2) {
            var exponent = 0

            // делим на текущий делитель пока остаток равен нулю
            while (true) {
                val quotient = divideExactly(remaining, divisor) ?: break
                remaining = quotient
                exponent++
            }

            // сохраняем найденный множитель и его кратность
            if (exponent > 0) result.add(PolynomialFactor(divisor, exponent))

            divisor += BigInteger.ONE
        }

        // оставшийся полином является неприводимым
        if (remaining != BigInteger.ONE) result.add(PolynomialFactor(remaining, 1))

        return result
    }

    // умножение по модулю без повторной проверки его неприводимости
    private fun multiplyUnchecked(a: Int, b: Int, modulus: Int): Int {
        var left = a
        var right = b
        var result = 0

        // перебираем биты второго множителя
        while (right != 0) {
            if ((right and 1) != 0) result = result xor left

            // запоминаем старший бит перед сдвигом
            val highBit = left and 128

            // умножаем первый полином на x
            left = (left shl 1) and 255

            // если появилась восьмая степень выполняем xor с сокращенным модулем
            if (highBit != 0) left = left xor modulus

            right = right ushr 1
        }

        return result
    }

    // нахождение остатка от деления двоичных полиномов
    private fun remainder(value: Int, divisor: Int): Int {
        var result = value
        val divisorDegree = 31 - Integer.numberOfLeadingZeros(divisor)

        // сокращаем старший член делимого пока его степень не станет меньше степени делителя
        while (result != 0) {
            val shift = 31 - Integer.numberOfLeadingZeros(result) - divisorDegree
            if (shift < 0) break
            result = result xor (divisor shl shift)
        }

        return result
    }

    // деление полиномов столбиком с вычислением частного
    private fun divideExactly(value: BigInteger, divisor: BigInteger): BigInteger? {
        var rest = value
        var quotient = BigInteger.ZERO

        while (rest != BigInteger.ZERO && rest.bitLength() >= divisor.bitLength()) {
            val shift = rest.bitLength() - divisor.bitLength()

            // устанавливаем соответствующий коэффициент частного
            quotient = quotient.setBit(shift)

            // сокращаем старший член остатка через сдвиг и xor
            rest = rest.xor(divisor.shiftLeft(shift))
        }

        // возвращаем частное только при делении без остатка
        return if (rest == BigInteger.ZERO) quotient else null
    }
}