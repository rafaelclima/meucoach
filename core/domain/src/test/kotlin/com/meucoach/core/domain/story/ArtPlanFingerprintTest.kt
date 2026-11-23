package com.meucoach.core.domain.story

import com.meucoach.core.model.Pace
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test
import java.time.LocalDate

class ArtPlanFingerprintTest {
    @Test
    fun `fingerprint is a lowercase SHA-256 hex string`() {
        val fingerprint = ArtPlanFingerprints.of(plan())

        assertThat(fingerprint.value).matches("[0-9a-f]{64}")
    }

    @Test
    fun `the same plan always produces the same fingerprint`() {
        val first = ArtPlanFingerprints.of(plan())
        val second = ArtPlanFingerprints.of(plan())

        assertThat(first).isEqualTo(second)
    }

    @Test
    fun `date distance duration pace and route each change the fingerprint`() {
        val base = plan()
        val baseFingerprint = ArtPlanFingerprints.of(base)

        val variants = listOf(
            plan(date = LocalDate.of(2026, 10, 25)),
            plan(distanceKm = 5.01),
            plan(durationTotalSec = 1801),
            plan(pace = Pace.fromSecondsPerKm(361)),
            plan(points = listOf(RoutePoint(0f, 0f), RoutePoint(0.5f, 0.6f), RoutePoint(1f, 1f))),
        )

        for (variant in variants) {
            assertThat(ArtPlanFingerprints.of(variant)).isNotEqualTo(baseFingerprint)
        }
    }

    @Test
    fun `a missing date or pace is distinct from a present value`() {
        val withValues = plan()
        val withoutDate = plan(date = null)
        val withoutPace = plan(pace = null)

        assertThat(ArtPlanFingerprints.of(withoutDate))
            .isNotEqualTo(ArtPlanFingerprints.of(withValues))
        assertThat(ArtPlanFingerprints.of(withoutPace))
            .isNotEqualTo(ArtPlanFingerprints.of(withValues))
    }

    @Test
    fun `route points are hashed with five decimal places`() {
        val first = plan(
            points = listOf(RoutePoint(0.123454f, 0.987654f), RoutePoint(1f, 1f)),
        )
        val second = plan(
            points = listOf(RoutePoint(0.1234541f, 0.9876541f), RoutePoint(1f, 1f)),
        )

        assertThat(ArtPlanFingerprints.of(first)).isEqualTo(ArtPlanFingerprints.of(second))
    }

    @Test
    fun `route order and length change the fingerprint`() {
        val forward = plan(points = listOf(RoutePoint(0f, 0f), RoutePoint(1f, 1f)))
        val backward = plan(points = listOf(RoutePoint(1f, 1f), RoutePoint(0f, 0f)))
        val longer = plan(
            points = listOf(RoutePoint(0f, 0f), RoutePoint(0.5f, 0.5f), RoutePoint(1f, 1f)),
        )

        assertThat(ArtPlanFingerprints.of(forward)).isNotEqualTo(ArtPlanFingerprints.of(backward))
        assertThat(ArtPlanFingerprints.of(forward)).isNotEqualTo(ArtPlanFingerprints.of(longer))
    }

    @Test
    fun `an empty route still produces a deterministic fingerprint`() {
        val empty = plan(points = emptyList())

        assertThat(ArtPlanFingerprints.of(empty).value).matches("[0-9a-f]{64}")
        assertThat(ArtPlanFingerprints.of(empty)).isEqualTo(ArtPlanFingerprints.of(empty))
    }

    @Test
    fun `fingerprint values must not be blank`() {
        assertThatThrownBy { ArtPlanFingerprint("") }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { ArtPlanFingerprint("   ") }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun plan(
        date: LocalDate? = LocalDate.of(2026, 10, 24),
        distanceKm: Double = 5.0,
        durationTotalSec: Int = 1800,
        pace: Pace? = Pace.fromSecondsPerKm(360),
        points: List<RoutePoint> = listOf(
            RoutePoint(0f, 0f),
            RoutePoint(0.5f, 0.5f),
            RoutePoint(1f, 1f),
        ),
    ): StoryPlan = StoryPlan(
        route = RouteData(points),
        splits = emptyList(),
        allSplits = emptyList(),
        splitBar = StorySplitBar.Empty,
        segments = emptyList(),
        durationSec = 30f,
        distanceKm = distanceKm,
        durationTotalSec = durationTotalSec,
        calories = null,
        averagePace = pace,
        averageHeartRate = null,
        boundingBox = null,
        localDate = date,
    )
}
