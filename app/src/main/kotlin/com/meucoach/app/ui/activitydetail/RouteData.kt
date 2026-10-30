package com.meucoach.app.ui.activitydetail

import com.meucoach.core.model.ActivitySample
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max

data class RoutePoint(val x: Float, val y: Float)

data class RouteData(val points: List<RoutePoint>) {
    val start: RoutePoint get() = points.first()
    val end: RoutePoint get() = points.last()
}

/**
 * Projects GPS samples into normalized [0,1] coordinates for an offline drawing.
 *
 * Uses an equirectangular approximation, which is accurate enough for a single
 * run and avoids map tiles, network access or a new map dependency.
 */
fun routeData(samples: List<ActivitySample>, maxPoints: Int = 400): RouteData? {
    require(maxPoints > 1) { "maxPoints must be greater than one" }
    val geo = samples.mapNotNull { sample ->
        val latitude = sample.latitude ?: return@mapNotNull null
        val longitude = sample.longitude ?: return@mapNotNull null
        latitude to longitude
    }
    if (geo.size < 2) return null

    val step = ceil(geo.size.toDouble() / maxPoints).toInt().coerceAtLeast(1)
    val sampled = buildList {
        geo.forEachIndexed { index, point ->
            if (index % step == 0) add(point)
        }
        if (lastOrNull() != geo.last()) add(geo.last())
    }

    val minLat = sampled.minOf { it.first }
    val maxLat = sampled.maxOf { it.first }
    val minLon = sampled.minOf { it.second }
    val maxLon = sampled.maxOf { it.second }
    if (abs(maxLat - minLat) < EPSILON && abs(maxLon - minLon) < EPSILON) return null

    val meanLatRadians = Math.toRadians((minLat + maxLat) / 2.0)
    val lonScale = cos(meanLatRadians).coerceAtLeast(MIN_LON_SCALE)
    val xs = sampled.map { (it.second - minLon) * lonScale }
    val ys = sampled.map { it.first - minLat }
    val spanX = xs.max()
    val spanY = ys.max()
    val scale = max(spanX, spanY).coerceAtLeast(EPSILON)
    val offsetX = (1.0 - spanX / scale) / 2.0
    val offsetY = (1.0 - spanY / scale) / 2.0

    return RouteData(
        points = sampled.mapIndexed { index, _ ->
            // Canvas y grows down; invert so north points up.
            RoutePoint(
                x = (offsetX + xs[index] / scale).toFloat(),
                y = (1.0 - (offsetY + ys[index] / scale)).toFloat(),
            )
        },
    )
}

private const val EPSILON = 1e-9
private const val MIN_LON_SCALE = 0.01
