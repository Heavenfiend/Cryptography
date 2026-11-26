import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference
import java.util.stream.IntStream

// шифрование и дешифрование массивов и файлов в различных режимах
class SymmetricCipherContext(
    key: ByteArray,
    private val cipher: SymmetricCipher,
    private val mode: CipherMode,
    paddingMode: PaddingMode,
    initializationVector: ByteArray? = null,
    vararg additionalArguments: Any
) {
    private val blockSize = cipher.blockSize
    private val padding = BlockPadding(paddingMode, blockSize)
    private val iv = initializationVector?.copyOf()
    private val arguments = additionalArguments.map { if (it is ByteArray) it.copyOf() else it }.toTypedArray()
    private val randomDelta: RandomDeltaMode?

    init {
        require(key.isNotEmpty()) { "Ключ не должен быть пустым" }

        // проверяем наличие и размер iv для всех режимов кроме ECB
        if (mode != CipherMode.ECB) {
            require(iv != null) { "Для режима $mode нужен вектор инициализации" }
            require(iv.size == blockSize) { "Размер IV должен совпадать с размером блока" }
        }

        // проверяем дополнительные параметры для Random Delta
        randomDelta = if (mode == CipherMode.RANDOM_DELTA) {
            require(arguments.size == 1 && arguments[0] is ByteArray) {
                "Для Random Delta нужен один дополнительный параметр: delta (ByteArray)"
            }
            RandomDeltaMode(arguments[0] as ByteArray, blockSize)
        } else {
            require(arguments.isEmpty()) { "Дополнительные параметры для режима $mode не предусмотрены" }
            null
        }

        // устанавливаем секретный ключ
        cipher.setKey(key.copyOf())
    }

    // асинхронно добавляем набивку и шифруем массив
    fun encrypt(data: ByteArray): CompletableFuture<ByteArray> {
        val snapshot = data.copyOf()
        return CompletableFuture.supplyAsync { process(padding.add(snapshot), true) }
    }

    // асинхронно дешифруем массив и удаляем набивку
    fun decrypt(data: ByteArray): CompletableFuture<ByteArray> {
        val snapshot = data.copyOf()
        return CompletableFuture.supplyAsync { padding.remove(process(snapshot, false)) }
    }

    // шифруем массив и сохраняем результат в переданную ссылку
    fun encrypt(data: ByteArray, result: AtomicReference<ByteArray>): CompletableFuture<Void> =
        encrypt(data).thenAccept { result.set(it) }

    // дешифруем массив и сохраняем результат в переданную ссылку
    fun decrypt(data: ByteArray, result: AtomicReference<ByteArray>): CompletableFuture<Void> =
        decrypt(data).thenAccept { result.set(it) }

    // считываем байты файла и записываем результат шифрования в другой файл
    fun encrypt(inputPath: Path, outputPath: Path): CompletableFuture<Void> =
        CompletableFuture.runAsync {
            val data = Files.readAllBytes(inputPath)
            Files.write(outputPath, process(padding.add(data), true))
        }

    // дешифруем содержимое файла и удаляем набивку
    fun decrypt(inputPath: Path, outputPath: Path): CompletableFuture<Void> =
        CompletableFuture.runAsync {
            val data = Files.readAllBytes(inputPath)
            Files.write(outputPath, padding.remove(process(data, false)))
        }

    // перегрузки для передачи путей в виде строк
    fun encrypt(inputPath: String, outputPath: String): CompletableFuture<Void> =
        encrypt(Path.of(inputPath), Path.of(outputPath))

    fun decrypt(inputPath: String, outputPath: String): CompletableFuture<Void> =
        decrypt(Path.of(inputPath), Path.of(outputPath))

    // обработка блоков в зависимости от выбранного режима шифрования
    private fun process(data: ByteArray, encrypting: Boolean): ByteArray {

        // Random Delta реализован в отдельном классе
        if (mode == CipherMode.RANDOM_DELTA) {
            return randomDelta!!.process(data, cipher, iv!!.copyOf(), encrypting)
        }

        require(data.size % blockSize == 0) { "Длина данных должна быть кратна размеру блока" }

        val result = ByteArray(data.size)
        val count = data.size / blockSize

        // получаем блок данных по его индексу
        fun block(index: Int) = data.copyOfRange(index * blockSize, (index + 1) * blockSize)

        // записываем обработанный блок на нужную позицию
        fun write(index: Int, value: ByteArray) {
            require(value.size == blockSize) { "Алгоритм вернул блок неверного размера" }
            value.copyInto(result, index * blockSize)
        }

        // параллельно обрабатываем независимые блоки
        fun parallel(action: (Int) -> Unit) {
            IntStream.range(0, count).parallel().forEach { action(it) }
        }

        when (mode) {

            // ECB каждый блок шифруется и дешифруется независимо
            CipherMode.ECB -> parallel { i ->
                write(i, applyCipher(block(i), encrypting))
            }

            CipherMode.CBC -> if (encrypting) {

                // перед шифрованием выполняем xor с предыдущим шифроблоком
                var previous = iv!!.copyOf()
                for (i in 0 until count) {
                    previous = applyCipher(xor(block(i), previous), true)
                    write(i, previous)
                }
            } else {

                // после дешифрования выполняем xor с предыдущим шифроблоком
                parallel { i ->
                    val previous = if (i == 0) iv!! else block(i - 1)
                    write(i, xor(applyCipher(block(i), false), previous))
                }
            }

            // PCBC обратная связь через xor исходного и зашифрованного блоков
            CipherMode.PCBC -> {
                var feedback = iv!!.copyOf()

                for (i in 0 until count) {
                    val input = block(i)
                    val output = if (encrypting) applyCipher(xor(input, feedback), true)
                    else xor(applyCipher(input, false), feedback)

                    write(i, output)
                    feedback = xor(input, output)
                }
            }

            CipherMode.CFB -> if (encrypting) {

                // шифруем предыдущий шифроблок и выполняем xor с исходным блоком
                var feedback = iv!!.copyOf()
                for (i in 0 until count) {
                    feedback = xor(block(i), applyCipher(feedback, true))
                    write(i, feedback)
                }
            } else {

                // при дешифровании также используем прямое шифрование
                parallel { i ->
                    val previous = if (i == 0) iv!! else block(i - 1)
                    write(i, xor(block(i), applyCipher(previous, true)))
                }
            }

            // OFB последовательно шифруем обратную связь и выполняем xor с данными
            CipherMode.OFB -> {
                var feedback = iv!!.copyOf()

                for (i in 0 until count) {
                    feedback = applyCipher(feedback, true)
                    write(i, xor(block(i), feedback))
                }
            }

            // CTR шифруем значение счетчика и выполняем xor с блоком
            CipherMode.CTR -> {

                // заранее проверяем счетчик на переполнение
                if (count > 0) counterAt(count - 1)

                parallel { i ->
                    write(i, xor(block(i), applyCipher(counterAt(i), true)))
                }
            }

            CipherMode.RANDOM_DELTA -> error("Random Delta обрабатывается отдельно")
        }

        return result
    }

    // шифруем или дешифруем один блок выбранным алгоритмом
    private fun applyCipher(block: ByteArray, encrypting: Boolean): ByteArray {
        val result = if (encrypting) cipher.encrypt(block.copyOf()) else cipher.decrypt(block.copyOf())
        require(result.size == blockSize) { "Алгоритм вернул блок неверного размера" }
        return result.copyOf()
    }

    // побайтовый xor двух массивов одинаковой длины
    private fun xor(left: ByteArray, right: ByteArray): ByteArray {
        require(left.size == right.size) { "Для XOR нужны массивы одинаковой длины" }
        return ByteArray(left.size) { (left[it].toInt() xor right[it].toInt()).toByte() }
    }

    // вычисляем значение счетчика CTR как iv плюс индекс блока
    private fun counterAt(index: Int): ByteArray {
        val counter = iv!!.copyOf()
        var carry = index.toLong()

        // выполняем побайтовое сложение с переносом разряда
        for (i in counter.lastIndex downTo 0) {
            val sum = (counter[i].toInt() and 0xFF) + (carry and 0xFF)
            counter[i] = sum.toByte()
            carry = (carry ushr 8) + (sum ushr 8)
        }

        require(carry == 0L) { "Переполнение счётчика CTR: нужен другой IV или меньший объём данных" }

        return counter
    }
}