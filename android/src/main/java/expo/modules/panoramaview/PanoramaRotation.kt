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
