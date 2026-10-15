package expo.modules.panoramaview
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
class ExpoPanoramaViewModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("ExpoPanoramaView")
    View(ExpoPanoramaView::class) {
      Events("onTap", "onLoad", "onError", "onCameraChange")
      Prop("source") { view: ExpoPanoramaView, value: Map<String, String>? -> view.setSource(value?.get("uri")) }
      Prop("yaw") { view: ExpoPanoramaView, value: Double? -> view.setYaw(value ?: 0.0) }
      Prop("pitch") { view: ExpoPanoramaView, value: Double? -> view.setPitch(value ?: 0.0) }
      Prop("zoom") { view: ExpoPanoramaView, value: Double? -> view.setZoom(value ?: 1.0) }
      Prop("enableGestures") { view: ExpoPanoramaView, value: Boolean? -> view.setEnableGestures(value ?: true) }
      Prop("enableMotion") { view: ExpoPanoramaView, value: Boolean? -> view.setEnableMotion(value ?: false) }
      AsyncFunction("resetMotion") { view: ExpoPanoramaView -> view.resetMotion() }
    }
  }
}
