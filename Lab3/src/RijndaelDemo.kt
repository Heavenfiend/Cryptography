import java.nio.file.Files
import java.nio.file.Path
import java.util.Random

object RijndaelDemo {
    fun run(inputDirectory: Path = Path.of("demo-input"), outputDirectory: Path = Path.of("demo-results")) {
        val input = inputDirectory.toAbsolutePath().normalize()
        val output = outputDirectory.toAbsolutePath().normalize()
        require(Files.isDirectory(input)) { "Папка с примерами не найдена: $input" }
        require(!output.startsWith(input) && !input.startsWith(output)) { "Папки исходных данных и результатов должны быть отдельными" }
        val files = ArrayList<Path>()
        Files.list(input).use { paths ->
            val iterator = paths.sorted().iterator()
            while (iterator.hasNext()) {
                val path = iterator.next()
                if (Files.isRegularFile(path)) files.add(path)
            }
        }
        require(files.isNotEmpty()) { "Добавьте в $input текст, музыку или изображения" }
        Files.createDirectories(output)
        val run = Files.createTempDirectory(output, "run-")
        val random = Random()
        val sizes = intArrayOf(16, 24, 32)
        val moduli = GaloisField.irreducibleModuli().take(3)
        var checks = 0

        println("\n3. Rijndael: массивы и файлы")
        println("Сочетаний блока и ключа: ${sizes.size * sizes.size}, режимов: ${CipherMode.entries.size}, набивок: ${PaddingMode.entries.size}, модулей: ${moduli.size}")
        println("Zeros: исходная длина сохраняется в отчёте, чтобы восстановить конечные нули данных")
        println("Random Delta: учебный вариант из первой лабы, C[i] = E(P[i] XOR (IV + i * delta))")
        println("Результаты: $run")

        Files.newBufferedWriter(run.resolve("report.csv")).use { report ->
            report.write("kind;name;block_bytes;key_bytes;modulus;mode;padding;original_bytes;encrypted_bytes;restored_bytes;equal;key;iv;delta\n")
            for (module in moduli) {
                for (blockSize in sizes) {
                    for (keySize in sizes) {
                        val key = ByteArray(keySize)
                        random.nextBytes(key)
                        val cipher = RijndaelCipher(blockSize, module)
                        val moduleHex = "1" + hex(byteArrayOf(module))
                        val directory = run.resolve("m${moduleHex}-b${blockSize}-k${keySize}")
                        for (mode in CipherMode.entries) {
                            for (padding in PaddingMode.entries) {
                                val iv = if (mode == CipherMode.ECB) null else ByteArray(blockSize)
                                if (iv != null) {
                                    random.nextBytes(iv)
                                    if (mode == CipherMode.CTR) iv[0] = (iv[0].toInt() and 0x7F).toByte()
                                }
                                val delta: ByteArray?
                                val context: SymmetricCipherContext
                                if (mode == CipherMode.RANDOM_DELTA) {
                                    delta = ByteArray(blockSize)
                                    random.nextBytes(delta)
                                    delta[delta.lastIndex] = (delta.last().toInt() or 1).toByte()
                                    context = SymmetricCipherContext(key, cipher, mode, padding, iv, delta)
                                } else {
                                    delta = null
                                    context = SymmetricCipherContext(key, cipher, mode, padding, iv)
                                }
                                val parameters = "$blockSize;$keySize;0x$moduleHex;$mode;$padding"
                                val keyAndVectors = "${hex(key)};${hex(iv)};${hex(delta)}"
                                val lengths = intArrayOf(0, 1, blockSize - 1, blockSize, blockSize + 1, 2 * blockSize, 257, 1024)
                                for (length in lengths) {
                                    val original = ByteArray(length)
                                    random.nextBytes(original)
                                    if (length > 0) original[length - 1] = 0
                                    val encrypted = context.encrypt(original).join()
                                    var restored = context.decrypt(encrypted).join()
                                    if (padding == PaddingMode.ZEROS) restored = restoreZeros(restored, length)
                                    check(original.contentEquals(restored)) { "Не восстановлен массив: $parameters, $length" }
                                    report.write("array;random-$length;$parameters;$length;${encrypted.size};${restored.size};true;$keyAndVectors\n")
                                    checks++
                                }
                                val target = directory.resolve("$mode-$padding")
                                val encryptedDirectory = Files.createDirectories(target.resolve("encrypted"))
                                val restoredDirectory = Files.createDirectories(target.resolve("restored"))
                                for (source in files) {
                                    val name = source.fileName.toString()
                                    val encrypted = encryptedDirectory.resolve("$name.enc")
                                    val restored = restoredDirectory.resolve(name)
                                    val length = Files.size(source)
                                    require(length <= Int.MAX_VALUE) { "Для демонстрации файл должен помещаться в массив байтов" }
                                    context.encrypt(source, encrypted).join()
                                    context.decrypt(encrypted, restored).join()
                                    if (padding == PaddingMode.ZEROS) {
                                        val decoded = Files.readAllBytes(restored)
                                        Files.write(restored, restoreZeros(decoded, length.toInt()))
                                    }
                                    check(Files.mismatch(source, restored) == -1L) { "Не восстановлен файл: $name, $parameters" }
                                    report.write("file;${csv(name)};$parameters;$length;${Files.size(encrypted)};${Files.size(restored)};true;$keyAndVectors\n")
                                    checks++
                                }
                            }
                        }
                        println("Модуль 0x$moduleHex | блок ${blockSize * 8} бит | ключ ${keySize * 8} бит: все режимы и набивки OK")
                    }
                }
            }
        }
        println("Проверок восстановления: $checks. Все данные совпали побайтно")
        println("Отчёт с параметрами каждой проверки: ${run.resolve("report.csv")}")
    }

    // нулевая набивка сама не хранит длину: по сохранённой длине возвращаем исходные конечные нули
    private fun restoreZeros(data: ByteArray, originalLength: Int): ByteArray {
        check(data.size <= originalLength) { "После удаления нулевой набивки осталось слишком много данных" }
        return data.copyOf(originalLength)
    }

    private fun hex(data: ByteArray?): String {
        if (data == null) return ""
        val result = StringBuilder()
        for (value in data) result.append((value.toInt() and 255).toString(16).padStart(2, '0').uppercase())
        return result.toString()
    }

    private fun csv(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
}
