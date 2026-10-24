interface RoundTransformation {
    fun transform(block: ByteArray, roundKey: ByteArray): ByteArray
}
