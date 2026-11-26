class RijndaelRoundTransformation(
    private val blockSize: Int = 16,
    modulus: Byte = 0x1B
) : RoundTransformation {

    private val tables = RijndaelTables(modulus)
    private val columns = blockSize / 4

    // величины сдвигов строк в зависимости от размера блока
    private val shifts = if (blockSize == 32) intArrayOf(0, 1, 3, 4) else intArrayOf(0, 1, 2, 3)

    // матрица для mixColumns
    private val forwardMatrix = arrayOf(
        intArrayOf(2, 3, 1, 1),
        intArrayOf(1, 2, 3, 1),
        intArrayOf(1, 1, 2, 3),
        intArrayOf(3, 1, 1, 2)
    )

    // обратная матрица для invMixColumns
    private val inverseMatrix = arrayOf(
        intArrayOf(14, 11, 13, 9),
        intArrayOf(9, 14, 11, 13),
        intArrayOf(13, 9, 14, 11),
        intArrayOf(11, 13, 9, 14)
    )

    init {
        require(blockSize == 16 || blockSize == 24 || blockSize == 32) { "Размер блока должен быть 16, 24 или 32 байта" }
    }

    // выполнение обычного раунда шифрования
    override fun transform(block: ByteArray, roundKey: ByteArray): ByteArray {
        return transform(block, roundKey, false)
    }

    // выполняем все преобразования раунда кроме mixColumns в последнем
    fun transform(block: ByteArray, roundKey: ByteArray, lastRound: Boolean): ByteArray {
        require(block.size == blockSize && roundKey.size == blockSize) { "Блок и раундовый ключ должны иметь размер $blockSize байтов" }

        val state = block.copyOf()
        substitute(state, false)
        shiftRows(state, false)
        if (!lastRound) mixColumns(state, false)
        addRoundKey(state, roundKey)

        return state
    }

    // выполняем обратный раунд пропуская invMixColumns в последнем
    fun inverseTransform(block: ByteArray, roundKey: ByteArray, lastRound: Boolean = false): ByteArray {
        require(block.size == blockSize && roundKey.size == blockSize) { "Блок и раундовый ключ должны иметь размер $blockSize байтов" }

        val state = block.copyOf()
        shiftRows(state, true)
        substitute(state, true)
        addRoundKey(state, roundKey)
        if (!lastRound) mixColumns(state, true)

        return state
    }

    // складываем каждый байт state с соответствующим байтом раундового ключа
    fun addRoundKey(state: ByteArray, roundKey: ByteArray) {
        require(state.size == blockSize && roundKey.size == blockSize) { "Блок и раундовый ключ должны иметь размер $blockSize байтов" }

        for (i in state.indices) state[i] = GaloisField.add(state[i], roundKey[i])
    }

    // заменяем каждый байт через прямую или обратную sBox
    private fun substitute(state: ByteArray, inverse: Boolean) {
        for (i in state.indices) {
            if (inverse) state[i] = tables.inverseSubstitute(state[i])
            else state[i] = tables.substitute(state[i])
        }
    }

    // циклически сдвигаем строки влево или вправо
    private fun shiftRows(state: ByteArray, inverse: Boolean) {
        val original = state.copyOf()

        // первую строку не трогаем так как ее сдвиг равен нулю
        for (row in 1..3) {
            for (column in 0 until columns) {
                var sourceColumn = (column + shifts[row]) % columns
                if (inverse) sourceColumn = (column - shifts[row] + columns) % columns

                state[4 * column + row] = original[4 * sourceColumn + row]
            }
        }
    }

    // перемножаем каждый столбец state с прямой или обратной матрицей
    private fun mixColumns(state: ByteArray, inverse: Boolean) {
        var matrix = forwardMatrix
        if (inverse) matrix = inverseMatrix

        val original = state.copyOf()

        for (column in 0 until columns) {
            for (row in 0..3) {
                var value: Byte = 0

                // умножаем строку матрицы на исходный столбец по правилам поля Галуа
                for (i in 0..3) {
                    val product = tables.multiply(original[4 * column + i], matrix[row][i])
                    value = GaloisField.add(value, product)
                }

                state[4 * column + row] = value
            }
        }
    }
}