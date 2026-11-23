package com.meucoach.app.story

import com.meucoach.core.domain.story.ArtBackgroundMode
import com.meucoach.core.domain.story.ArtPhotoCrop
import com.meucoach.core.domain.story.ArtPhotoRef
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArtPhotoCodecTest {
    @Test
    fun `round-trips a photo background with focus and zoom`() {
        val record = ArtPhotoRecord(
            background = ArtBackgroundMode.PHOTO,
            photo = ArtPhotoRef(
                photoId = PHOTO_ID,
                activityId = ACTIVITY_ID,
                crop = ArtPhotoCrop(focusX = 0.25f, focusY = 0.75f, zoom = 2f),
            ),
        )

        val decoded = ArtPhotoCodec.decode(ArtPhotoCodec.encode(ACTIVITY_ID, record))

        assertThat(decoded).isEqualTo(ArtPhotoMetadata(ACTIVITY_ID, record))
    }

    @Test
    fun `round-trips transparent mode with a waiting photo`() {
        val record = ArtPhotoRecord(
            background = ArtBackgroundMode.TRANSPARENT,
            photo = ArtPhotoRef(photoId = PHOTO_ID, activityId = ACTIVITY_ID),
        )

        val decoded = ArtPhotoCodec.decode(ArtPhotoCodec.encode(ACTIVITY_ID, record))

        assertThat(decoded).isEqualTo(ArtPhotoMetadata(ACTIVITY_ID, record))
    }

    @Test
    fun `writes version 1 json with the documented keys`() {
        val record = ArtPhotoRecord(
            background = ArtBackgroundMode.PHOTO,
            photo = ArtPhotoRef(
                photoId = PHOTO_ID,
                activityId = ACTIVITY_ID,
                crop = ArtPhotoCrop(focusX = 0.25f, focusY = 0.75f, zoom = 2f),
            ),
        )

        val json = JSONObject(ArtPhotoCodec.encode(ACTIVITY_ID, record))

        assertThat(ArtPhotoCodec.VERSION).isEqualTo(1)
        assertThat(json.getInt("v")).isEqualTo(1)
        assertThat(json.getString("activityId")).isEqualTo(ACTIVITY_ID)
        assertThat(json.getString("background")).isEqualTo("PHOTO")
        assertThat(json.getString("photoId")).isEqualTo(PHOTO_ID)
        assertThat(json.getDouble("focusX")).isEqualTo(0.25, org.assertj.core.data.Offset.offset(0.000001))
        assertThat(json.getDouble("focusY")).isEqualTo(0.75, org.assertj.core.data.Offset.offset(0.000001))
        assertThat(json.getDouble("zoom")).isEqualTo(2.0, org.assertj.core.data.Offset.offset(0.000001))
    }

    @Test
    fun `transparent mode without photo omits the photo fields`() {
        val record = ArtPhotoRecord(background = ArtBackgroundMode.TRANSPARENT, photo = null)

        val json = JSONObject(ArtPhotoCodec.encode(ACTIVITY_ID, record))

        assertThat(json.getString("background")).isEqualTo("TRANSPARENT")
        assertThat(json.has("photoId")).isFalse()
        assertThat(json.has("focusX")).isFalse()
        assertThat(json.has("focusY")).isFalse()
        assertThat(json.has("zoom")).isFalse()
        assertThat(ArtPhotoCodec.decode(json.toString()))
            .isEqualTo(ArtPhotoMetadata(ACTIVITY_ID, record))
    }

    @Test
    fun `unknown background falls back to transparent and keeps the waiting photo`() {
        val json = JSONObject(ArtPhotoCodec.encode(ACTIVITY_ID, recordWithPhoto()))
            .put("background", "OPAQUE")
            .toString()

        val decoded = ArtPhotoCodec.decode(json)

        assertThat(decoded!!.record.background).isEqualTo(ArtBackgroundMode.TRANSPARENT)
        assertThat(decoded.record.photo).isNotNull
    }

    @Test
    fun `invalid focus and zoom are sanitized per field`() {
        val json = JSONObject(ArtPhotoCodec.encode(ACTIVITY_ID, recordWithPhoto()))
            .put("focusX", 1.5)
            .put("focusY", -0.2)
            .put("zoom", 9.0)
            .toString()

        val decoded = ArtPhotoCodec.decode(json)!!.record.photo!!.crop

        assertThat(decoded.focusX).isEqualTo(1f)
        assertThat(decoded.focusY).isEqualTo(0f)
        assertThat(decoded.zoom).isEqualTo(4f)
    }

    @Test
    fun `non finite focus and zoom fall back to the defaults`() {
        val json = JSONObject(ArtPhotoCodec.encode(ACTIVITY_ID, recordWithPhoto()))
            .put("focusX", "NaN")
            .put("focusY", "Infinity")
            .put("zoom", "NaN")
            .toString()

        val decoded = ArtPhotoCodec.decode(json)!!.record.photo!!.crop

        assertThat(decoded).isEqualTo(ArtPhotoCrop())
    }

    @Test
    fun `an invalid photo id drops the photo and falls back to transparent`() {
        val json = JSONObject(ArtPhotoCodec.encode(ACTIVITY_ID, recordWithPhoto()))
            .put("photoId", "photo-1")
            .toString()

        val decoded = ArtPhotoCodec.decode(json)!!

        assertThat(decoded.record.background).isEqualTo(ArtBackgroundMode.TRANSPARENT)
        assertThat(decoded.record.photo).isNull()
    }

    @Test
    fun `photo background without a photo falls back to transparent`() {
        val json = JSONObject(ArtPhotoCodec.encode(ACTIVITY_ID, recordWithPhoto()))
            .put("background", "PHOTO")
            .apply { remove("photoId") }
            .toString()

        val decoded = ArtPhotoCodec.decode(json)!!

        assertThat(decoded.record.background).isEqualTo(ArtBackgroundMode.TRANSPARENT)
        assertThat(decoded.record.photo).isNull()
    }

    @Test
    fun `corrupt unsupported or unowned payloads return null`() {
        assertThat(ArtPhotoCodec.decode(null)).isNull()
        assertThat(ArtPhotoCodec.decode("")).isNull()
        assertThat(ArtPhotoCodec.decode("não é json")).isNull()
        assertThat(ArtPhotoCodec.decode("[]")).isNull()

        val wrongVersion = JSONObject(ArtPhotoCodec.encode(ACTIVITY_ID, recordWithPhoto()))
            .put("v", 2)
            .toString()
        assertThat(ArtPhotoCodec.decode(wrongVersion)).isNull()

        val missingVersion = JSONObject(ArtPhotoCodec.encode(ACTIVITY_ID, recordWithPhoto()))
            .apply { remove("v") }
            .toString()
        assertThat(ArtPhotoCodec.decode(missingVersion)).isNull()

        val missingActivity = JSONObject(ArtPhotoCodec.encode(ACTIVITY_ID, recordWithPhoto()))
            .apply { remove("activityId") }
            .toString()
        assertThat(ArtPhotoCodec.decode(missingActivity)).isNull()

        val blankActivity = JSONObject(ArtPhotoCodec.encode(ACTIVITY_ID, recordWithPhoto()))
            .put("activityId", " ")
            .toString()
        assertThat(ArtPhotoCodec.decode(blankActivity)).isNull()
    }

    @Test
    fun `a photo background requires a photo reference`() {
        assertThatThrownBy {
            ArtPhotoRecord(background = ArtBackgroundMode.PHOTO, photo = null)
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `encode requires a non blank activity id`() {
        assertThatThrownBy {
            ArtPhotoCodec.encode(" ", ArtPhotoRecord(ArtBackgroundMode.TRANSPARENT, null))
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun recordWithPhoto(): ArtPhotoRecord = ArtPhotoRecord(
        background = ArtBackgroundMode.PHOTO,
        photo = ArtPhotoRef(photoId = PHOTO_ID, activityId = ACTIVITY_ID),
    )

    private companion object {
        const val ACTIVITY_ID = "activity-1"
        const val PHOTO_ID = "3f2504e0-4f89-41d3-9a0c-0305e82c3301"
    }
}
