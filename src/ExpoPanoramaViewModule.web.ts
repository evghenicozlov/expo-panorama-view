import { registerWebModule, NativeModule } from 'expo';

class ExpoPanoramaViewModule extends NativeModule<{}> {}

export default registerWebModule(ExpoPanoramaViewModule, 'ExpoPanoramaViewModule');
