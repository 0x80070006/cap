package org.capnav.app.navigation

import org.capnav.app.model.GeoPoint
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object Geo {
    private const val EARTH_RADIUS_M = 6_371_008.8

    fun distanceM(a: GeoPoint, b: GeoPoint): Double {
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).let { it * it } +
            cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(1 - h))
    }

    fun bearingDeg(a: GeoPoint, b: GeoPoint): Double {
        val la1 = Math.toRadians(a.lat)
        val la2 = Math.toRadians(b.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val y = sin(dLon) * cos(la2)
        val x = cos(la1) * sin(la2) - sin(la1) * cos(la2) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    data class Projection(
        /** Index of the segment start in the polyline. */
        val segmentIndex: Int,
        /** Fraction along that segment, 0..1. */
        val t: Double,
        val point: GeoPoint,
        val distanceM: Double,
    )

    /**
     * Projects [p] onto the polyline using a local equirectangular approximation, which is
     * accurate to well under a metre at the segment lengths routing engines produce.
     * The search can be restricted to [fromIndex, toIndex) to keep the match monotonic.
     */
    fun project(p: GeoPoint, line: List<GeoPoint>, fromIndex: Int = 0, toIndex: Int = line.size - 1): Projection? {
        if (line.size < 2) return null
        val start = fromIndex.coerceIn(0, line.size - 2)
        val end = toIndex.coerceIn(start + 1, line.size - 1)
        val kx = cos(Math.toRadians(p.lat)) * 111_320.0
        val ky = 110_574.0
        var best: Projection? = null
        for (i in start until end) {
            val a = line[i]
            val b = line[i + 1]
            val ax = (a.lon - p.lon) * kx
            val ay = (a.lat - p.lat) * ky
            val bx = (b.lon - p.lon) * kx
            val by = (b.lat - p.lat) * ky
            val dx = bx - ax
            val dy = by - ay
            val len2 = dx * dx + dy * dy
            val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
            val px = ax + t * dx
            val py = ay + t * dy
            val d = sqrt(px * px + py * py)
            if (best == null || d < best.distanceM) {
                best = Projection(i, t, GeoPoint(a.lat + t * (b.lat - a.lat), a.lon + t * (b.lon - a.lon)), d)
            }
        }
        return best
    }

    /** Cumulative distance in metres at each vertex of [line]. */
    fun cumulative(line: List<GeoPoint>): DoubleArray {
        val out = DoubleArray(line.size)
        for (i in 1 until line.size) out[i] = out[i - 1] + distanceM(line[i - 1], line[i])
        return out
    }

    /** Point located [distanceM] metres along [line]. */
    fun along(line: List<GeoPoint>, cumulative: DoubleArray, distanceM: Double): GeoPoint {
        if (line.isEmpty()) error("empty line")
        if (distanceM <= 0) return line.first()
        for (i in 1 until line.size) {
            if (cumulative[i] >= distanceM) {
                val seg = cumulative[i] - cumulative[i - 1]
                val t = if (seg == 0.0) 0.0 else (distanceM - cumulative[i - 1]) / seg
                val a = line[i - 1]
                val b = line[i]
                return GeoPoint(a.lat + t * (b.lat - a.lat), a.lon + t * (b.lon - a.lon))
            }
        }
        return line.last()
    }

    /** Decodes a Valhalla polyline (precision 6). */
    fun decodePolyline6(encoded: String): List<GeoPoint> {
        val out = ArrayList<GeoPoint>(encoded.length / 4)
        var index = 0
        var lat = 0L
        var lon = 0L
        while (index < encoded.length) {
            var result = 0L
            var shift = 0
            var b: Int
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f).toLong() shl shift)
                shift += 5
            } while (b >= 0x20 && index < encoded.length)
            lat += if (result and 1L != 0L) (result shr 1).inv() else result shr 1
            result = 0
            shift = 0
            do {
                b = encoded[index++].code - 63
                result = result or ((b and 0x1f).toLong() shl shift)
                shift += 5
            } while (b >= 0x20 && index < encoded.length)
            lon += if (result and 1L != 0L) (result shr 1).inv() else result shr 1
            out += GeoPoint(lat / 1e6, lon / 1e6)
        }
        return out
    }
}
