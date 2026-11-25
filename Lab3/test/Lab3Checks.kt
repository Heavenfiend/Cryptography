import java.util.Random
import java.util.concurrent.CompletionException
import java.util.concurrent.atomic.AtomicReference

object Lab3Checks {
    private var checks = 0

    @JvmStatic
    fun main(args: Array<String>) {
        val field = GaloisFieldChecks.run()
        println("Полиномы и GF(2^8): $field проверок")
        val cipher = RijndaelChecks.run()
        println("Rijndael: $cipher проверок")
        modeVectors()
        paddingChecks()
        contextChecks()
        println("Контекст, режимы и набивки: $checks проверок")
        println("Всего: ${field + cipher + checks}. Все проверки пройдены")
    }

    private fun verify(condition: Boolean, message: String) {
        check(condition) { message }
        checks++
    }

    private fun hex(text: String): ByteArray {
        val result = ByteArray(text.length / 2)
        for (i in result.indices) result[i] = text.substring(2 * i, 2 * i + 2).toInt(16).toByte()
        return result
    }

    private fun modeVectors() {
        // независимые контрольные значения из nist sp 800-38a, приложение f
        val key = hex("2b7e151628aed2a6abf7158809cf4f3c")
        val data = hex("6bc1bee22e409f96e93d7e117393172aae2d8a571e03ac9c9eb76fac45af8e5130c81c46a35ce411e5fbc1191a0a52eff69f2445df4f9b17ad2b417be66c3710")
        val vectors = arrayOf(
            arrayOf("ECB", "000102030405060708090a0b0c0d0e0f", "3ad77bb40d7a3660a89ecaf32466ef97f5d3d58503b9699de785895a96fdbaaf43b1cd7f598ece23881b00e3ed0306887b0c785e27e8ad3f8223207104725dd4"),
            arrayOf("CBC", "000102030405060708090a0b0c0d0e0f", "7649abac8119b246cee98e9b12e9197d5086cb9b507219ee95db113a917678b273bed6b8e3c1743b7116e69e222295163ff1caa1681fac09120eca307586e1a7"),
            arrayOf("CFB", "000102030405060708090a0b0c0d0e0f", "3b3fd92eb72dad20333449f8e83cfb4ac8a64537a0b3a93fcde3cdad9f1ce58b26751f67a3cbb140b1808cf187a4f4dfc04b05357c5d1c0eeac4c66f9ff7f2e6"),
            arrayOf("OFB", "000102030405060708090a0b0c0d0e0f", "3b3fd92eb72dad20333449f8e83cfb4a7789508d16918f03f53c52dac54ed8259740051e9c5fecf64344f7a82260edcc304c6528f659c77866a510d9c1d6ae5e"),
            arrayOf("CTR", "f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff", "874d6191b620e3261bef6864990db6ce9806f66b7970fdff8617187bb9fffdff5ae4df3edbd5d35e5b4f09020db03eab1e031dda2fbe03d1792170a0f3009cee")
        )
        for (vector in vectors) {
            val context = SymmetricCipherContext(key, RijndaelCipher(), CipherMode.valueOf(vector[0]), PaddingMode.ZEROS, hex(vector[1]))
            val expected = hex(vector[2])
            verify(context.encrypt(data).join().contentEquals(expected), "режим ${vector[0]} не совпал с nist")
            verify(context.decrypt(expected).join().contentEquals(data), "расшифрование ${vector[0]} не совпало с nist")
        }
        val delta = ByteArray(16)
        delta[15] = 3
        val iv = ByteArray(16)
        val randomDelta = SymmetricCipherContext(key, RijndaelCipher(), CipherMode.RANDOM_DELTA, PaddingMode.ZEROS, iv, delta)
        val direct = RijndaelCipher()
        direct.setKey(key)
        val expected = ByteArray(data.size)
        for (i in 0..3) {
            val block = data.copyOfRange(16 * i, 16 * i + 16)
            block[15] = (block[15].toInt() xor (i * 3)).toByte()
            direct.encrypt(block).copyInto(expected, i * 16)
        }
        verify(randomDelta.encrypt(data).join().contentEquals(expected), "формула random delta")
        val pcbc = SymmetricCipherContext(key, RijndaelCipher(), CipherMode.PCBC, PaddingMode.ZEROS, iv)
        var feedback = iv.copyOf()
        for (i in 0..3) {
            val original = data.copyOfRange(16 * i, 16 * i + 16)
            val input = original.copyOf()
            for (j in input.indices) input[j] = (input[j].toInt() xor feedback[j].toInt()).toByte()
            val encrypted = direct.encrypt(input)
            encrypted.copyInto(expected, i * 16)
            for (j in feedback.indices) feedback[j] = (original[j].toInt() xor encrypted[j].toInt()).toByte()
        }
        verify(pcbc.encrypt(data).join().contentEquals(expected), "формула pcbc")
    }

