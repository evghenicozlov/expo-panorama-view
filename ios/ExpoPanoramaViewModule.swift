import ExpoModulesCore

public class ExpoPanoramaViewModule: Module {
  public func definition() -> ModuleDefinition {
    Name("ExpoPanoramaView")

    View(ExpoPanoramaView.self) {
      Events("onTap", "onLoad", "onError", "onCameraChange")

      Prop("source") { (view: ExpoPanoramaView, source: [String: String]?) in
        view.setSource(source?["uri"])
      }
      Prop("yaw", 0.0) { (view: ExpoPanoramaView, value: Double) in
        view.setYaw(value)
      }
      Prop("pitch", 0.0) { (view: ExpoPanoramaView, value: Double) in
        view.setPitch(value)
      }
      Prop("zoom", 1.0) { (view: ExpoPanoramaView, value: Double) in
        view.setZoom(value)
      }
      Prop("enableMotion", false) { (view: ExpoPanoramaView, value: Bool) in
        view.setEnableMotion(value)
      }
      Prop("enableGestures", true) { (view: ExpoPanoramaView, value: Bool) in
        view.setEnableGestures(value)
      }
    }
  }
}
