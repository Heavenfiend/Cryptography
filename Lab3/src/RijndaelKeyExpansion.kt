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
        val columns = blockSize / 4
        val keyWords = key.size / 4
        val rounds = maxOf(columns, keyWords) + 6
        val words = Array(columns * (rounds + 1)) { ByteArray(4) }
        for (i in 0 until keyWords) {
            for (row in 0..3) words[i][row] = key[4 * i + row]
        }

        var roundConstant: Byte = 1
        for (i in keyWords until words.size) {
            val temporary = words[i - 1].copyOf()
            if (i % keyWords == 0) {
                val first = temporary[0]
                for (row in 0..2) temporary[row] = temporary[row + 1]
                temporary[3] = first
                for (row in 0..3) temporary[row] = tables.substitute(temporary[row])
                temporary[0] = GaloisField.add(temporary[0], roundConstant)
                roundConstant = GaloisField.multiply(roundConstant, 2, modulus)
            } else if (keyWords == 8 && i % keyWords == 4) {
                for (row in 0..3) temporary[row] = tables.substitute(temporary[row])
            }
            for (row in 0..3) words[i][row] = GaloisField.add(words[i - keyWords][row], temporary[row])
        }

        val roundKeys = Array(rounds + 1) { ByteArray(blockSize) }
        for (round in roundKeys.indices) {
            for (column in 0 until columns) {
                words[round * columns + column].copyInto(roundKeys[round], column * 4)
            }
        }
        return roundKeys
    }
}
