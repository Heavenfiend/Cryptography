@file:JvmName("Lab3Main")

import java.math.BigInteger
import java.nio.file.Path

fun main(args: Array<String>) {
    require(args.size <= 1) { "Можно передать только путь к папке с текстом, музыкой и изображениями" }
    demonstrateField()
    demonstrateRijndael()
    val input = if (args.isEmpty()) Path.of("demo-input") else Path.of(args[0])
    RijndaelDemo.run(input)
}

private fun demonstrateField() {
    println("1. Поле GF(2^8) и двоичные полиномы")
    val module = 0x1B.toByte()
    println("Модуль: 0x11B, в одном байте храним 0x1B")
    println("0x57 + 0x83 = 0x${byteHex(GaloisField.add(0x57, 0x83.toByte()))}")
    println("0x57 * 0x83 = 0x${byteHex(GaloisField.multiply(0x57, 0x83.toByte(), module))}")
    val inverse = GaloisField.inverse(0x53, module)
    println("Обратный к 0x53: 0x${byteHex(inverse)}")
    println("0x53 * обратный = 0x${byteHex(GaloisField.multiply(0x53, inverse, module))}")
    val moduli = GaloisField.irreducibleModuli()
    check(moduli.size == 30)
    print("Все 30 неприводимых модулей (полное значение): ")
    for (value in moduli) print("0x1${byteHex(value)} ")
    println()
    val polynomial = BigInteger("15", 16)
    val factors = GaloisField.factorize(polynomial)
    println("Разложение x^4 + x^2 + 1 (0x15):")
    for (factor in factors) {
        println("  полином 0x${factor.polynomial.toString(16)} в степени ${factor.exponent}")
    }
    try {
        GaloisField.multiply(1, 2, 0)
        error("Приводимый модуль не был отклонён")
    } catch (error: IllegalArgumentException) {
        println("Ожидаемая ошибка для приводимого модуля 0x100: ${error.message}")
    }
    try {
        GaloisField.inverse(1, 0)
        error("Приводимый модуль не был отклонён")
    } catch (error: IllegalArgumentException) {
        println("Ожидаемая ошибка при взятии обратного: ${error.message}")
    }
}

private fun demonstrateRijndael() {
    println("\n2. Контрольный пример Rijndael с блоком и ключом по 128 бит (AES-128)")
    val key = ByteArray(16)
    val block = ByteArray(16)
    val cipher = RijndaelCipher()
    cipher.setKey(key)
    val encrypted = cipher.encrypt(block)
    check(hexText(encrypted) == "66E94BD4EF8A2C3B884CFA59CA342B2E")
    check(cipher.decrypt(encrypted).contentEquals(block))
    println("Ключ: ${hexText(key)}")
    println("Блок: ${hexText(block)}")
    println("Шифротекст: ${hexText(encrypted)}")
    println("Контрольный ответ совпал, исходный блок восстановлен")
}

private fun byteHex(value: Byte): String = (value.toInt() and 255).toString(16).padStart(2, '0').uppercase()

private fun hexText(data: ByteArray): String {
    val result = StringBuilder()
    for (value in data) result.append(byteHex(value))
    return result.toString()
}
