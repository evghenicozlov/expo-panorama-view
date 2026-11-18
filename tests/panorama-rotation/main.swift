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
  let camera = previousDrag * device
  let horizontal = PanoramaCameraRotation.applyingPan(to: previousDrag, motion: device, horizontal: 0.1, vertical: 0)
  let vertical = PanoramaCameraRotation.applyingPan(to: previousDrag, motion: device, horizontal: 0, vertical: -0.1)
  // In the camera's coordinate system the delta must be identical in every pose.
  expectSameRotation(camera.inverse * horizontal * device, yawDelta)
  expectSameRotation(camera.inverse * vertical * device, pitchDelta)
}
let device = simd_quatf(angle: 0.6, axis: SIMD3<Float>(0, 0, 1))
let turn = PanoramaCameraRotation.applyingPan(to: identity, motion: device, horizontal: .pi, vertical: 0)
let afterSwipe = turn * device
for axis in [SIMD3<Float>(1, 0, 0), SIMD3<Float>(0, 0, 1)] {
  let tilt = simd_quatf(angle: 0.2, axis: axis)
  expectSameRotation(afterSwipe.inverse * turn * (device * tilt), tilt)
}
var accumulated = identity
for _ in 0..<10000 {
  accumulated = PanoramaCameraRotation.applyingPan(to: accumulated, motion: identity, horizontal: 0.001, vertical: -0.002)
}
precondition(abs(simd_length(accumulated.vector) - 1) < 0.00001)
print("Screen-relative pan checks passed for 5 poses; repeated rotations stay normalized.")


// A continuous circular drag returns to its original pose after each lap.
for step in 0...720 {
  let angle = Float(step % 72) * 2 * .pi / 72
  let pose = PanoramaCameraRotation.applyingPan(to: turn, motion: device,
    horizontal: 0.4 * (cos(angle) - 1), vertical: 0.4 * sin(angle))
  if step % 72 == 0 { expectSameRotation(pose, turn) }
}

// Synthetic sensor poses: earth is Z-up; an upright screen is rotated 90° about X.
let upright = simd_quatf(angle: .pi / 2, axis: [1, 0, 0])
for elevation: Float in [-.pi / 2, -0.6, 0, 0.4, .pi / 2] {
  let roll: Float = abs(elevation) > 1.5 ? 0 : 0.55
  let tilt = simd_quatf(angle: elevation, axis: [1, 0, 0]) * simd_quatf(angle: roll, axis: [0, 0, 1])
  let heading = simd_quatf(angle: 1.2, axis: [0, 0, 1])
  expectSameRotation(PanoramaCameraRotation.initialTilt(screen: heading * upright * tilt), tilt)
}
