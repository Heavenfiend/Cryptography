// шифрует и расшифровывает 16 байтовые блоки алгоритмом DEAL
class DealCipher : SymmetricCipher {
    override val blockSize: Int = 16
    private val network = FeistelNetwork(DealKeyExpansion(), DesRoundAdapter(), blockSize)

    override fun setKey(key: ByteArray) = network.setKey(key)

    override fun encrypt(block: ByteArray): ByteArray {
        return network.encrypt(swapHalves(block))
    }

    override fun decrypt(block: ByteArray): ByteArray {
        return swapHalves(network.decrypt(block))
    }

    private fun swapHalves(block: ByteArray): ByteArray {
        require(block.size == blockSize) { "Блок DEAL должен содержать 16 байтов" }
        return block.copyOfRange(8, 16) + block.copyOfRange(0, 8)
    }
}
