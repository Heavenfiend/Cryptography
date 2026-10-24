class FeistelNetwork(
    private val keyExpansion: KeyExpansion,
    private val roundTransformation: RoundTransformation,
    override val blockSize: Int = 8
) : SymmetricCipher {
    @Volatile
    private var roundKeys: Array<ByteArray>? = null

    init {
        require(blockSize > 0 && blockSize % 2 == 0) {
            "Размер блока сети Фейстеля должен быть положительным и чётным"
        }
    }

    override fun setKey(key: ByteArray) {
        require(key.isNotEmpty()) { "Ключ не должен быть пустым" }
        val generatedKeys = keyExpansion.generateRoundKeys(key.copyOf())
        require(generatedKeys.isNotEmpty()) { "Нужен хотя бы один раундовый ключ" }
        require(generatedKeys.all { it.isNotEmpty() }) { "Раундовые ключи не должны быть пустыми" }
        roundKeys = Array(generatedKeys.size) { generatedKeys[it].copyOf() }
    }

    override fun encrypt(block: ByteArray): ByteArray = process(block, decrypting = false)

    override fun decrypt(block: ByteArray): ByteArray = process(block, decrypting = true)

    private fun process(block: ByteArray, decrypting: Boolean): ByteArray {
        val keys = checkNotNull(roundKeys) { "Сначала настройте ключ методом setKey" }
        require(block.size == blockSize) { "Блок должен содержать ровно $blockSize байтов" }
        val halfSize = blockSize / 2
        var left = block.copyOfRange(0, halfSize)
        var right = block.copyOfRange(halfSize, blockSize)

        for (round in keys.indices) {
            val keyIndex = if (decrypting) keys.lastIndex - round else round
            val transformed = synchronized(roundTransformation) {
                roundTransformation.transform(right.copyOf(), keys[keyIndex].copyOf()).copyOf()
            }
            require(transformed.size == halfSize) {
                "Раундовое преобразование должно вернуть $halfSize байтов"
            }

            val nextRight = ByteArray(halfSize) {
                (left[it].toInt() xor transformed[it].toInt()).toByte()
            }
            left = right
            right = nextRight
        }

        return right + left
    }
}
