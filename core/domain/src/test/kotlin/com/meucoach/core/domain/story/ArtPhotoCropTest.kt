package com.meucoach.core.domain.story

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.within
import org.junit.Test

class ArtPhotoCropTest {
    @Test
    fun `invalid source returns null instead of dividing by zero`() {
        val crop = ArtPhotoCrop()

        assertThat(crop.resolve(ArtFormat.PORTRAIT_4_5, sourceWidth = 0, sourceHeight = 1000)).isNull()
        assertThat(crop.resolve(ArtFormat.PORTRAIT_4_5, sourceWidth = 1000, sourceHeight = 0)).isNull()
        assertThat(crop.resolve(ArtFormat.PORTRAIT_4_5, sourceWidth = -1, sourceHeight = 1000)).isNull()
        assertThat(crop.resolve(ArtFormat.PORTRAIT_4_5, sourceWidth = 1000, sourceHeight = -1)).isNull()
    }

    @Test
    fun `4x5 crop from landscape source uses the full height and centers the width`() {
        val crop = ArtPhotoCrop().resolve(ArtFormat.PORTRAIT_4_5, sourceWidth = 4000, sourceHeight = 3000)!!

        assertThat(crop.left).isCloseTo(0.2f, within(0.0001f))
        assertThat(crop.top).isEqualTo(0f)
        assertThat(crop.right).isCloseTo(0.8f, within(0.0001f))
        assertThat(crop.bottom).isEqualTo(1f)
        assertAspect(crop, ArtFormat.PORTRAIT_4_5, sourceWidth = 4000, sourceHeight = 3000)
    }

    @Test
    fun `4x5 crop from portrait source uses the full width`() {
        val crop = ArtPhotoCrop().resolve(ArtFormat.PORTRAIT_4_5, sourceWidth = 3000, sourceHeight = 4000)!!

        assertThat(crop.left).isEqualTo(0f)
        assertThat(crop.top).isCloseTo(0.03125f, within(0.0001f))
        assertThat(crop.right).isEqualTo(1f)
        assertThat(crop.bottom).isCloseTo(0.96875f, within(0.0001f))
        assertAspect(crop, ArtFormat.PORTRAIT_4_5, sourceWidth = 3000, sourceHeight = 4000)
    }

    @Test
    fun `square crop is square for landscape and portrait sources`() {
        val landscape = ArtPhotoCrop().resolve(ArtFormat.SQUARE, sourceWidth = 4000, sourceHeight = 3000)!!
        val portrait = ArtPhotoCrop().resolve(ArtFormat.SQUARE, sourceWidth = 3000, sourceHeight = 4000)!!

        assertThat(landscape.left).isCloseTo(0.125f, within(0.0001f))
        assertThat(landscape.top).isEqualTo(0f)
        assertThat(landscape.right).isCloseTo(0.875f, within(0.0001f))
        assertThat(landscape.bottom).isEqualTo(1f)

        assertThat(portrait.left).isEqualTo(0f)
        assertThat(portrait.top).isCloseTo(0.125f, within(0.0001f))
        assertThat(portrait.right).isEqualTo(1f)
        assertThat(portrait.bottom).isCloseTo(0.875f, within(0.0001f))

        assertAspect(landscape, ArtFormat.SQUARE, sourceWidth = 4000, sourceHeight = 3000)
        assertAspect(portrait, ArtFormat.SQUARE, sourceWidth = 3000, sourceHeight = 4000)
    }

