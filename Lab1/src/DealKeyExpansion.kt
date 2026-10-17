// Получает 6 или 8 раундовых ключей DEAL из основного ключа с помощью DES
class DealKeyExpansion : KeyExpansion {
    override fun generateRoundKeys(key: ByteArray): Array<ByteArray> {
        require(key.size == 16 || key.size == 24 || key.size == 32) {
            "Ключ DEAL должен содержать 16, 24 или 32 байта"
        }

        val partCount = key.size / 8 //                    2 / 3 / 4 части делим
        val roundCount = if (key.size == 32) 8 else 6
        val roundKeys = Array(roundCount) { ByteArray(8) }
        val des = DesCipher()
        des.setKey(byteArrayOf(0x01, 0x23, 0x45, 0x67, 0x89.toByte(), 0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte())) // статичный для всех
        var previous = ByteArray(8) // RK 1-8

        for (round in 0 until roundCount) {
            val start = (round % partCount) * 8 // диапазон в исходном ключе чтобы выбрать A/B/C
            val input = key.copyOfRange(start, start + 8)
            for (i in input.indices) {
                input[i] = (input[i].toInt() xor previous[i].toInt()).toByte()
            }

            if (round >= partCount) {
                val constant = 0x80 ushr (round - partCount)
                input[0] = (input[0].toInt() xor constant).toByte()
            }

            previous = des.encrypt(input)
            roundKeys[round] = previous
        }
        return roundKeys
    }
}
