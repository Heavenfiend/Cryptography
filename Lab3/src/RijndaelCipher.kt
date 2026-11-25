class RijndaelCipher(
    override val blockSize: Int = 16,
    modulus: Byte = 0x1B
) : SymmetricCipher {
    private val keyExpansion = RijndaelKeyExpansion(blockSize, modulus)
    private val transformation = RijndaelRoundTransformation(blockSize, modulus)

    @Volatile
    private var roundKeys: Array<ByteArray>? = null

    override fun setKey(key: ByteArray) {
        roundKeys = keyExpansion.generateRoundKeys(key.copyOf())
    }

    override fun encrypt(block: ByteArray): ByteArray {
        val keys = roundKeys
        check(keys != null) { "Сначала задайте ключ методом setKey" }
        require(block.size == blockSize) { "Размер блока должен быть $blockSize байтов" }
        var state = block.copyOf()
        transformation.addRoundKey(state, keys[0])
        for (round in 1 until keys.size) {
            state = transformation.transform(state, keys[round], round == keys.lastIndex)
        }
        return state
    }

    override fun decrypt(block: ByteArray): ByteArray {
        val keys = roundKeys
        check(keys != null) { "Сначала задайте ключ методом setKey" }
        require(block.size == blockSize) { "Размер блока должен быть $blockSize байтов" }
        var state = block.copyOf()
        transformation.addRoundKey(state, keys.last())
        for (round in keys.lastIndex - 1 downTo 0) {
            state = transformation.inverseTransform(state, keys[round], round == 0)
        }
        return state
    }
}
