/** выполняет одно шифрующее преобразование с одним раундовым ключом, функция F */
interface RoundTransformation {
    fun transform(block: ByteArray, roundKey: ByteArray): ByteArray
}
