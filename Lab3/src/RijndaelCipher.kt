class RijndaelCipher(
    override val blockSize: Int = 16,
    modulus: Byte = 0x1B
) : SymmetricCipher {

    private val keyExpansion = RijndaelKeyExpansion(blockSize, modulus)
    private val transformation = RijndaelRoundTransformation(blockSize, modulus)

    // храним раундовые ключи для повторного использования
    @Volatile
    private var roundKeys: Array<ByteArray>? = null

    // расширяем исходный ключ и сохраняем раундовые ключи
    override fun setKey(key: ByteArray) {
        roundKeys = keyExpansion.generateRoundKeys(key.copyOf())
    }

    // шифрование одного блока
    override fun encrypt(block: ByteArray): ByteArray {
        val keys = roundKeys
        check(keys != null) { "Сначала задайте ключ методом setKey" }
        require(block.size == blockSize) { "Размер блока должен быть $blockSize байтов" }

        var state = block.copyOf()

        // начальный addRoundKey перед всеми раундами
        transformation.addRoundKey(state, keys[0])

        // выполняем раунды шифрования пропуская mixColumns в последнем
        for (round in 1 until keys.size) {
            state = transformation.transform(state, keys[round], round == keys.lastIndex)
        }

        return state
    }

    // дешифрование одного блока
    override fun decrypt(block: ByteArray): ByteArray {
        val keys = roundKeys
        check(keys != null) { "Сначала задайте ключ методом setKey" }
        require(block.size == blockSize) { "Размер блока должен быть $blockSize байтов" }

        var state = block.copyOf()

        // выполняем addRoundKey с последним раундовым ключом
        transformation.addRoundKey(state, keys.last())

        // выполняем обратные раунды используя ключи в обратном порядке
        // в последнем обратном раунде пропускаем invMixColumns
        for (round in keys.lastIndex - 1 downTo 0) {
            state = transformation.inverseTransform(state, keys[round], round == 0)
        }

        return state
    }
}