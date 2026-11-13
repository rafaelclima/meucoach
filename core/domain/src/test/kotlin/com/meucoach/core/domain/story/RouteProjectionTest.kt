package com.meucoach.core.domain.story

import com.meucoach.core.model.ActivitySample
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Test

class RouteProjectionTest {
    @Test
    fun `returns null without two positioned samples`() {
        assertThat(routeData(emptyList())).isNull()
        assertThat(routeData(listOf(sample(0, -5.79, -35.21)))).isNull()
        assertThat(
            routeData(
                listOf(
                    sample(0, -5.79, -35.21),
                    sample(1, null, null),
                ),
            ),
        ).isNull()
    }

    @Test
    fun `projects points inside the unit square`() {
        val route = routeData(
            listOf(
                sample(0, -5.790, -35.210),
                sample(10, -5.789, -35.209),
                sample(20, -5.788, -35.208),
            ),
        )!!

        assertThat(route.points).hasSize(3)
        route.points.forEach { point ->
            assertThat(point.x).isBetween(0f, 1f)
            assertThat(point.y).isBetween(0f, 1f)
        }
    }

    @Test
    fun `keeps north pointing up and preserves the wider span`() {
        val route = routeData(
            listOf(
                sample(0, 0.0, 0.0),
                sample(10, 0.001, 0.002),
            ),
        )!!

        // Two degrees of longitude span are wider than one of latitude.
        assertThat(route.points.map { it.x }).containsExactly(0f, 1f)
        assertThat(route.points.first().y).isGreaterThan(route.points.last().y)
        assertThat(route.points.last().y).isEqualTo(0.25f)
    }

    @Test
    fun `downsamples long routes but keeps the last point`() {
        val samples = List(1_000) { index ->
            sample(index, -5.79 + index * 0.00001, -35.21 + index * 0.00001)
        }

        val route = routeData(samples, maxPoints = 50)!!

        assertThat(route.points.size).isLessThanOrEqualTo(51)
        assertThat(route.end).isEqualTo(route.points.last())
    }

    @Test
    fun `requires a valid point limit`() {
        assertThatThrownBy {
            routeData(
                listOf(sample(0, 0.0, 0.0), sample(1, 0.1, 0.1)),
                maxPoints = 1,
            )
        }.isInstanceOf(IllegalArgumentException::class.java)
    }

    private fun sample(offset: Int, latitude: Double?, longitude: Double?) = ActivitySample(
        offsetSec = offset,
        latitude = latitude,
        longitude = longitude,
    )
}
