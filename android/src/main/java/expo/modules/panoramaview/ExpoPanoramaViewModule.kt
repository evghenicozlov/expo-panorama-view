package expo.modules.panoramaview

import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition

class ExpoPanoramaViewModule : Module() {
  override fun definition() = ModuleDefinition {
    Name("ExpoPanoramaView")

    View(ExpoPanoramaView::class) {
      // Defines an event that the view can send to JavaScript.
      Events("onTap")
    }
  }
}
