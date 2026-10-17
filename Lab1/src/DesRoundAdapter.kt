// позволяет использовать шифрование DES как раундовую функцию F для DEAL
class DesRoundAdapter(private val des: SymmetricCipher = DesCipher()) : RoundTransformation {
    init {
        require(des.blockSize == 8) { "Для DEAL нужен DES с блоком 8 байтов" }
    }

    override fun transform(block: ByteArray, roundKey: ByteArray): ByteArray {
        require(block.size == 8) { "Половина блока DEAL должна содержать 8 байтов" }
        require(roundKey.size == 8) { "Раундовый ключ DEAL должен содержать 8 байтов" }

        return synchronized(des) {
            des.setKey(roundKey.copyOf())
            des.encrypt(block.copyOf()).copyOf()
        }
    }
}
