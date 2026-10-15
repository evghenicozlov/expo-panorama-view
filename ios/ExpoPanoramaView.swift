import CoreMotion
import ExpoModulesCore
import SceneKit
import UIKit
import simd

/// Equirectangular (2:1) images viewed from the center of a sphere.
class ExpoPanoramaView: ExpoView {
  let onTap = EventDispatcher()
  let onLoadStart = EventDispatcher()
  let onLoadEnd = EventDispatcher()
  let onLoadError = EventDispatcher()
  let onCameraChange = EventDispatcher()

  private let sceneView = SCNView()
  private let cameraNode = SCNNode()
  private let material = SCNMaterial()
  private var imageTask: URLSessionDataTask?
  private var loadID = UUID()
  private var currentURI: String?
  private let motionManager = CMMotionManager()
  private let motionQueue = OperationQueue()
  private var motionEnabled = false
  private var motionSession = UUID()
  private var motionReference: simd_quatf?
  private var motionOffset = simd_quatf(angle: 0, axis: SIMD3<Float>(0, 1, 0))
  private var motionAnchor = simd_quatf(angle: 0, axis: SIMD3<Float>(0, 1, 0))
  private var motionInterfaceOrientation: UIInterfaceOrientation?
  private var gestureRotation = simd_quatf(angle: 0, axis: SIMD3<Float>(0, 1, 0))
  private var yaw = 0.0
  private var pitch = 0.0
  private var zoom = 1.0
  private var pinchStartZoom = 1.0
  private var panGesture: UIPanGestureRecognizer!
  private var pinchGesture: UIPinchGestureRecognizer!

