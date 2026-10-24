interface SymmetricCipher {
    val blockSize: Int

    fun setKey(key: ByteArray)

    fun encrypt(block: ByteArray): ByteArray

    fun decrypt(block: ByteArray): ByteArray
}
