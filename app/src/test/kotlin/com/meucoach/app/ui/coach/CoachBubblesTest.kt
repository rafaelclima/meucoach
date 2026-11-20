package com.meucoach.app.ui.coach

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class CoachBubblesTest {
    @Test
    fun `junta frases curtas e deixa a pergunta final sozinha`() {
        val bubbles = CoachBubbles.split("Bom dia, Rafael. Hoje é fácil. Bora?")

        assertThat(bubbles).containsExactly("Bom dia, Rafael. Hoje é fácil.", "Bora?")
    }

    @Test
    fun `frase longa quebra em palavras e respeita o teto`() {
        val text = ("manter o volume da semana sem inventar carga ".repeat(6)).trim() + "."

        val bubbles = CoachBubbles.split(text)

        assertThat(bubbles).allMatch { it.length <= CoachBubbles.MAX_CHARS }
        assertThat(bubbles.joinToString(" ")).isEqualTo(text)
        assertThat(bubbles).allMatch { it == it.trim() }
    }

    @Test
    fun `nunca corta uma palavra no meio`() {
        val words = ("palavra ".repeat(30)).trim()
        val bubbles = CoachBubbles.split(words)

        val rebuilt = bubbles.flatMap { it.split(" ") }
        assertThat(rebuilt).allMatch { it == "palavra" }
    }

    @Test
    fun `quebra de linha e espacos repetidos viram um espaco`() {
        val bubbles = CoachBubbles.split("  Primeira linha.\n\nSegunda   linha.  ")

        assertThat(bubbles).containsExactly("Primeira linha. Segunda linha.")
    }

    @Test
    fun `texto vazio nao gera bolha`() {
        assertThat(CoachBubbles.split("   \n  ")).isEmpty()
    }

    @Test
    fun `pergunta no meio permanece junto da frase quando cabe`() {
        val bubbles = CoachBubbles.split("Confere aqui? Depois seguimos.")

        assertThat(bubbles).containsExactly("Confere aqui? Depois seguimos.")
    }

    @Test
    fun `palavra maior que o teto fica inteira`() {
        val giant = "x".repeat(CoachBubbles.MAX_CHARS + 20)

        val bubbles = CoachBubbles.split("Antes. $giant. Depois.")

        assertThat(bubbles).contains("$giant.")
    }
}
