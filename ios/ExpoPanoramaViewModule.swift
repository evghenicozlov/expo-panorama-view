import ExpoModulesCore

public class ExpoPanoramaViewModule: Module {
  public func definition() -> ModuleDefinition {
    Name("ExpoPanoramaView")

    View(ExpoPanoramaView.self) {
      Events("onTap")
    }
  }
}
