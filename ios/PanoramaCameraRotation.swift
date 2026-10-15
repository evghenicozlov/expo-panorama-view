import simd

/// Angles are radians in the camera's local screen coordinate system.
enum PanoramaCameraRotation {
  static func applyingPan(to rotation: simd_quatf, horizontal: Float, vertical: Float) -> simd_quatf {
    let yaw = simd_quatf(angle: horizontal, axis: SIMD3<Float>(0, 1, 0))
    let pitch = simd_quatf(angle: vertical, axis: SIMD3<Float>(1, 0, 0))
    return simd_normalize(rotation * yaw * pitch)
  }
}
