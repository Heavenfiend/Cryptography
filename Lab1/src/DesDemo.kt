import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference
import kotlin.random.Random

object DesDemo {
    private val algorithm = "DES"
    private val keySize = 8
    private val blockSize = newCipher().blockSize


    private fun newCipher(): SymmetricCipher = DesCipher()

    fun run(inputDirectory: Path = Path.of("demo-input"), outputDirectory: Path = Path.of("demo-results")) {
        val input = inputDirectory.toAbsolutePath().normalize()
        val output = outputDirectory.toAbsolutePath().normalize()
        require(Files.isDirectory(input)) { "Папка с примерами не найдена: $input" }
        require(!input.startsWith(output)) { "Папка результатов не должна содержать папку исходных файлов" }
        val files = Files.walk(input).use { paths ->
            paths.filter { Files.isRegularFile(it) && !it.startsWith(output) }.sorted().toList()
        }
        require(files.isNotEmpty()) { "Добавьте файлы для проверки в $input" }
        Files.createDirectories(output)
        val runDirectory = Files.createTempDirectory(output, "run-")
        val random = Random(2026)
        val key = random.nextBytes(keySize)
        val lengths = listOf(0, 1, blockSize - 1, blockSize, blockSize + 1, 31, 1024)
        val arrays = lengths.associateWith { length ->
            random.nextBytes(length).also {
                if (it.isNotEmpty()) it[it.lastIndex] = 0
            }
        }
        Files.writeString(runDirectory.resolve("parameters.txt"),
            "$algorithm demonstration\nseed=2026\nkey=${hex(key)}\nkey_bytes=$keySize\nblock_bytes=$blockSize\npadding=PKCS7\n" +
                "IV and delta for each operation are recorded in report.csv.\n" +
                "Random Delta uses the educational convention documented in RandomDeltaMode.kt.\n")

        val taskNumber = 5
        println("Пункт $taskNumber: $algorithm для массивов байтов и файлов")
        println("Ключ: $keySize байт. Блок: $blockSize байт.")
        println("Набивка: PKCS7. Результаты: $runDirectory")
        println("Random Delta: учебный вариант, описанный в RandomDeltaMode.kt")
        val jobs = mutableListOf<CompletableFuture<Result>>()

        for (mode in CipherMode.entries) {
            for ((length, original) in arrays) {
                val parameters = newParameters(mode, random)
                val context = context(key, mode, parameters)
                val encrypted = AtomicReference<ByteArray>()
                val restored = AtomicReference<ByteArray>()
                val job = context.encrypt(original, encrypted)
                    .thenCompose { context.decrypt(encrypted.get(), restored) }
                    .thenApply {
                        check(original.contentEquals(restored.get())) { "Ошибка восстановления массива: $mode, $length" }
                        if (length == 31) {
                            val directory = runDirectory.resolve("arrays").resolve(mode.name)
                            Files.createDirectories(directory)
                            Files.write(directory.resolve("original.bin"), original)
                            Files.write(directory.resolve("encrypted.bin"), encrypted.get())
                            Files.write(directory.resolve("restored.bin"), restored.get())
                        }
                        Result("array", "random-$length", mode, original.size.toLong(),
                            encrypted.get().size.toLong(), restored.get().size.toLong(), parameters)
                    }
                jobs.add(job)
            }

            for (source in files) {
                val relative = input.relativize(source)
                val parameters = newParameters(mode, random)
                val context = context(key, mode, parameters)
                val encrypted = runDirectory.resolve("encrypted").resolve(mode.name)
                    .resolve(relative.toString() + "." + algorithm.lowercase())
                val restored = runDirectory.resolve("decrypted").resolve(mode.name).resolve(relative)
                Files.createDirectories(encrypted.parent)
                Files.createDirectories(restored.parent)
                val job = context.encrypt(source, encrypted)
                    .thenCompose { context.decrypt(encrypted, restored) }
                    .thenApply {
                        check(Files.mismatch(source, restored) == -1L) { "Файл восстановлен неверно: $relative, $mode" }
                        Result("file", relative.toString(), mode, Files.size(source),
                            Files.size(encrypted), Files.size(restored), parameters)
                    }
                jobs.add(job)
            }
        }

        CompletableFuture.allOf(*jobs.toTypedArray()).join()
        val results = jobs.map { it.join() }
        val header = "type;name;mode;padding;original_bytes;encrypted_bytes;restored_bytes;equal;iv;delta"
        val rows = results.map { result ->
            listOf(result.type, result.name, result.mode.name, "PKCS7", result.originalSize.toString(),
                result.encryptedSize.toString(), result.restoredSize.toString(), "true",
                hex(result.parameters.iv ?: byteArrayOf()), hex(result.parameters.delta ?: byteArrayOf()))
                .joinToString(";") { csvField(it) }
        }
        Files.writeString(runDirectory.resolve("report.csv"), (listOf(header) + rows).joinToString("\n") + "\n")

        for (mode in CipherMode.entries) {
            println("$mode | массивы длиной ${lengths.joinToString()} байт: OK")
            for (result in results.filter { it.type == "file" && it.mode == mode }) {
                println("$mode | ${result.name}: ${result.originalSize} -> ${result.encryptedSize} -> ${result.restoredSize} байт | OK")
            }
        }
        println("Проверок: ${results.size}. Все данные восстановлены побайтно.")
        println("Отчёт: ${runDirectory.resolve("report.csv")}")
    }

    private data class Parameters(val iv: ByteArray?, val delta: ByteArray?)
    private data class Result(val type: String, val name: String, val mode: CipherMode,
        val originalSize: Long, val encryptedSize: Long, val restoredSize: Long, val parameters: Parameters)

    private fun newParameters(mode: CipherMode, random: Random): Parameters {
        val iv = if (mode == CipherMode.ECB) null else random.nextBytes(blockSize)
        if (mode == CipherMode.CTR) iv!![0] = (iv[0].toInt() and 0x7F).toByte()
        val delta = if (mode == CipherMode.RANDOM_DELTA) random.nextBytes(blockSize).also {
            it[it.lastIndex] = (it.last().toInt() or 1).toByte()
        } else null
        return Parameters(iv, delta)
    }

    private fun context(key: ByteArray, mode: CipherMode, parameters: Parameters): SymmetricCipherContext =
        if (mode == CipherMode.RANDOM_DELTA) {
            SymmetricCipherContext(key, newCipher(), mode, PaddingMode.PKCS7, parameters.iv, parameters.delta!!)
        } else {
            SymmetricCipherContext(key, newCipher(), mode, PaddingMode.PKCS7, parameters.iv)
        }

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") {
        (it.toInt() and 0xFF).toString(16).padStart(2, '0').uppercase()
    }

    private fun csvField(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
}
