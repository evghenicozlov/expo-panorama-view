import simd

/// Angles are radians in the camera's local screen coordinate system.
enum PanoramaCameraRotation {
  /// Sensor world uses +Z up; the panorama uses +Y up and looks along -Z.
  static func initialTilt(screen: simd_quatf) -> simd_quatf {
    let up = screen.inverse.act(SIMD3<Float>(0, 0, 1))
    let horizontal = hypot(up.x, up.y)
    let pitch = atan2(-up.z, horizontal)
    // Facing straight up/down, roll cannot be determined from gravity.
    let roll: Float = horizontal < 0.01 ? 0 : atan2(up.x, up.y)
    return simd_quatf(angle: pitch, axis: [1, 0, 0]) * simd_quatf(angle: roll, axis: [0, 0, 1])
  }

  static func applyingPan(to rotation: simd_quatf, motion: simd_quatf, horizontal: Float, vertical: Float) -> simd_quatf {
    let yaw = simd_quatf(angle: horizontal, axis: SIMD3<Float>(0, 1, 0))
    let pitch = simd_quatf(angle: vertical, axis: SIMD3<Float>(1, 0, 0))
    return simd_normalize(rotation * motion * yaw * pitch * motion.inverse)
  }
}
