package com.meucoach.app.ui.coach

/**
 * Presentation-only splitter that turns validated AI copy into chat bubbles.
 *
 * It never changes the text: whitespace is normalized, sentences are packed up
 * to [MAX_CHARS], long sentences break at word boundaries, and the closing
 * question gets its own bubble. A word longer than the limit is kept whole so
 * the copy never loses meaning.
 */
internal object CoachBubbles {
    const val MAX_CHARS = 160

    private val whitespace = Regex("\\s+")
    private val endings = charArrayOf('.', '!', '?', '…')

    fun split(text: String, limit: Int = MAX_CHARS): List<String> {
        require(limit > 0) { "Bubble limit must be positive" }
        val normalized = text.replace(whitespace, " ").trim()
        if (normalized.isEmpty()) return emptyList()

        val sentences = sentences(normalized)
        val bubbles = mutableListOf<String>()
        val pending = StringBuilder()

        fun flush() {
            if (pending.isNotEmpty()) {
                bubbles += pending.toString()
                pending.clear()
            }
        }

        sentences.forEachIndexed { index, sentence ->
            val closingQuestion = index == sentences.lastIndex && sentence.endsWith("?")
            when {
                sentence.length > limit -> {
                    flush()
                    bubbles += splitLong(sentence, limit)
                }

                closingQuestion -> {
                    flush()
                    bubbles += sentence
                }

                pending.isEmpty() -> pending.append(sentence)

                pending.length + 1 + sentence.length <= limit ->
                    pending.append(' ').append(sentence)

                else -> {
                    flush()
                    pending.append(sentence)
                }
            }
        }
        flush()
        return bubbles
    }

    private fun sentences(text: String): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        text.forEachIndexed { index, char ->
            val isBoundary = char in endings && (index == text.lastIndex || text[index + 1] == ' ')
            if (isBoundary) {
                val sentence = text.substring(start, index + 1).trim()
                if (sentence.isNotEmpty()) result += sentence
                start = index + 1
            }
        }
        val tail = text.substring(start).trim()
        if (tail.isNotEmpty()) result += tail
        return result
    }

    private fun splitLong(sentence: String, limit: Int): List<String> {
        val chunks = mutableListOf<String>()
        val current = StringBuilder()
        sentence.split(' ').forEach { word ->
            when {
                current.isEmpty() -> current.append(word)

                current.length + 1 + word.length <= limit -> current.append(' ').append(word)

                else -> {
                    chunks += current.toString()
                    current.clear()
                    current.append(word)
                }
            }
        }
        if (current.isNotEmpty()) chunks += current.toString()
        return chunks
    }
}
