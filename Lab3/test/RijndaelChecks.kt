import java.util.Random
import java.util.concurrent.Callable
import java.util.concurrent.Executors

object RijndaelChecks {
    private var checks = 0

    fun run(): Int {
        checks = 0
        knownAnswers()
        tablesAndRoundKeys()
        roundTrips()
        parallelCalls()
        invalidArguments()
        return checks
    }

    private fun verify(condition: Boolean, description: String) {
        check(condition) { description }
        checks++
    }

    private fun hex(text: String): ByteArray {
        require(text.length % 2 == 0)
        val result = ByteArray(text.length / 2)
        for (i in result.indices) result[i] = text.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        return result
    }

    private fun knownAnswers() {
        // контрольные значения aes из fips 197, остальных размеров — из официальных тестов bouncy castle
        // https://raw.githubusercontent.com/bcgit/bc-java/main/core/src/test/java/org/bouncycastle/crypto/test/RijndaelTest.java
        val vectors = arrayOf(
            arrayOf("000102030405060708090A0B0C0D0E0F", "00112233445566778899AABBCCDDEEFF", "69C4E0D86A7B0430D8CDB78070B4C55A"),
            arrayOf("000102030405060708090A0B0C0D0E0F1011121314151617", "00112233445566778899AABBCCDDEEFF", "DDA97CA4864CDFE06EAF70A0EC0D7191"),
            arrayOf("000102030405060708090A0B0C0D0E0F101112131415161718191A1B1C1D1E1F", "00112233445566778899AABBCCDDEEFF", "8EA2B7CA516745BFEAFC49904B496089"),
            arrayOf("2b7e151628aed2a6abf7158809cf4f3c", "3243f6a8885a308d313198a2e03707344a4093822299f31d", "b24d275489e82bb8f7375e0d5fcdb1f481757c538b65148a"),
            arrayOf("2b7e151628aed2a6abf7158809cf4f3c762e7160f38b4da5", "3243f6a8885a308d313198a2e03707344a4093822299f31d", "725ae43b5f3161de806a7c93e0bca93c967ec1ae1b71e1cf"),
            arrayOf("2b7e151628aed2a6abf7158809cf4f3c762e7160f38b4da56a784d9045190cfe", "3243f6a8885a308d313198a2e03707344a4093822299f31d", "0ebacf199e3315c2e34b24fcc7c46ef4388aa475d66c194c"),
            arrayOf("2b7e151628aed2a6abf7158809cf4f3c", "3243f6a8885a308d313198a2e03707344a4093822299f31d0082efa98ec4e6c8", "7d15479076b69a46ffb3b3beae97ad8313f622f67fedb487de9f06b9ed9c8f19"),
            arrayOf("2b7e151628aed2a6abf7158809cf4f3c762e7160f38b4da5", "3243f6a8885a308d313198a2e03707344a4093822299f31d0082efa98ec4e6c8", "5d7101727bb25781bf6715b0e6955282b9610e23a43c2eb062699f0ebf5887b2"),
            arrayOf("2b7e151628aed2a6abf7158809cf4f3c762e7160f38b4da56a784d9045190cfe", "3243f6a8885a308d313198a2e03707344a4093822299f31d0082efa98ec4e6c8", "a49406115dfb30a40418aafa4869b7c6a886ff31602a7dd19c889dc64f7e4e7a")
        )
        for (vector in vectors) {
            val key = hex(vector[0])
            val input = hex(vector[1])
            val expected = hex(vector[2])
            val originalKey = key.copyOf()
            val cipher = RijndaelCipher(input.size)
            cipher.setKey(key)
            key.fill(0)
            val restored = cipher.decrypt(expected)
            verify(restored.contentEquals(input), "контрольное расшифрование ${input.size}/${originalKey.size}")
            val encrypted = cipher.encrypt(input)
            verify(encrypted.contentEquals(expected), "контрольное шифрование ${input.size}/${originalKey.size}")
            verify(input.contentEquals(hex(vector[1])), "исходный блок изменён")
            verify(expected.contentEquals(hex(vector[2])), "шифротекст изменён при расшифровании")
            encrypted.fill(0)
            restored.fill(0)
            verify(cipher.encrypt(input).contentEquals(expected), "изменение результата испортило состояние шифра")
            cipher.setKey(ByteArray(originalKey.size))
            cipher.setKey(originalKey)
            verify(cipher.encrypt(input).contentEquals(expected), "повторная установка ключа")
            verify(originalKey.contentEquals(hex(vector[0])), "установка ключа изменила его массив")
        }
    }

