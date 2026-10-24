class DesKeyExpansion(private val permutation: BitPermutation = BitPermutation()) : KeyExpansion {
    override fun generateRoundKeys(key: ByteArray): Array<ByteArray> {
        require(key.size == 8) { "Ключ DES должен содержать 8 байтов" }
        val selected = permutation.permute(key, DesTables.PC1, BitOrder.MOST_SIGNIFICANT_FIRST, 1)
        var packed = 0L
        for (byte in selected) packed = (packed shl 8) or (byte.toLong() and 0xFF)

        var left = (packed ushr 28).toInt() and 0x0FFFFFFF
        var right = packed.toInt() and 0x0FFFFFFF
        return Array(16) { round ->
            val shift = DesTables.SHIFTS[round]
            left = rotateLeft28(left, shift)
            right = rotateLeft28(right, shift)

            val combined = (left.toLong() shl 28) or right.toLong()
            val bytes = ByteArray(7) { index ->
                (combined ushr (8 * (6 - index))).toByte()
            }
            permutation.permute(bytes, DesTables.PC2, BitOrder.MOST_SIGNIFICANT_FIRST, 1)
        }
    }

    private fun rotateLeft28(value: Int, shift: Int): Int =
        ((value shl shift) or (value ushr (28 - shift))) and 0x0FFFFFFF
}
