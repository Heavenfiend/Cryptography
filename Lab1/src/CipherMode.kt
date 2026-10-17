enum class CipherMode {
    ECB, CBC, PCBC, CFB, OFB, CTR, RANDOM_DELTA
}

// P — исходный блок, C — зашифрованный, E — шифрование, XOR — побитовое исключающее ИЛИ.
//
// ECB:
// C1 = E(P1)
// C2 = E(P2)
//
// CBC:
// C1 = E(P1 XOR IV)
// C2 = E(P2 XOR C1)
//
// PCBC:
// C1 = E(P1 XOR IV)
// C2 = E(P2 XOR P1 XOR C1)
//
// CFB:
// Маска1 = E(IV)
// C1 = P1 XOR Маска1
// Маска2 = E(C1)
// C2 = P2 XOR Маска2
//
// OFB:
// Маска1 = E(IV)
// Маска2 = E(Маска1)
// C1 = P1 XOR Маска1
// C2 = P2 XOR Маска2
//
// CTR (IV = 10):
// Маска1 = E(10)
// Маска2 = E(11)
// C1 = P1 XOR Маска1
// C2 = P2 XOR Маска2
//
// Random Delta (IV = 10, delta = 3):
// C1 = E(P1 XOR 10)
// C2 = E(P2 XOR 13)
// C3 = E(P3 XOR 16)