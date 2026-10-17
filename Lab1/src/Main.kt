fun main() {
    demonstratePermutation()
    println()
    demonstrateContext()
    println()
    demonstrateFeistel()
    println()
    demonstrateDes()
    println()
    DesDemo.run()
    println()
    demonstrateDeal()
    println()
    DealDemo.run()
}

private fun demonstrateDeal() {
    val block = ByteArray(16) { it.toByte() }
    println("Пункт 6. DEAL: блок 16 байтов")
    for (keySize in listOf(16, 24, 32)) {
        val key = ByteArray(keySize) { it.toByte() }
        val cipher = DealCipher()
        cipher.setKey(key)
        val encrypted = cipher.encrypt(block)
        val decrypted = cipher.decrypt(encrypted)

        println("Ключ ($keySize байт): ${key.toHexText()}")
        println("Исходный блок: ${block.toHexText()}")
        println("Зашифрованный блок: ${encrypted.toHexText()}")
        println("Расшифрованный блок: ${decrypted.toHexText()}")
        check(decrypted.contentEquals(block))
        println("Проверка восстановления: OK")
    }
}

private fun demonstrateDes() {
    val key = byteArrayOf(0x13, 0x34, 0x57, 0x79, 0x9B.toByte(), 0xBC.toByte(), 0xDF.toByte(), 0xF1.toByte())
    val block = byteArrayOf(0x01, 0x23, 0x45, 0x67, 0x89.toByte(), 0xAB.toByte(), 0xCD.toByte(), 0xEF.toByte())
    val expected = byteArrayOf(0x85.toByte(), 0xE8.toByte(), 0x13, 0x54, 0x0F, 0x0A, 0xB4.toByte(), 0x05)
    val cipher = DesCipher()
    cipher.setKey(key)
    val encrypted = cipher.encrypt(block)
    val decrypted = cipher.decrypt(encrypted)

    println("Пункт 4. DES: проверка известного примера")
    println("Ключ: ${key.toHexText()}")
    println("Исходный блок: ${block.toHexText()}")
    println("Зашифрованный блок: ${encrypted.toHexText()}")
    println("Ожидаемый результат: ${expected.toHexText()}")
    println("Расшифрованный блок: ${decrypted.toHexText()}")
    check(encrypted.contentEquals(expected))
    check(decrypted.contentEquals(block))
}

private fun demonstratePermutation() {
    val value = byteArrayOf(0b01101110.toByte())
    val pBlock = intArrayOf(5, 7, 6, 4, 3, 2, 1, 0)
    val result = BitPermutation().permute(
        value = value,
        pBlock = pBlock,
        bitOrder = BitOrder.MOST_SIGNIFICANT_FIRST,
        startIndex = 0
    )

    println("Пункт 1. Перестановка битов")
    println("Исходные биты: ${value.toBitString()}")
    println("P-блок: ${pBlock.joinToString()}")
    println("Нумерация: от старшего бита, начиная с 0")
    println("Результат: ${result.toBitString()}")
    check(result.contentEquals(byteArrayOf(0b10110110.toByte())))
}

private fun demonstrateContext() {
    val context = SymmetricCipherContext(
        key = byteArrayOf(0x06, 0x03),
        cipher = createDemoCipher(),
        mode = CipherMode.CBC,
        paddingMode = PaddingMode.PKCS7,
        initializationVector = byteArrayOf(0x01, 0x02)
    )
    val data = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x05)

    println("Пункт 2. Контекст шифрования")
    println("Учебная сеть Фейстеля. Размер блока: 2 байта.")
    println("Режим: CBC. Набивка: PKCS7.")
    println("Исходные данные (${data.size} байт): ${data.toHexText()}")

    val operation = context.encrypt(data).thenCompose { encrypted ->
        println("Зашифрованные данные (${encrypted.size} байт): ${encrypted.toHexText()}")
        context.decrypt(encrypted).thenAccept { decrypted ->
            println("Расшифрованные данные (${decrypted.size} байт): ${decrypted.toHexText()}")
            check(decrypted.contentEquals(data))
            println("Проверка восстановления: OK")
        }
    }
    operation.join()
}

private fun demonstrateFeistel() {
    val key = byteArrayOf(0x06, 0x03)
    val block = byteArrayOf(0x0A, 0x0C)
    val cipher = createDemoCipher()
    cipher.setKey(key)

    val encrypted = cipher.encrypt(block)
    val decrypted = cipher.decrypt(encrypted)

    println("Пункт 3. Сеть Фейстеля")
    println("Учебный пример: 2 раунда, F(R, K) = R XOR K.")
    println("Блок состоит из двух байтов: левый и правый.")
    println("Раундовые ключи: ${key.toHexText()}")
    println("Исходный блок: ${block.toBitString()}")
    println("Зашифрованный блок: ${encrypted.toBitString()}")
    println("Расшифрованный блок: ${decrypted.toBitString()}")
    check(encrypted.contentEquals(byteArrayOf(0x0F, 0x00)))
    check(decrypted.contentEquals(block))
    println("Проверка восстановления: OK")
}

private fun createDemoCipher(): FeistelNetwork = FeistelNetwork(
    keyExpansion = DemoKeyExpansion(),
    roundTransformation = DemoRoundTransformation(),
    blockSize = 2
)

private class DemoKeyExpansion : KeyExpansion {
    override fun generateRoundKeys(key: ByteArray): Array<ByteArray> {
        require(key.size == 2) { "В учебном примере ключ должен содержать 2 байта" }
        return Array(key.size) { byteArrayOf(key[it]) }
    }
}

private class DemoRoundTransformation : RoundTransformation {
    override fun transform(block: ByteArray, roundKey: ByteArray): ByteArray {
        require(block.size == 1 && roundKey.size == 1) {
            "В учебном примере половина блока и раундовый ключ содержат по одному байту"
        }
        return byteArrayOf((block[0].toInt() xor roundKey[0].toInt()).toByte())
    }
}

private fun ByteArray.toBitString(): String = joinToString(" ") {
    (it.toInt() and 0xFF).toString(2).padStart(8, '0')
}

private fun ByteArray.toHexText(): String = joinToString(" ") {
    (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase()
}