    private fun paddingChecks() {
        for (mode in PaddingMode.entries) {
            val padding = BlockPadding(mode, 8)
            val data = byteArrayOf(1, 2, 3, 4, 5)
            val padded = padding.add(data)
            verify(padded.size == 8, "размер набивки $mode")
            if (mode == PaddingMode.ZEROS) verify(padded.copyOfRange(5, 8).contentEquals(byteArrayOf(0, 0, 0)), "zeros")
            if (mode == PaddingMode.ANSI_X923) verify(padded.copyOfRange(5, 8).contentEquals(byteArrayOf(0, 0, 3)), "ansi x923")
            if (mode == PaddingMode.PKCS7) verify(padded.copyOfRange(5, 8).contentEquals(byteArrayOf(3, 3, 3)), "pkcs7")
            if (mode == PaddingMode.ISO_10126) verify(padded.last() == 3.toByte(), "iso10126")
            verify(padding.remove(padded).contentEquals(data), "удаление набивки $mode")
        }
        expectInvalid { BlockPadding(PaddingMode.PKCS7, 8).remove(byteArrayOf(1, 2, 3, 4, 5, 6, 2, 3)) }
        expectInvalid { BlockPadding(PaddingMode.ANSI_X923, 8).remove(byteArrayOf(1, 2, 3, 4, 5, 1, 0, 3)) }
        expectInvalid { BlockPadding(PaddingMode.PKCS7, 8).remove(ByteArray(8)) }
        val zeros = BlockPadding(PaddingMode.ZEROS, 8)
        verify(zeros.remove(zeros.add(byteArrayOf(1, 0))).contentEquals(byteArrayOf(1)), "неоднозначность zeros должна быть явной")
    }

    private fun contextChecks() {
        val random = Random(31415)
        val key = ByteArray(32)
        random.nextBytes(key)
        for (blockSize in intArrayOf(16, 24, 32)) {
            for (mode in CipherMode.entries) {
                for (padding in PaddingMode.entries) {
                    val iv = ByteArray(blockSize)
                    iv[blockSize - 1] = 0xFE.toByte()
                    val delta = ByteArray(blockSize)
                    delta[blockSize - 1] = 3
                    val context: SymmetricCipherContext
                    if (mode == CipherMode.RANDOM_DELTA) {
                        context = SymmetricCipherContext(key, RijndaelCipher(blockSize), mode, padding, iv, delta)
                    } else {
                        context = SymmetricCipherContext(key, RijndaelCipher(blockSize), mode, padding, iv)
                    }
                    for (length in intArrayOf(0, 1, blockSize - 1, blockSize, blockSize + 1, blockSize * 3)) {
                        val data = ByteArray(length)
                        random.nextBytes(data)
                        if (length > 0 && padding == PaddingMode.ZEROS) data[length - 1] = 1
                        val original = data.copyOf()
                        val result = AtomicReference<ByteArray>()
                        val future = context.encrypt(data, result)
                        data.fill(0)
                        future.join()
                        val encrypted = result.get().copyOf()
                        val decoded = context.decrypt(encrypted)
                        encrypted.fill(0)
                        verify(decoded.join().contentEquals(original), "контекст $blockSize/$mode/$padding/$length")
                    }
                }
            }
        }
        expectInvalid { SymmetricCipherContext(key, RijndaelCipher(), CipherMode.CBC, PaddingMode.PKCS7) }
        expectInvalid { SymmetricCipherContext(key, RijndaelCipher(), CipherMode.CTR, PaddingMode.PKCS7, ByteArray(8)) }
        expectInvalid { SymmetricCipherContext(key, RijndaelCipher(), CipherMode.RANDOM_DELTA, PaddingMode.PKCS7, ByteArray(16), ByteArray(16)) }
        val maxIv = ByteArray(16) { 0xFF.toByte() }
        val context = SymmetricCipherContext(key, RijndaelCipher(), CipherMode.CTR, PaddingMode.ZEROS, maxIv)
        expectInvalid { context.encrypt(ByteArray(32)).join() }
        expectInvalid { context.decrypt(ByteArray(1)).join() }
    }

    private fun expectInvalid(action: () -> Unit) {
        try {
            action()
        } catch (error: IllegalArgumentException) {
            checks++
            return
        } catch (error: CompletionException) {
            if (error.cause is IllegalArgumentException) {
                checks++
                return
            }
            throw error
        }
        error("Неверные параметры не отклонены")
    }
}