    private fun tablesAndRoundKeys() {
        for (modulus in GaloisField.irreducibleModuli()) {
            val tables = RijndaelTables(modulus)
            val inverseValues = ByteArray(256)
            for (i in 0..255) inverseValues[i] = tables.inverseSubstitute(i.toByte())
            val seen = BooleanArray(256)
            for (i in 0..255) {
                val value = tables.substitute(i.toByte())
                verify(!seen[value.toInt() and 255], "s-матрица должна быть перестановкой")
                seen[value.toInt() and 255] = true
                verify(inverseValues[value.toInt() and 255] == i.toByte(), "обратная s-матрица")
                for (coefficient in intArrayOf(1, 2, 3, 9, 11, 13, 14)) {
                    verify(tables.multiply(i.toByte(), coefficient) == GaloisField.multiply(i.toByte(), coefficient.toByte(), modulus), "таблица умножения")
                }
            }
        }
        val standard = RijndaelTables(0x1B)
        verify(standard.substitute(0) == 0x63.toByte(), "s(0) = 63")
        verify(standard.substitute(0x53) == 0xED.toByte(), "s(53) = ed")
        verify(standard.inverseSubstitute(0) == 0x52.toByte(), "обратная s(0) = 52")
        for (blockSize in intArrayOf(16, 24, 32)) {
            for (keySize in intArrayOf(16, 24, 32)) {
                val expansion = RijndaelKeyExpansion(blockSize)
                val input = ByteArray(keySize) { it.toByte() }
                val expectedInput = input.copyOf()
                val keys = expansion.generateRoundKeys(input)
                verify(keys.size == maxOf(blockSize / 4, keySize / 4) + 7, "количество раундовых ключей")
                for (key in keys) verify(key.size == blockSize, "размер раундового ключа")
                verify(input.contentEquals(expectedInput), "расширение изменило ключ")
                val snapshot = Array(keys.size) { keys[it].copyOf() }
                val otherKeys = expansion.generateRoundKeys(ByteArray(keySize))
                for (i in keys.indices) verify(keys[i].contentEquals(snapshot[i]), "повторное расширение испортило прошлые ключи")
                otherKeys[0].fill(99)
                verify(keys[0].contentEquals(snapshot[0]), "массивы расширений разделяют память")
            }
        }
    }

    private fun roundTrips() {
        val random = Random(20261114)
        for (modulus in GaloisField.irreducibleModuli()) {
            for (blockSize in intArrayOf(16, 24, 32)) {
                for (keySize in intArrayOf(16, 24, 32)) {
                    val cipher = RijndaelCipher(blockSize, modulus)
                    val key = ByteArray(keySize)
                    random.nextBytes(key)
                    cipher.setKey(key)
                    for (sample in 0 until 5) {
                        val input = ByteArray(blockSize)
                        if (sample > 0) random.nextBytes(input)
                        val encrypted = cipher.encrypt(input)
                        verify(encrypted.size == blockSize, "размер результата")
                        verify(cipher.decrypt(encrypted).contentEquals(input), "обратимость ${modulus.toInt() and 255}/$blockSize/$keySize")
                        verify(cipher.encrypt(input).contentEquals(encrypted), "повторное использование раундовых ключей")
                    }
                }
            }
        }
    }

    private fun parallelCalls() {
        val pool = Executors.newFixedThreadPool(4)
        try {
            for (blockSize in intArrayOf(16, 24, 32)) {
                val cipher = RijndaelCipher(blockSize)
                cipher.setKey(ByteArray(32) { it.toByte() })
                val inputs = Array(24) { index -> ByteArray(blockSize) { (index * 7 + it).toByte() } }
                val expected = Array(inputs.size) { cipher.encrypt(inputs[it]) }
                val tasks = ArrayList<Callable<Boolean>>()
                for (i in inputs.indices) {
                    tasks.add(Callable {
                        cipher.encrypt(inputs[i]).contentEquals(expected[i]) &&
                            cipher.decrypt(expected[i]).contentEquals(inputs[i])
                    })
                }
                for (result in pool.invokeAll(tasks)) verify(result.get(), "одновременная обработка блоков")
            }
        } finally {
            pool.shutdown()
        }
    }

    private fun invalidArguments() {
        for (size in intArrayOf(0, 8, 20, 28, 40)) expectInvalid { RijndaelCipher(size) }
        expectInvalid { RijndaelCipher(16, 0) }
        expectInvalid { RijndaelCipher(16, 1) }
        val cipher = RijndaelCipher()
        expectInvalid { cipher.encrypt(ByteArray(16)) }
        expectInvalid { cipher.decrypt(ByteArray(16)) }
        for (size in intArrayOf(0, 1, 8, 20, 28, 33)) expectInvalid { cipher.setKey(ByteArray(size)) }
        cipher.setKey(ByteArray(16))
        for (size in intArrayOf(0, 1, 15, 17, 32)) {
            expectInvalid { cipher.encrypt(ByteArray(size)) }
            expectInvalid { cipher.decrypt(ByteArray(size)) }
        }
    }

    private fun expectInvalid(action: () -> Unit) {
        try {
            action()
        } catch (error: IllegalArgumentException) {
            checks++
            return
        } catch (error: IllegalStateException) {
            checks++
            return
        }
        error("ошибочный аргумент принят")
    }
}
