interface KeyExpansion {
    fun generateRoundKeys(key: ByteArray): Array<ByteArray>
}
