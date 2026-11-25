class RijndaelTables(private val modulus: Byte) {
    init {
        require(GaloisField.isIrreducible(modulus)) { "Модуль должен быть неприводимым полиномом степени 8" }
    }

    private val forward: ByteArray by lazy {
        val table = ByteArray(256)
        for (i in table.indices) {
            var inverse = 0
            if (i != 0) inverse = GaloisField.inverse(i.toByte(), modulus).toInt() and 0xFF
            var value = inverse.toByte()
            for (shift in 1..4) value = GaloisField.add(value, rotate(inverse, shift).toByte())
            table[i] = GaloisField.add(value, 0x63.toByte())
        }
        table
    }

    private val backward: ByteArray by lazy {
        val table = ByteArray(256)
        for (i in table.indices) {
            var value = GaloisField.add(rotate(i, 1).toByte(), rotate(i, 3).toByte())
            value = GaloisField.add(value, rotate(i, 6).toByte())
            value = GaloisField.add(value, 0x05.toByte())
            if (value != 0.toByte()) value = GaloisField.inverse(value, modulus)
            table[i] = value
        }
        table
    }

    private val products: Array<ByteArray> by lazy {
        val table = Array(16) { ByteArray(256) }
        for (coefficient in table.indices) {
            for (value in 0..255) {
                table[coefficient][value] = GaloisField.multiply(coefficient.toByte(), value.toByte(), modulus)
            }
        }
        table
    }

    fun substitute(value: Byte): Byte = forward[value.toInt() and 0xFF]

    fun inverseSubstitute(value: Byte): Byte = backward[value.toInt() and 0xFF]

    fun multiply(value: Byte, coefficient: Int): Byte {
        require(coefficient in 0..15) { "Коэффициент должен быть от 0 до 15" }
        return products[coefficient][value.toInt() and 0xFF]
    }

    private fun rotate(value: Int, shift: Int): Int {
        return ((value shl shift) or (value ushr (8 - shift))) and 0xFF
    }
}