    @Test
    fun `9x16 crop is narrow in landscape and full height in a tall source`() {
        val landscape = ArtPhotoCrop().resolve(
            ArtFormat.PORTRAIT_9_16,
            sourceWidth = 4000,
            sourceHeight = 3000,
        )!!
        val tall = ArtPhotoCrop().resolve(ArtFormat.PORTRAIT_9_16, sourceWidth = 3000, sourceHeight = 4000)!!

        assertThat(landscape.left).isCloseTo(0.2890625f, within(0.0001f))
        assertThat(landscape.top).isEqualTo(0f)
        assertThat(landscape.right).isCloseTo(0.7109375f, within(0.0001f))
        assertThat(landscape.bottom).isEqualTo(1f)

        assertThat(tall.left).isCloseTo(0.125f, within(0.0001f))
        assertThat(tall.top).isEqualTo(0f)
        assertThat(tall.right).isCloseTo(0.875f, within(0.0001f))
        assertThat(tall.bottom).isEqualTo(1f)

        assertAspect(landscape, ArtFormat.PORTRAIT_9_16, sourceWidth = 4000, sourceHeight = 3000)
        assertAspect(tall, ArtFormat.PORTRAIT_9_16, sourceWidth = 3000, sourceHeight = 4000)
    }

    @Test
    fun `focus at the edges clamps the crop inside the source`() {
        val topLeft = ArtPhotoCrop(focusX = 0f, focusY = 0f, zoom = 4f)
            .resolve(ArtFormat.PORTRAIT_4_5, sourceWidth = 4000, sourceHeight = 3000)!!
        val bottomRight = ArtPhotoCrop(focusX = 1f, focusY = 1f, zoom = 4f)
            .resolve(ArtFormat.PORTRAIT_4_5, sourceWidth = 4000, sourceHeight = 3000)!!

        assertThat(topLeft.left).isEqualTo(0f)
        assertThat(topLeft.top).isEqualTo(0f)
        assertThat(topLeft.right).isCloseTo(0.15f, within(0.0001f))
        assertThat(topLeft.bottom).isCloseTo(0.25f, within(0.0001f))

        assertThat(bottomRight.left).isCloseTo(0.85f, within(0.0001f))
        assertThat(bottomRight.top).isCloseTo(0.75f, within(0.0001f))
        assertThat(bottomRight.right).isEqualTo(1f)
        assertThat(bottomRight.bottom).isEqualTo(1f)
    }

    @Test
    fun `zoom 2 and zoom 4 halve and quarter the centered crop`() {
        val base = ArtPhotoCrop().resolve(ArtFormat.PORTRAIT_4_5, sourceWidth = 4000, sourceHeight = 3000)!!
        val zoom2 = ArtPhotoCrop(zoom = 2f)
            .resolve(ArtFormat.PORTRAIT_4_5, sourceWidth = 4000, sourceHeight = 3000)!!
        val zoom4 = ArtPhotoCrop(zoom = 4f)
            .resolve(ArtFormat.PORTRAIT_4_5, sourceWidth = 4000, sourceHeight = 3000)!!

        assertThat(zoom2.width()).isCloseTo(base.width() / 2f, within(0.0001f))
        assertThat(zoom2.height()).isCloseTo(base.height() / 2f, within(0.0001f))
        assertThat(zoom4.width()).isCloseTo(base.width() / 4f, within(0.0001f))
        assertThat(zoom4.height()).isCloseTo(base.height() / 4f, within(0.0001f))

        assertThat(zoom2.left).isCloseTo(0.35f, within(0.0001f))
        assertThat(zoom2.top).isCloseTo(0.25f, within(0.0001f))
        assertThat(zoom2.right).isCloseTo(0.65f, within(0.0001f))
        assertThat(zoom2.bottom).isCloseTo(0.75f, within(0.0001f))

        assertThat(zoom4.left).isCloseTo(0.425f, within(0.0001f))
        assertThat(zoom4.top).isCloseTo(0.375f, within(0.0001f))
        assertThat(zoom4.right).isCloseTo(0.575f, within(0.0001f))
        assertThat(zoom4.bottom).isCloseTo(0.625f, within(0.0001f))
    }

