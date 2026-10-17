/** преобразует исходный ключ в набор ключей для отдельных раундов */
interface KeyExpansion {
    fun generateRoundKeys(key: ByteArray): Array<ByteArray>
}