  required init(appContext: AppContext? = nil) {
    super.init(appContext: appContext)
    motionQueue.maxConcurrentOperationCount = 1
    motionQueue.qualityOfService = .userInteractive
    NotificationCenter.default.addObserver(self, selector: #selector(updateMotionState),
      name: UIApplication.didBecomeActiveNotification, object: nil)
    NotificationCenter.default.addObserver(self, selector: #selector(stopMotion),
      name: UIApplication.willResignActiveNotification, object: nil)
    clipsToBounds = true
    sceneView.backgroundColor = .black
    sceneView.scene = SCNScene()
    sceneView.autoenablesDefaultLighting = false
    sceneView.allowsCameraControl = false
    addSubview(sceneView)

    let sphere = SCNSphere(radius: 10)
    sphere.segmentCount = 96
    material.lightingModel = .constant
    material.isDoubleSided = true
    // Flip horizontally so text and landmarks read correctly from inside.
    material.diffuse.contentsTransform = SCNMatrix4MakeScale(-1, 1, 1)
    material.diffuse.wrapS = .repeat
    sphere.firstMaterial = material
    let panoramaNode = SCNNode(geometry: sphere)
    // SCNSphere places UV (0.5, 0.5) on +Z; the camera looks along -Z.
    // Align the photo center with yaw = 0 without changing camera/gesture axes.
    panoramaNode.eulerAngles.y = .pi
    sceneView.scene?.rootNode.addChildNode(panoramaNode)

    cameraNode.camera = SCNCamera()
    cameraNode.camera?.projectionDirection = .vertical
    cameraNode.camera?.zNear = 0.01
    cameraNode.camera?.zFar = 20
    sceneView.scene?.rootNode.addChildNode(cameraNode)
    sceneView.pointOfView = cameraNode
    updateCamera()

    panGesture = UIPanGestureRecognizer(target: self, action: #selector(handlePan(_:)))
    pinchGesture = UIPinchGestureRecognizer(target: self, action: #selector(handlePinch(_:)))
    let tap = UITapGestureRecognizer(target: self, action: #selector(handleTap))
    tap.require(toFail: panGesture)
    sceneView.addGestureRecognizer(panGesture)
    sceneView.addGestureRecognizer(pinchGesture)
    sceneView.addGestureRecognizer(tap)
  }

  override func layoutSubviews() {
    super.layoutSubviews()
    sceneView.frame = bounds
  }

  override func didMoveToWindow() {
    super.didMoveToWindow()
    updateMotionState()
  }

  deinit {
    NotificationCenter.default.removeObserver(self)
    motionManager.stopDeviceMotionUpdates()
    imageTask?.cancel()
  }

  func setSource(_ uri: String?) {
    guard uri != currentURI else { return }
    currentURI = uri
    imageTask?.cancel()
    imageTask = nil
    loadID = UUID()
    material.diffuse.contents = nil
    guard let uri, !uri.isEmpty else { return }
    onLoadStart(["uri": uri])
    guard let url = URL(string: uri),
          let scheme = url.scheme?.lowercased(),
          ["http", "https", "file"].contains(scheme) else {
      onLoadError(["message": "Expected an http, https or file URI."])
      return
    }
    let requestID = loadID
    // URLSession performs file/network loading and image decoding off the UI thread.
    if url.isFileURL {
      DispatchQueue.global(qos: .userInitiated).async { [weak self] in
        let image = UIImage(contentsOfFile: url.path)
        DispatchQueue.main.async { [weak self] in
          self?.finishLoad(image, id: requestID, message: "Unable to read panorama image.")
        }
      }
      return
    }
    imageTask = URLSession.shared.dataTask(with: url) { [weak self] data, response, error in
      let success = (response as? HTTPURLResponse).map { (200..<300).contains($0.statusCode) } ?? false
      let image = success ? data.flatMap { UIImage(data: $0) } : nil
      let message = error?.localizedDescription ?? "Unable to load panorama image (HTTP \((response as? HTTPURLResponse)?.statusCode ?? 0))."
      DispatchQueue.main.async { [weak self] in
        self?.finishLoad(image, id: requestID, message: message)
      }
    }
    imageTask?.resume()
  }

  private func finishLoad(_ image: UIImage?, id: UUID, message: String) {
    guard id == loadID else { return }
    imageTask = nil
    guard let image else {
      onLoadError(["message": message])
      return
    }
    material.diffuse.contents = image
    onLoadEnd(["width": image.size.width * image.scale, "height": image.size.height * image.scale])
  }

  func setYaw(_ value: Double) {
    guard value.isFinite else { return }
    yaw = value.truncatingRemainder(dividingBy: 360)
    updateCamera()
  }

  func setPitch(_ value: Double) {
    guard value.isFinite else { return }
    pitch = min(89, max(-89, value))
    updateCamera()
  }

  func setZoom(_ value: Double) {
    guard value.isFinite else { return }
    zoom = min(4, max(1, value))
    updateCamera()
  }

  func setEnableGestures(_ enabled: Bool) {
    panGesture.isEnabled = enabled
    pinchGesture.isEnabled = enabled
  }

  /// Recenter fused gyroscope/accelerometer motion and clear accumulated drag.
  /// Keep the configured yaw, pitch and zoom as the starting view.
  func resetMotion() {
    stopMotion() // Invalidate queued samples before accepting a fresh reference.
    let identity = simd_quatf(angle: 0, axis: SIMD3<Float>(0, 1, 0))
    motionOffset = identity
    motionAnchor = identity
    gestureRotation = identity
    motionInterfaceOrientation = nil
    panGesture.setTranslation(.zero, in: sceneView)
    updateCamera()
    emitCameraChange()
    updateMotionState()
  }

  func setEnableMotion(_ enabled: Bool) {
    guard enabled != motionEnabled else { return }
    motionEnabled = enabled
    updateMotionState()
  }

  @objc private func updateMotionState() {
    let shouldRun = motionEnabled && window != nil && UIApplication.shared.applicationState == .active
    guard shouldRun else {
      stopMotion()
      return
    }
    guard !motionManager.isDeviceMotionActive else { return }
    guard motionManager.isDeviceMotionAvailable,
          CMMotionManager.availableAttitudeReferenceFrames().contains(.xArbitraryZVertical) else {
      onLoadError(["message": "Device motion is unavailable on this device."])
      return
    }
    motionReference = nil
    motionSession = UUID()
    let session = motionSession
    motionManager.deviceMotionUpdateInterval = 1.0 / 60.0
    motionManager.startDeviceMotionUpdates(using: .xArbitraryZVertical, to: motionQueue) { [weak self] motion, error in
      DispatchQueue.main.async { [weak self] in
        guard let self, self.motionSession == session else { return }
        if let error {
          self.motionManager.stopDeviceMotionUpdates()
          self.motionSession = UUID()
          self.onLoadError(["message": error.localizedDescription])
          return
        }
        guard let motion else { return }
        self.applyMotion(motion)
      }
    }
  }

  @objc private func stopMotion() {
    motionSession = UUID()
    motionManager.stopDeviceMotionUpdates()
    motionReference = nil
  }

  private func applyMotion(_ motion: CMDeviceMotion) {
    let orientation = window?.windowScene?.interfaceOrientation ?? .portrait
    if orientation != motionInterfaceOrientation {
      motionInterfaceOrientation = orientation
      motionReference = nil
    }
    // CoreMotion's quaternion maps device coordinates to its reference frame.
    // Rotate the screen axes for landscape before computing the relative rotation.
    let angle: Float
    switch orientation {
    case .landscapeLeft: angle = -.pi / 2
    case .landscapeRight: angle = .pi / 2
    case .portraitUpsideDown: angle = .pi
    default: angle = 0
    }
    let q = motion.attitude.quaternion
    let device = simd_quatf(ix: Float(q.x), iy: Float(q.y), iz: Float(q.z), r: Float(q.w))
    let screen = simd_normalize(device * simd_quatf(angle: angle, axis: SIMD3<Float>(0, 0, 1)))
    guard let reference = motionReference else {
      motionReference = screen
      motionAnchor = motionOffset
      return
    }
    motionOffset = simd_normalize(motionAnchor * reference.inverse * screen)
    updateCamera()
    emitCameraChange()
  }

  private func updateCamera() {
    let horizontal = simd_quatf(angle: Float(yaw * .pi / 180), axis: SIMD3<Float>(0, 1, 0))
    let vertical = simd_quatf(angle: Float(pitch * .pi / 180), axis: SIMD3<Float>(1, 0, 0))
    cameraNode.simdOrientation = simd_normalize(horizontal * vertical * motionOffset * gestureRotation)
    cameraNode.camera?.fieldOfView = 75 / zoom
  }

  private func emitCameraChange() {
    let angles = cameraNode.eulerAngles
    onCameraChange(["yaw": Double(angles.y) * 180 / .pi,
                    "pitch": Double(angles.x) * 180 / .pi, "zoom": zoom])
  }

  @objc private func handlePan(_ gesture: UIPanGestureRecognizer) {
    let translation = gesture.translation(in: sceneView)
    let degreesPerPoint = (75 / zoom) / Double(max(sceneView.bounds.height, 1))
    // Apply drag after device motion, around the rendered camera's screen axes.
    // Changing world yaw/pitch here makes swipes rotate with the phone's roll.
    gestureRotation = PanoramaCameraRotation.applyingPan(
      to: gestureRotation,
      horizontal: Float(Double(translation.x) * degreesPerPoint * .pi / 180),
      vertical: Float(Double(translation.y) * degreesPerPoint * .pi / 180)
    )
    updateCamera()
    gesture.setTranslation(.zero, in: sceneView)
    emitCameraChange()
  }

  @objc private func handlePinch(_ gesture: UIPinchGestureRecognizer) {
    if gesture.state == .began { pinchStartZoom = zoom }
    setZoom(pinchStartZoom * Double(gesture.scale))
    emitCameraChange()
  }

  @objc private func handleTap() {
    onTap([:])
  }
}
