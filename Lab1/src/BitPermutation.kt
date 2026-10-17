enum class BitOrder { // выбор режима нумеровки битов
    MOST_SIGNIFICANT_FIRST,
    LEAST_SIGNIFICANT_FIRST
}

class BitPermutation {
    fun permute(
        value: ByteArray,
        pBlock: IntArray,
        bitOrder: BitOrder,
        startIndex: Int // 0 или 1
    ): ByteArray {
        require(startIndex == 0 || startIndex == 1) {
            "Начальный номер брита должен быть 0 или 1"
        }

        val inputBitCount = value.size.toLong() * 8
        val resultByteCount = pBlock.size / 8 + if (pBlock.size % 8 == 0) 0 else 1 // кол-во байтов в резе
        val result = ByteArray(resultByteCount)

        for (outputIndex in pBlock.indices) {
            val inputIndex = pBlock[outputIndex].toLong() - startIndex
            require(inputIndex >= 0 && inputIndex < inputBitCount) {
                "Номер бита ${pBlock[outputIndex]} выходит за пределы исходного массива"
            }

            val inputByteIndex: Int
            val inputBitOffset: Int
            val outputByteIndex: Int
            val outputBitOffset: Int

            when (bitOrder) {
                BitOrder.MOST_SIGNIFICANT_FIRST -> {
                    inputByteIndex = (inputIndex / 8).toInt() // номер байта
                    inputBitOffset = 7 - (inputIndex % 8).toInt() // справа налево птмчт
                    outputByteIndex = outputIndex / 8
                    outputBitOffset = 7 - outputIndex % 8
                }
                BitOrder.LEAST_SIGNIFICANT_FIRST -> {
                    inputByteIndex = value.lastIndex - (inputIndex / 8).toInt()
                    inputBitOffset = (inputIndex % 8).toInt()
                    outputByteIndex = result.lastIndex - outputIndex / 8
                    outputBitOffset = outputIndex % 8
                }
            }

            val unsignedByte = value[inputByteIndex].toInt() and 0xFF // т.к. знаковый котлин -128 .. 127
            val bit = (unsignedByte ushr inputBitOffset) and 1 // сдвигаем вправо на inputBitOffset
            result[outputByteIndex] =
                (result[outputByteIndex].toInt() or (bit shl outputBitOffset)).toByte() // побитовое или
        }

        return result
    }
}
