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
        val initial = permutation.permute(block, DesTables.IP, BitOrder.MOST_SIGNIFICANT_FIRST, 1) // от старшего бита нумерация и с единицы IP
        val encrypted = network.encrypt(initial) // сеть фейстеля
        return permutation.permute(encrypted, DesTables.INVERSE_IP, BitOrder.MOST_SIGNIFICANT_FIRST, 1) // обратная перестановка IP^-1
    }

    override fun decrypt(block: ByteArray): ByteArray { // то же самое только decrypt с раундовыми ключами от 16 до 1
        require(block.size == blockSize) { "Блок DES должен содержать 8 байтов" }
        val initial = permutation.permute(block, DesTables.IP, BitOrder.MOST_SIGNIFICANT_FIRST, 1)
        val decrypted = network.decrypt(initial)
        return permutation.permute(decrypted, DesTables.INVERSE_IP, BitOrder.MOST_SIGNIFICANT_FIRST, 1)
    }
}
