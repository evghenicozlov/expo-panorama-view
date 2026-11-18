package expo.modules.panoramaview

import kotlin.math.*

internal data class PanoramaRotation(val x: Float = 0f, val y: Float = 0f, val z: Float = 0f, val w: Float = 1f) {
  operator fun times(b: PanoramaRotation) = PanoramaRotation(
    w*b.x+x*b.w+y*b.z-z*b.y, w*b.y-x*b.z+y*b.w+z*b.x,
    w*b.z+x*b.y-y*b.x+z*b.w, w*b.w-x*b.x-y*b.y-z*b.z
  ).normalized()
  fun normalized(): PanoramaRotation {
    val n = sqrt(x*x+y*y+z*z+w*w)
    return if (n > 0f) PanoramaRotation(x/n,y/n,z/n,w/n) else PanoramaRotation()
  }
  // Store the screen-space drag before motion so later sensor deltas stay camera-local.
  fun applyingPan(motion: PanoramaRotation, delta: PanoramaRotation) =
    this * motion * delta * motion.inverse()

  fun initialTilt(): PanoramaRotation {
    // Third row is sensor-world up expressed in screen coordinates.
    val m = matrix()
    val horizontal = hypot(m[2], m[5])
    val pitch = atan2(-m[8], horizontal)
    val roll = if (horizontal < 0.01f) 0f else atan2(m[2], m[5])
    return axis(pitch, 1f, 0f, 0f) * axis(roll, 0f, 0f, 1f)
  }

  fun inverse() = PanoramaRotation(-x,-y,-z,w)
  fun matrix() = floatArrayOf(
    1-2*(y*y+z*z), 2*(x*y+z*w), 2*(x*z-y*w),
    2*(x*y-z*w), 1-2*(x*x+z*z), 2*(y*z+x*w),
    2*(x*z+y*w), 2*(y*z-x*w), 1-2*(x*x+y*y)
  )
  companion object {
    fun axis(angle: Float, x: Float, y: Float, z: Float): PanoramaRotation {
      val s = sin(angle / 2)
      return PanoramaRotation(x*s,y*s,z*s,cos(angle/2))
    }
  }
}
