import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference
import java.util.stream.IntStream

class SymmetricCipherContext(
    key: ByteArray,
    private val cipher: SymmetricCipher, // объект шифра
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
        if (mode != CipherMode.ECB) {
            require(iv != null) { "Для режима $mode нужен вектор инициализации" }
            require(iv.size == blockSize) { "Размер IV должен совпадать с размером блока" }
        }
        randomDelta = if (mode == CipherMode.RANDOM_DELTA) {
            require(arguments.size == 1 && arguments[0] is ByteArray) {
                "Для Random Delta нужен один дополнительный параметр: delta (ByteArray)"
            }
            RandomDeltaMode(arguments[0] as ByteArray, blockSize)
        } else {
            require(arguments.isEmpty()) { "Дополнительные параметры для режима $mode не предусмотрены" }
            null
        }
        cipher.setKey(key.copyOf())
    }
    // шифрует массив: добавляет набивку и обрабатывает выбранным режимом
    fun encrypt(data: ByteArray): CompletableFuture<ByteArray> {
        val snapshot = data.copyOf()
        return CompletableFuture.supplyAsync { process(padding.add(snapshot), true) }
    }

    fun decrypt(data: ByteArray): CompletableFuture<ByteArray> {
        val snapshot = data.copyOf()
        return CompletableFuture.supplyAsync { padding.remove(process(snapshot, false)) }
    }

    // В Kotlin нет out/ref-параметра для переназначения массива у вызывающего кода.
    // AtomicReference служит контейнером для ссылки на результирующий массив.
    fun encrypt(data: ByteArray, result: AtomicReference<ByteArray>): CompletableFuture<Void> =
        encrypt(data).thenAccept { result.set(it) }

    fun decrypt(data: ByteArray, result: AtomicReference<ByteArray>): CompletableFuture<Void> =
        decrypt(data).thenAccept { result.set(it) }

    // Чтение и запись файла также выполняются в фоне. Файл загружается в память целиком.
    fun encrypt(inputPath: Path, outputPath: Path): CompletableFuture<Void> =
        CompletableFuture.runAsync {
            val data = Files.readAllBytes(inputPath)
            Files.write(outputPath, process(padding.add(data), true))
        }
    // расшифровывает массив и удаляет набивку
    fun decrypt(inputPath: Path, outputPath: Path): CompletableFuture<Void> =
        CompletableFuture.runAsync {
            val data = Files.readAllBytes(inputPath)
            Files.write(outputPath, padding.remove(process(data, false)))
        }
    // принимают пути строками, преобразуют их в Path
    fun encrypt(inputPath: String, outputPath: String): CompletableFuture<Void> =
        encrypt(Path.of(inputPath), Path.of(outputPath))

    fun decrypt(inputPath: String, outputPath: String): CompletableFuture<Void> =
        decrypt(Path.of(inputPath), Path.of(outputPath))

    private fun process(data: ByteArray, encrypting: Boolean): ByteArray {
        if (mode == CipherMode.RANDOM_DELTA) {
            return randomDelta!!.process(data, cipher, iv!!.copyOf(), encrypting)
        }
        require(data.size % blockSize == 0) { "Длина данных должна быть кратна размеру блока" }
        val result = ByteArray(data.size)
        val count = data.size / blockSize
        fun block(index: Int) = data.copyOfRange(index * blockSize, (index + 1) * blockSize) // помощник для получения блока
        fun write(index: Int, value: ByteArray) { // помощник для записи готового блока
            require(value.size == blockSize) { "Алгоритм вернул блок неверного размера" }
            value.copyInto(result, index * blockSize)
        } // помощник для параллельной обработки
        fun parallel(action: (Int) -> Unit) {
            IntStream.range(0, count).parallel().forEach { action(it) }
        }

        when (mode) {
            CipherMode.ECB -> parallel { i -> write(i, applyCipher(block(i), encrypting)) }
            CipherMode.CBC -> if (encrypting) {
                var previous = iv!!.copyOf()
                for (i in 0 until count) {
                    previous = applyCipher(xor(block(i), previous), true)
                    write(i, previous)
                }
            } else {
                parallel { i ->
                    val previous = if (i == 0) iv!! else block(i - 1)
                    write(i, xor(applyCipher(block(i), false), previous))
                }
            }
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
                var feedback = iv!!.copyOf()
                for (i in 0 until count) {
                    feedback = xor(block(i), applyCipher(feedback, true))
                    write(i, feedback)
                }
            } else {
                parallel { i ->
                    val previous = if (i == 0) iv!! else block(i - 1)
                    write(i, xor(block(i), applyCipher(previous, true)))
                }
            }
            CipherMode.OFB -> {
                var feedback = iv!!.copyOf()
                for (i in 0 until count) {
                    feedback = applyCipher(feedback, true)
                    write(i, xor(block(i), feedback))
                }
            }
            CipherMode.CTR -> {
                if (count > 0) counterAt(count - 1)
                parallel { i -> write(i, xor(block(i), applyCipher(counterAt(i), true))) }
            }
            CipherMode.RANDOM_DELTA -> error("Random Delta обрабатывается отдельно")
        }
        return result
    }

    private fun applyCipher(block: ByteArray, encrypting: Boolean): ByteArray {
        val result = if (encrypting) cipher.encrypt(block.copyOf()) else cipher.decrypt(block.copyOf())
        require(result.size == blockSize) { "Алгоритм вернул блок неверного размера" }
        return result.copyOf()
    }

    private fun xor(left: ByteArray, right: ByteArray): ByteArray {
        require(left.size == right.size) { "Для XOR нужны массивы одинаковой длины" }
        return ByteArray(left.size) { (left[it].toInt() xor right[it].toInt()).toByte() }
    }

    private fun counterAt(index: Int): ByteArray { // IV + номер блока
        val counter = iv!!.copyOf()
        var carry = index.toLong()
        for (i in counter.lastIndex downTo 0) {
            val sum = (counter[i].toInt() and 0xFF) + (carry and 0xFF)
            counter[i] = sum.toByte()
            carry = (carry ushr 8) + (sum ushr 8)
        }
        require(carry == 0L) { "Переполнение счётчика CTR: нужен другой IV или меньший объём данных" }
        return counter
    }
}
