import java.util.stream.IntStream
class RandomDeltaMode(
    delta: ByteArray,
    private val blockSize: Int
) {
    private val delta = delta.copyOf()

    init {
        require(this.delta.size == blockSize) { "Размер delta должен совпадать с размером блока" }
        require((this.delta.last().toInt() and 1) == 1) { "Шаг delta должен быть нечётным" }
    }

    fun process(data: ByteArray, cipher: SymmetricCipher, iv: ByteArray, encrypting: Boolean): ByteArray {
        require(iv.size == blockSize) { "Размер IV должен совпадать с размером блока" }
        require(data.size % blockSize == 0) { "Длина данных должна быть кратна размеру блока" }
        val result = ByteArray(data.size)
        IntStream.range(0, data.size / blockSize).parallel().forEach { index ->
            val mask = maskAt(iv, index)
            val input = data.copyOfRange(index * blockSize, (index + 1) * blockSize)
            val output = if (encrypting) {
                cipher.encrypt(ByteArray(blockSize) { (input[it].toInt() xor mask[it].toInt()).toByte() })
            } else {
                val decrypted = cipher.decrypt(input)
                require(decrypted.size == blockSize) { "Алгоритм вернул блок неверного размера" }
                ByteArray(blockSize) { (decrypted[it].toInt() xor mask[it].toInt()).toByte() }
            }
            require(output.size == blockSize) { "Алгоритм вернул блок неверного размера" }
            output.copyInto(result, index * blockSize)
        }
        return result
    }

    private fun maskAt(iv: ByteArray, index: Int): ByteArray {
        val result = ByteArray(blockSize)
        var carry = 0L
        for (i in result.lastIndex downTo 0) {
            val sum = (iv[i].toInt() and 0xFF).toLong() +
                (delta[i].toInt() and 0xFF).toLong() * index + carry
            result[i] = sum.toByte()
            carry = sum ushr 8
        }
        return result
    }
}
