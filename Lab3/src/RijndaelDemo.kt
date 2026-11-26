import java.nio.file.Files
import java.nio.file.Path
import java.util.Random

// демонстрация шифрования и дешифрования массивов и файлов
object RijndaelDemo {
    fun run(inputDirectory: Path = Path.of("demo-input"), outputDirectory: Path = Path.of("demo-results")) {
        val input = inputDirectory.toAbsolutePath().normalize()
        val output = outputDirectory.toAbsolutePath().normalize()

        // проверяем существование папки и корректность путей
        require(Files.isDirectory(input)) { "Папка с примерами не найдена: $input" }
        require(!output.startsWith(input) && !input.startsWith(output)) { "Папки исходных данных и результатов должны быть отдельными" }

        // собираем все файлы из входной папки
        val files = ArrayList<Path>()
        Files.list(input).use { paths ->
            val iterator = paths.sorted().iterator()
            while (iterator.hasNext()) {
                val path = iterator.next()
                if (Files.isRegularFile(path)) files.add(path)
            }
        }
        require(files.isNotEmpty()) { "Добавьте в $input текст, музыку или изображения" }

        // создаем отдельную папку для результатов текущего запуска
        Files.createDirectories(output)
        val run = Files.createTempDirectory(output, "run-")
        val random = Random()

        // размеры блока и ключа в байтах и первые три неприводимых модуля
        val sizes = intArrayOf(16, 24, 32)
        val moduli = GaloisField.irreducibleModuli().take(3)
        var checks = 0

        println("\n3. Rijndael: массивы и файлы")
        println("Сочетаний блока и ключа: ${sizes.size * sizes.size}, режимов: ${CipherMode.entries.size}, набивок: ${PaddingMode.entries.size}, модулей: ${moduli.size}")
        println("Zeros: маркер 0x80 и нули до конца блока")
        println("Random Delta: учебный вариант из первой лабы, C[i] = E(P[i] XOR (IV + i * delta))")
        println("Результаты: $run")

        // создаем отчет с параметрами и результатами всех проверок
        Files.newBufferedWriter(run.resolve("report.csv")).use { report ->
            report.write("kind;name;block_bytes;key_bytes;modulus;mode;padding;original_bytes;encrypted_bytes;restored_bytes;equal;key;iv;delta\n")

            // перебираем все сочетания модуля, размера блока и ключа
            for (module in moduli) {
                for (blockSize in sizes) {
                    for (keySize in sizes) {

                        // генерируем ключ и создаем экземпляр шифра
                        val key = ByteArray(keySize)
                        random.nextBytes(key)
                        val cipher = RijndaelCipher(blockSize, module)
                        val moduleHex = "1" + hex(byteArrayOf(module))
                        val directory = run.resolve("m${moduleHex}-b${blockSize}-k${keySize}")

                        // перебираем все режимы шифрования и набивки
                        for (mode in CipherMode.entries) {
                            for (padding in PaddingMode.entries) {

                                // генерируем вектор инициализации для всех режимов кроме ECB
                                val iv = if (mode == CipherMode.ECB) null else ByteArray(blockSize)
                                if (iv != null) {
                                    random.nextBytes(iv)

                                    // обнуляем старший бит счетчика в режиме CTR
                                    if (mode == CipherMode.CTR) iv[0] = (iv[0].toInt() and 0x7F).toByte()
                                }

                                val delta: ByteArray?
                                val context: SymmetricCipherContext

                                // для Random Delta дополнительно генерируем нечетную дельту
                                if (mode == CipherMode.RANDOM_DELTA) {
                                    delta = ByteArray(blockSize)
                                    random.nextBytes(delta)
                                    delta[delta.lastIndex] = (delta.last().toInt() or 1).toByte()
                                    context = SymmetricCipherContext(key, cipher, mode, padding, iv, delta)
                                } else {
                                    delta = null
                                    context = SymmetricCipherContext(key, cipher, mode, padding, iv)
                                }

                                // сохраняем параметры текущей комбинации для отчета
                                val parameters = "$blockSize;$keySize;0x$moduleHex;$mode;$padding"
                                val keyAndVectors = "${hex(key)};${hex(iv)};${hex(delta)}"

                                // проверяем массивы разных размеров включая граничные случаи
                                val lengths = intArrayOf(0, 1, blockSize - 1, blockSize, blockSize + 1, 2 * blockSize, 257, 1024)
                                for (length in lengths) {
                                    val original = ByteArray(length)
                                    random.nextBytes(original)

                                    // добавляем нулевой байт в конец для проверки удаления набивки
                                    if (length > 0) original[length - 1] = 0

                                    // шифруем массив и восстанавливаем исходные данные
                                    val encrypted = context.encrypt(original).join()
                                    val restored = context.decrypt(encrypted).join()

                                    // проверяем побайтовое совпадение массивов
                                    check(original.contentEquals(restored)) { "Не восстановлен массив: $parameters, $length" }
                                    report.write("array;random-$length;$parameters;$length;${encrypted.size};${restored.size};true;$keyAndVectors\n")
                                    checks++
                                }

                                // создаем папки для зашифрованных и восстановленных файлов
                                val target = directory.resolve("$mode-$padding")
                                val encryptedDirectory = Files.createDirectories(target.resolve("encrypted"))
                                val restoredDirectory = Files.createDirectories(target.resolve("decrypted"))

                                // шифруем и дешифруем каждый исходный файл
                                for (source in files) {
                                    val name = source.fileName.toString()
                                    val encrypted = encryptedDirectory.resolve("$name.enc")
                                    val restored = restoredDirectory.resolve(name)
                                    val length = Files.size(source)

                                    require(length <= Int.MAX_VALUE) { "Для демонстрации файл должен помещаться в массив байтов" }

                                    context.encrypt(source, encrypted).join()
                                    context.decrypt(encrypted, restored).join()

                                    // проверяем побайтовое совпадение файлов
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

        // выводим общее количество успешных проверок и путь к отчету
        println("Проверок восстановления: $checks. Все данные совпали побайтно")
        println("Отчёт с параметрами каждой проверки: ${run.resolve("report.csv")}")
    }

    // переводим массив байтов в шестнадцатеричную строку
    private fun hex(data: ByteArray?): String {
        if (data == null) return ""
        val result = StringBuilder()
        for (value in data) result.append((value.toInt() and 255).toString(16).padStart(2, '0').uppercase())
        return result.toString()
    }

    // экранируем кавычки в имени файла для записи в csv
    private fun csv(value: String): String = "\"" + value.replace("\"", "\"\"") + "\""
}
