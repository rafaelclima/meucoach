package com.meucoach.core.domain.parser.fit

import org.junit.Test
import kotlin.random.Random

/**
 * Fuzz leve do parser FIT (T-05), sem dependência externa.
 *
 * Contrato sob ataque: para QUALQUER entrada de bytes, o parser retorna um
 * [FitParseResult] ou lança [FitParseException] com código controlado
 * ([FitErrorCode]). Qualquer outra exceção (IndexOutOfBounds, NPE,
 * NegativeArraySize, ClassCast, Arithmetic…) é um bug do parser e não pode
 * alcançar o pipeline de import.
 *
 * Determinístico por construção: seeds fixas de PRNG e operadores de mutação
 * fixos, para que toda falha reproduza igual localmente e no CI. Os corpus são
 * os fixtures reais sanitizados — mutações sobre arquivos bem formados exploram
 * caminhos que lixo puro não alcança.
 */
class FitParserFuzzTest {
    private val parser = FitParser()

    private val fixtures = listOf(
        "Zepp20261022195619.fit",
        "Zepp20261024051710.fit",
        "Zepp20261026202359.fit",
        "Zepp20261027052000.fit",
    )

    @Test
    fun `mutations of real fixtures never escape the controlled error surface`() {
        for (seed in 1..150) {
            for (name in fixtures) {
                val base = fixture(name)
                val mutation = mutate(base, seed)
                parseUnderContract(mutation, "fixture=$name seed=$seed")
            }
        }
    }

    @Test
    fun `random garbage never crashes the parser`() {
        val random = Random(GARBAGE_SEED)
        for (round in 0 until 200) {
            val garbage = ByteArray(random.nextInt(1, 4_096)).also(random::nextBytes)
            parseUnderContract(garbage, "garbage round=$round")
        }
    }

    @Test
    fun `empty and minimal inputs are rejected as controlled errors`() {
        parseUnderContract(ByteArray(0), "empty")
        for (size in 1 until HEADER_THRESHOLD) {
            parseUnderContract(ByteArray(size), "header prefix size=$size")
        }
    }

    @Test
    fun `parsing the same mutated input twice is deterministic`() {
        for (seed in 1..40) {
            val base = fixture(fixtures[seed % fixtures.size])
            val mutation = mutate(base, seed)
            val first = runCatching { parser.parse(mutation) }
            val second = runCatching { parser.parse(mutation) }
            check(first.exceptionOrNull()?.javaClass == second.exceptionOrNull()?.javaClass) {
                "Parser divergiu entre duas execuções idênticas (seed=$seed)"
            }
        }
    }

    /** Runs one parse and fails with repro details if the error surface is escaped. */
    private fun parseUnderContract(bytes: ByteArray, repro: String) {
        try {
            parser.parse(bytes)
        } catch (expected: FitParseException) {
            // Rejeição controlada é um resultado válido: mensagem sempre com código.
            check(expected.message?.startsWith(expected.code.name) == true) {
                "FitParseException sem código na mensagem ($repro): ${expected.message}"
            }
        } catch (other: Throwable) {
            throw AssertionError(
                "Parser escapou da superfície de erro controlada ($repro, bytes=${bytes.size}): " +
                    "${other.javaClass.simpleName}: ${other.message}",
                other,
            )
        }
    }

    /** Picks one deterministic operator per seed; truncation may combine with bit flips. */
    private fun mutate(base: ByteArray, seed: Int): ByteArray {
        val random = Random(seed)
        var bytes = base.copyOf()
        if (random.nextBoolean()) {
            // Truncamento em qualquer ponto, inclusive no meio do header ou de uma message.
            bytes = bytes.copyOf(random.nextInt(bytes.size + 1))
        }
        when (random.nextInt(5)) {
            0 -> repeat(random.nextInt(1, 33)) { index ->
                val offset = random.nextInt(bytes.size)
                bytes[offset] = (bytes[offset].toInt() xor (1 shl random.nextInt(8))).toByte()
            }

            1 -> repeat(random.nextInt(1, 17)) {
                bytes[random.nextInt(bytes.size)] = random.nextInt(256).toByte()
            }

            2 -> bytes += ByteArray(random.nextInt(1, 65)).also(random::nextBytes)

            3 -> {
                val from = random.nextInt(bytes.size)
                val length = random.nextInt(1, 65).coerceAtMost(bytes.size - from)
                bytes += bytes.copyOfRange(from, from + length)
            }

            4 -> if (bytes.isNotEmpty()) {
                // Zera um trecho: testa o parser contra campos nulos onde se esperava dado.
                val from = random.nextInt(bytes.size)
                val length = random.nextInt(1, 65).coerceAtMost(bytes.size - from)
                repeat(length) { bytes[from + it] = 0 }
            }
        }
        return bytes
    }

    private fun fixture(name: String): ByteArray =
        checkNotNull(javaClass.getResourceAsStream("/fit/$name")) { "Missing test fixture $name" }
            .use { it.readBytes() }

    private companion object {
        const val GARBAGE_SEED = 42
        const val HEADER_THRESHOLD = 16
    }
}
