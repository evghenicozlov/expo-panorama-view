import simd

func expectSameRotation(_ lhs: simd_quatf, _ rhs: simd_quatf) {
  precondition(abs(simd_dot(lhs.vector, rhs.vector)) > 0.99999, "Camera rotation differs")
}

let identity = simd_quatf(angle: 0, axis: SIMD3<Float>(0, 1, 0))
let yawDelta = simd_quatf(angle: 0.1, axis: SIMD3<Float>(0, 1, 0))
let pitchDelta = simd_quatf(angle: -0.1, axis: SIMD3<Float>(1, 0, 0))
// Test portrait, both landscape rotations, upside down, and an arbitrary pose.
for roll: Float in [0, .pi / 2, -.pi / 2, .pi, 0.7] {
  let device = simd_quatf(angle: roll, axis: SIMD3<Float>(0, 0, 1))
    * simd_quatf(angle: 0.4, axis: SIMD3<Float>(1, 0, 0))
  let previousDrag = simd_quatf(angle: 0.3, axis: SIMD3<Float>(0, 1, 0))
  let camera = device * previousDrag
  let horizontal = PanoramaCameraRotation.applyingPan(to: previousDrag, horizontal: 0.1, vertical: 0)
  let vertical = PanoramaCameraRotation.applyingPan(to: previousDrag, horizontal: 0, vertical: -0.1)
  // In the camera's coordinate system the delta must be identical in every pose.
  expectSameRotation(camera.inverse * device * horizontal, yawDelta)
  expectSameRotation(camera.inverse * device * vertical, pitchDelta)
}
var accumulated = identity
for _ in 0..<10000 {
  accumulated = PanoramaCameraRotation.applyingPan(to: accumulated, horizontal: 0.001, vertical: -0.002)
}
precondition(abs(simd_length(accumulated.vector) - 1) < 0.00001)
print("Screen-relative pan checks passed for 5 poses; repeated rotations stay normalized.")
