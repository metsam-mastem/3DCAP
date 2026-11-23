package org.three_dcap.capture

import kotlin.math.PI
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class CaptureDirection(val yawDegrees: Double, val pitchDegrees: Double)
data class CoverageSnapshot(
    val selectedDirection: CaptureDirection?,
    val coveredCandidateCount: Int,
    val totalCandidateCount: Int,
    val estimatedCoveragePercent: Double
)

/**
 * Deterministic maximin shot planner.
 * The coverage percentage is a sampling estimate, not a reconstruction guarantee.
 */
class SphericalCoveragePlanner(candidateCount: Int = 192) {
    private val candidates: List<Vector>

    init {
        require(candidateCount in 8..4096) { "candidateCount must be between 8 and 4096" }
        candidates = fibonacciSphere(candidateCount)
    }

    fun nextShot(existingShots: List<CaptureDirection>): CaptureDirection? {
        val existing = existingShots.map(::toVector)
        val best = candidates.asSequence()
            .filter { candidate -> existing.none { angularDistance(candidate, it) < 1e-7 } }
            .maxByOrNull { candidate -> if (existing.isEmpty()) PI else existing.minOf { angularDistance(candidate, it) } }
        return best?.let(::toDirection)
    }

    fun snapshot(existingShots: List<CaptureDirection>, coveredWithinDegrees: Double = 18.0): CoverageSnapshot {
        require(coveredWithinDegrees.isFinite() && coveredWithinDegrees in 1.0..90.0)
        val existing = existingShots.map(::toVector)
        val covered = candidates.count { candidate ->
            existing.any { angularDistance(candidate, it) <= Math.toRadians(coveredWithinDegrees) }
        }
        return CoverageSnapshot(nextShot(existingShots), covered, candidates.size, covered * 100.0 / candidates.size)
    }

    private data class Vector(val x: Double, val y: Double, val z: Double)

    private fun fibonacciSphere(count: Int): List<Vector> {
        val goldenAngle = PI * (3.0 - sqrt(5.0))
        return (0 until count).map { index ->
            val y = 1.0 - (2.0 * (index + 0.5) / count)
            val radius = sqrt(1.0 - y * y)
            val theta = goldenAngle * index
            Vector(cos(theta) * radius, y, sin(theta) * radius)
        }
    }

    private fun toVector(direction: CaptureDirection): Vector {
        require(direction.yawDegrees.isFinite() && direction.yawDegrees in -180.0..180.0)
        require(direction.pitchDegrees.isFinite() && direction.pitchDegrees in -90.0..90.0)
        val yaw = Math.toRadians(direction.yawDegrees)
        val pitch = Math.toRadians(direction.pitchDegrees)
        val horizontal = cos(pitch)
        return Vector(horizontal * cos(yaw), sin(pitch), horizontal * sin(yaw))
    }

    private fun toDirection(vector: Vector) = CaptureDirection(
        Math.toDegrees(atan2(vector.z, vector.x)),
        Math.toDegrees(asin(vector.y.coerceIn(-1.0, 1.0)))
    )

    private fun angularDistance(a: Vector, b: Vector): Double {
        val dot = (a.x * b.x + a.y * b.y + a.z * b.z).coerceIn(-1.0, 1.0)
        return acos(dot)
    }
}
