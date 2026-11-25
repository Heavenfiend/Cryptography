import kotlin.random.Random

class BlockPadding(
    private val mode: PaddingMode,
    private val blockSize: Int
) {
    init {
        require(blockSize in 1..255) { "Размер блока должен быть от 1 до 255 байтов" }
    }

    fun add(data: ByteArray): ByteArray {
        val remainder = data.size % blockSize
        val count = if (mode == PaddingMode.ZEROS && remainder == 0) 0 else blockSize - remainder
        require(data.size <= Int.MAX_VALUE - count) { "Слишком большой массив" }
        val result = data.copyOf(data.size + count)
        if (count == 0) return result

        when (mode) {
            PaddingMode.ZEROS -> Unit
            PaddingMode.ANSI_X923 -> result[result.lastIndex] = count.toByte()
            PaddingMode.PKCS7 -> result.fill(count.toByte(), data.size)
            PaddingMode.ISO_10126 -> {
                for (i in data.size until result.lastIndex) result[i] = Random.nextInt(256).toByte()
                result[result.lastIndex] = count.toByte()
            }
        }
        return result
    }

    fun remove(data: ByteArray): ByteArray {
        require(data.size % blockSize == 0) { "Длина данных не кратна размеру блока" }
        if (mode == PaddingMode.ZEROS) {
            var end = data.size
            while (end > 0 && data[end - 1] == 0.toByte()) end--
            return data.copyOf(end)
        }
        require(data.isNotEmpty()) { "Отсутствует блок с набивкой" }
        val count = data.last().toInt() and 0xFF
        require(count in 1..blockSize && count <= data.size) { "Некорректная длина набивки" }
        val end = data.size - count
        when (mode) {
            PaddingMode.ANSI_X923 -> require((end until data.lastIndex).all { data[it] == 0.toByte() }) {
                "Некорректная набивка ANSI X.923"
            }
            PaddingMode.PKCS7 -> require((end until data.size).all { (data[it].toInt() and 0xFF) == count }) {
                "Некорректная набивка PKCS7"
            }
            else -> Unit
        }
        return data.copyOf(end)
    }
}
