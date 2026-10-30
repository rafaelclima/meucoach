package com.meucoach.core.domain.importer

import com.meucoach.core.model.ActivitySample
import com.meucoach.core.model.ActivitySource
import com.meucoach.core.model.ImportedActivity
import com.meucoach.core.model.Sport
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.time.Instant

class ActivityDeduplicatorTest {
    private val deduplicator = ActivityDeduplicator()
    private val start = Instant.parse("2026-03-01T10:00:00Z")

    @Test
    fun `prefers fit over tcx and gpx for the same activity`() {
        val fit = activity(ActivitySource.FIT, samples = 10)
        val tcx = activity(ActivitySource.TCX, samples = 20)
        val gpx = activity(ActivitySource.GPX, samples = 30)

        val result = deduplicator.deduplicate(listOf(gpx, tcx, fit))

        assertThat(result).containsExactly(fit)
    }

    @Test
    fun `keeps the richer sample set when the source is the same`() {
        val sparse = activity(ActivitySource.FIT, samples = 1)
        val rich = activity(ActivitySource.FIT, samples = 5)

        val result = deduplicator.deduplicate(listOf(sparse, rich))

        assertThat(result).containsExactly(rich)
    }

    @Test
    fun `merges near matches within the data model tolerance`() {
        val fit = activity(ActivitySource.FIT, samples = 10)
        val tcx = activity(
            ActivitySource.TCX,
            start = start.plusSeconds(1),
            durationSec = 1_802,
            distanceM = 5_040.0,
            samples = 5,
        )

        val result = deduplicator.deduplicate(listOf(tcx, fit))

        assertThat(result).containsExactly(fit)
    }

    @Test
    fun `keeps activities outside the tolerance`() {
        val fit = activity(ActivitySource.FIT, samples = 10)
        val tcx = activity(
            ActivitySource.TCX,
            start = start.plusSeconds(2),
            durationSec = 1_803,
            distanceM = 5_060.0,
            samples = 5,
        )

        assertThat(deduplicator.deduplicate(listOf(fit, tcx))).hasSize(2)
    }

    @Test
    fun `keeps activities with different start duration or distance`() {
        val first = activity(ActivitySource.FIT, start = start, samples = 2)
        val later = activity(ActivitySource.TCX, start = start.plusSeconds(60), samples = 2)
        val longer = activity(ActivitySource.GPX, durationSec = 1_803, samples = 2)
        val farther = activity(ActivitySource.GPX, distanceM = 5_200.0, samples = 2)

        val result = deduplicator.deduplicate(listOf(farther, longer, later, first))

        assertThat(result).hasSize(4)
        assertThat(result.first().startTime).isEqualTo(start)
        assertThat(result.last().startTime).isEqualTo(start.plusSeconds(60))
    }

    @Test
    fun `rounds distance to the nearest meter in the key`() {
        val a = activity(ActivitySource.TCX, distanceM = 5_000.2, samples = 1)
        val b = activity(ActivitySource.FIT, distanceM = 5_000.4, samples = 1)

        assertThat(deduplicator.keyOf(a)).isEqualTo(deduplicator.keyOf(b))
        assertThat(deduplicator.deduplicate(listOf(a, b))).containsExactly(b)
    }

    @Test
    fun `orders every supported source by priority`() {
        assertThat(
            deduplicator.deduplicate(
                listOf(
                    activity(ActivitySource.MANUAL, samples = 1),
                    activity(ActivitySource.HEALTH_CONNECT, samples = 1),
                ),
            ).single().source,
        ).isEqualTo(ActivitySource.HEALTH_CONNECT)

        assertThat(
            deduplicator.deduplicate(
                listOf(
                    activity(ActivitySource.HEALTH_CONNECT, samples = 1),
                    activity(ActivitySource.GPX, samples = 1),
                ),
            ).single().source,
        ).isEqualTo(ActivitySource.GPX)

        assertThat(
            deduplicator.deduplicate(
                listOf(
                    activity(ActivitySource.GPX, samples = 1),
                    activity(ActivitySource.TCX, samples = 1),
                ),
            ).single().source,
        ).isEqualTo(ActivitySource.TCX)

        assertThat(
            deduplicator.deduplicate(
                listOf(
                    activity(ActivitySource.TCX, samples = 1),
                    activity(ActivitySource.FIT, samples = 1),
                ),
            ).single().source,
        ).isEqualTo(ActivitySource.FIT)
    }

    @Test
    fun `prefers a richer source even with fewer samples`() {
        val fit = activity(ActivitySource.FIT, samples = 1)
        val tcx = activity(ActivitySource.TCX, samples = 100)

        assertThat(deduplicator.isPreferred(fit, tcx)).isTrue()
        assertThat(deduplicator.isPreferred(tcx, fit)).isFalse()
    }

    @Test
    fun `uses stored sample count when comparing the same source`() {
        val sparse = activity(ActivitySource.FIT, samples = 2)
        val rich = activity(ActivitySource.FIT, samples = 5)

        assertThat(deduplicator.isPreferred(rich, sparse)).isTrue()
        assertThat(deduplicator.isPreferred(sparse, rich)).isFalse()
        assertThat(deduplicator.isPreferred(sparse, sparse, currentSampleCount = 1)).isTrue()
    }

    @Test
    fun `uses parser version as the final tie breaker`() {
        val older = activity(ActivitySource.FIT, samples = 2, parserVersion = "1.0.0")
        val newer = activity(ActivitySource.FIT, samples = 2, parserVersion = "1.1.0")

        assertThat(deduplicator.isPreferred(newer, older)).isTrue()
        assertThat(deduplicator.isPreferred(older, newer)).isFalse()
    }

    @Test
    fun `matches activities only inside the tolerance`() {
        val first = activity(ActivitySource.FIT, samples = 1)
        val near = activity(ActivitySource.TCX, start = start.plusSeconds(1), samples = 1)
        val far = activity(ActivitySource.TCX, start = start.plusSeconds(2), samples = 1)

        assertThat(deduplicator.matches(first, near)).isTrue()
        assertThat(deduplicator.matches(first, far)).isFalse()
    }

    private fun activity(
        source: ActivitySource,
        start: Instant = this.start,
        durationSec: Int = 1_800,
        distanceM: Double = 5_000.0,
        samples: Int,
        parserVersion: String = "1.0.0",
    ): ImportedActivity = ImportedActivity(
        source = source,
        sport = Sport.RUNNING,
        startTime = start,
        durationSec = durationSec,
        distanceM = distanceM,
        samples = List(samples) { ActivitySample(offsetSec = it) },
        parserVersion = parserVersion,
    )
}