    @Test
    fun `resolved crop always stays inside the source and preserves the format aspect`() {
        val sources = listOf(4000 to 3000, 3000 to 4000, 1080 to 1920)

        for (format in ArtFormat.entries) {
            for ((sourceWidth, sourceHeight) in sources) {
                for (focus in listOf(0f, 0.5f, 1f)) {
                    for (zoom in listOf(1f, 2f, 4f)) {
                        val crop = ArtPhotoCrop(focusX = focus, focusY = focus, zoom = zoom)
                            .resolve(format, sourceWidth, sourceHeight)!!

                        val description = "$format ${sourceWidth}x$sourceHeight focus=$focus zoom=$zoom"
                        assertThat(crop.left).describedAs(description).isBetween(0f, 1f)
                        assertThat(crop.top).describedAs(description).isBetween(0f, 1f)
                        assertThat(crop.right).describedAs(description).isBetween(0f, 1f)
                        assertThat(crop.bottom).describedAs(description).isBetween(0f, 1f)
                        assertThat(crop.left).describedAs(description).isLessThan(crop.right)
                        assertThat(crop.top).describedAs(description).isLessThan(crop.bottom)
                        assertAspect(crop, format, sourceWidth, sourceHeight, description)
                    }
                }
            }
        }
    }

    @Test
    fun `crop rejects invalid focus and zoom`() {
        assertThatThrownBy { ArtPhotoCrop(focusX = -0.01f) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { ArtPhotoCrop(focusY = 1.01f) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { ArtPhotoCrop(focusX = Float.NaN) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { ArtPhotoCrop(focusY = Float.NEGATIVE_INFINITY) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { ArtPhotoCrop(zoom = 0.99f) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { ArtPhotoCrop(zoom = 4.01f) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { ArtPhotoCrop(zoom = Float.POSITIVE_INFINITY) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { ArtPhotoCrop(zoom = Float.NaN) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `crop rect rejects non finite out of range and empty rectangles`() {
        assertThatThrownBy { ArtCropRect(left = Float.NaN, top = 0f, right = 1f, bottom = 1f) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { ArtCropRect(left = -0.1f, top = 0f, right = 1f, bottom = 1f) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { ArtCropRect(left = 0f, top = 0f, right = 1.1f, bottom = 1f) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { ArtCropRect(left = 0.5f, top = 0f, right = 0.5f, bottom = 1f) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { ArtCropRect(left = 0f, top = 0.5f, right = 1f, bottom = 0.5f) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `photo ref requires a UUID and a non blank activity`() {
        val ref = ArtPhotoRef(
            photoId = "3f2504e0-4f89-41d3-9a0c-0305e82c3301",
            activityId = "activity-1",
        )

        assertThat(ref.crop).isEqualTo(ArtPhotoCrop())
        assertThat(ref.photoId).isEqualTo("3f2504e0-4f89-41d3-9a0c-0305e82c3301")

        assertThatThrownBy { ArtPhotoRef(photoId = "photo-1", activityId = "activity-1") }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy {
            ArtPhotoRef(photoId = "3f2504e0-4f89-41d3-9a0c-0305e82c330", activityId = "activity-1")
        }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy {
            ArtPhotoRef(photoId = "3f2504e0-4f89-41d3-9a0c-0305e82c3301", activityId = " ")
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun assertAspect(
        crop: ArtCropRect,
        format: ArtFormat,
        sourceWidth: Int,
        sourceHeight: Int,
        description: String = "$format ${sourceWidth}x$sourceHeight",
    ) {
        val expected = format.width.toFloat() / format.height.toFloat()
        assertThat(crop.aspect(sourceWidth, sourceHeight))
            .describedAs(description)
            .isCloseTo(expected, within(0.0001f))
    }

    private fun ArtCropRect.aspect(sourceWidth: Int, sourceHeight: Int): Float =
        width() * sourceWidth / (height() * sourceHeight)

    private fun ArtCropRect.width(): Float = right - left

    private fun ArtCropRect.height(): Float = bottom - top
}
