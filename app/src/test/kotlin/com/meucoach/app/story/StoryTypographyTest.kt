package com.meucoach.app.story

import android.graphics.Paint
import android.graphics.Typeface
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class StoryTypographyTest {
    @Test
    fun `story text uses tabular figures so numbers do not wobble`() {
        val paint = storyTextPaint(0xFFFFFFFF.toInt(), 30f, Typeface.DEFAULT)

        assertThat(paint.fontFeatureSettings).isEqualTo("tnum")
        assertThat(paint.textSize).isEqualTo(30f)
        assertThat(paint.color).isEqualTo(0xFFFFFFFF.toInt())
    }

    @Test
    fun `bold story text keeps tabular figures`() {
        val paint = storyTextPaint(0xFFD8FF3E.toInt(), 46f, Typeface.DEFAULT, bold = true)

        assertThat(paint.fontFeatureSettings).isEqualTo("tnum")
        assertThat(paint.typeface).isEqualTo(Typeface.create(Typeface.DEFAULT, Typeface.BOLD))
    }

    @Test
    fun `paint factory returns an antialiased paint`() {
        val paint = storyTextPaint(0xFFFFFFFF.toInt(), 12f, Typeface.DEFAULT)

        assertThat(paint.isAntiAlias).isTrue()
        assertThat(paint.flags and Paint.ANTI_ALIAS_FLAG).isNotZero()
    }
}
