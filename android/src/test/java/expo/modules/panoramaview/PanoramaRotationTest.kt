package expo.modules.panoramaview

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class PanoramaRotationTest {
  private fun assertRotation(a: PanoramaRotation, b: PanoramaRotation) {
    assertEquals(1f,abs(a.x*b.x+a.y*b.y+a.z*b.z+a.w*b.w),0.00001f)
  }
  @Test fun panStaysInScreenAxesForAllPhoneOrientations() {
    val delta=PanoramaRotation.axis(0.1f,0f,1f,0f)
    for(roll in listOf(0f,Math.PI.toFloat()/2,-Math.PI.toFloat()/2,Math.PI.toFloat(),0.7f)) {
      val device=PanoramaRotation.axis(roll,0f,0f,1f)*PanoramaRotation.axis(0.4f,1f,0f,0f)
      val drag=PanoramaRotation.axis(0.3f,0f,1f,0f)
      val before=device*drag
      val after=device*(drag*delta)
      assertRotation(before.inverse()*after,delta)
    }
  }
  @Test fun referenceSampleIsNeutralAndResetReturnsToCenter() {
    val reference=PanoramaRotation.axis(0.9f,0f,1f,0f)*PanoramaRotation.axis(-0.4f,1f,0f,0f)
    assertRotation(reference.inverse()*reference,PanoramaRotation())
    val matrix=PanoramaRotation().matrix()
    // The shader maps the default -Z ray to u=0.5, v=0.5.
    val x=-matrix[6]; val y=-matrix[7]; val z=-matrix[8]
    assertEquals(0.5,0.5+atan2(x.toDouble(),-z.toDouble())/(2*Math.PI),0.00001)
    assertEquals(0.5,0.5-asin(y.toDouble())/Math.PI,0.00001)
  }
  @Test fun repeatedRotationsStayNormalized() {
    var rotation=PanoramaRotation()
    repeat(10000) { rotation=rotation*PanoramaRotation.axis(0.001f,0f,1f,0f)*PanoramaRotation.axis(-0.002f,1f,0f,0f) }
    assertEquals(1f,sqrt(rotation.x*rotation.x+rotation.y*rotation.y+rotation.z*rotation.z+rotation.w*rotation.w),0.00001f)
  }
}
