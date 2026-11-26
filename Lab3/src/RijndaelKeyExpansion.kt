class RijndaelKeyExpansion(
    private val blockSize: Int = 16,
    private val modulus: Byte = 0x1B
) : KeyExpansion {

    private val tables = RijndaelTables(modulus)

    init {
        require(blockSize == 16 || blockSize == 24 || blockSize == 32) { "Размер блока должен быть 16, 24 или 32 байта" }
    }

    override fun generateRoundKeys(key: ByteArray): Array<ByteArray> {
        require(key.size == 16 || key.size == 24 || key.size == 32) { "Размер ключа должен быть 16, 24 или 32 байта" }

        // вычисляем количество слов в блоке и ключе и количество раундов
        val columns = blockSize / 4
        val keyWords = key.size / 4
        val rounds = maxOf(columns, keyWords) + 6

        // создаем массив всех слов расширенного ключа
        val words = Array(columns * (rounds + 1)) { ByteArray(4) }

        // заполняем первые слова байтами исходного ключа
        for (i in 0 until keyWords) {
            for (row in 0..3) words[i][row] = key[4 * i + row]
        }

        // начальное значение раундовой константы
        var roundConstant: Byte = 1

        // последовательно вычисляем оставшиеся слова
        for (i in keyWords until words.size) {
            val temporary = words[i - 1].copyOf()

            if (i % keyWords == 0) {

                // rotWord циклически сдвигаем байты слова влево
                val first = temporary[0]
                for (row in 0..2) temporary[row] = temporary[row + 1]
                temporary[3] = first

                // subWord заменяем каждый байт через sBox
                for (row in 0..3) temporary[row] = tables.substitute(temporary[row])

                // добавляем rcon к первому байту слова
                temporary[0] = GaloisField.add(temporary[0], roundConstant)

                // вычисляем следующую константу умножением на 2 в поле Галуа
                roundConstant = GaloisField.multiply(roundConstant, 2, modulus)

            } else if (keyWords == 8 && i % keyWords == 4) {

                // дополнительный subWord для ключа длиной 256 бит
                for (row in 0..3) temporary[row] = tables.substitute(temporary[row])
            }

            // вычисляем новое слово через xor
            for (row in 0..3) words[i][row] = GaloisField.add(words[i - keyWords][row], temporary[row])
        }

        // формируем раундовые ключи из полученных слов
        val roundKeys = Array(rounds + 1) { ByteArray(blockSize) }

        for (round in roundKeys.indices) {
            for (column in 0 until columns) {
                words[round * columns + column].copyInto(roundKeys[round], column * 4)
            }
        }

        return roundKeys
    }
}