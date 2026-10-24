class DesCipher : SymmetricCipher {
    override val blockSize: Int = 8
    private val permutation = BitPermutation()
    private val network = FeistelNetwork(
        DesKeyExpansion(permutation),
        DesRoundTransformation(permutation),
        blockSize
    )

    override fun setKey(key: ByteArray) = network.setKey(key)

    override fun encrypt(block: ByteArray): ByteArray {
        require(block.size == blockSize) { "Блок DES должен содержать 8 байтов" }
        val initial = permutation.permute(block, DesTables.IP, BitOrder.MOST_SIGNIFICANT_FIRST, 1)
        val encrypted = network.encrypt(initial)
        return permutation.permute(encrypted, DesTables.INVERSE_IP, BitOrder.MOST_SIGNIFICANT_FIRST, 1)
    }

    override fun decrypt(block: ByteArray): ByteArray {
        require(block.size == blockSize) { "Блок DES должен содержать 8 байтов" }
        val initial = permutation.permute(block, DesTables.IP, BitOrder.MOST_SIGNIFICANT_FIRST, 1)
        val decrypted = network.decrypt(initial)
        return permutation.permute(decrypted, DesTables.INVERSE_IP, BitOrder.MOST_SIGNIFICANT_FIRST, 1)
    }
}
