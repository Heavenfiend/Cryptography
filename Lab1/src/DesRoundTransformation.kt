// функция F у DES
class DesRoundTransformation(private val permutation: BitPermutation = BitPermutation()) : RoundTransformation {
    override fun transform(block: ByteArray, roundKey: ByteArray): ByteArray {
        require(block.size == 4) { "Раунд DES принимает половину блока: 4 байта" }
        require(roundKey.size == 6) { "Раундовый ключ DES должен содержать 6 байтов" }

        val expanded = permutation.permute(block, DesTables.EXPANSION, BitOrder.MOST_SIGNIFICANT_FIRST, 1) // расширение до 48 бит
        var mixed = 0L
        for (i in expanded.indices) {
            val byte = (expanded[i].toInt() xor roundKey[i].toInt()) and 0xFF
            mixed = (mixed shl 8) or byte.toLong()
        }

        val substituted = ByteArray(4)
        for (box in 0 until 8) {
            val sixBits = ((mixed ushr (42 - 6 * box)) and 0x3F).toInt()
            // крайние - строка, четыре в середине - столбец
            val row = ((sixBits ushr 4) and 2) or (sixBits and 1) // строка
            val column = (sixBits ushr 1) and 0x0F
            val fourBits = DesTables.S_BOXES[box][row * 16 + column]
            val shift = if (box % 2 == 0) 4 else 0
            substituted[box / 2] = (substituted[box / 2].toInt() or (fourBits shl shift)).toByte() // записываем по пол байта
        }

        return permutation.permute(substituted, DesTables.P, BitOrder.MOST_SIGNIFICANT_FIRST, 1) // P блок и получаем F
    }
}
