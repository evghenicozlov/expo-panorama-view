import { NativeModule, requireNativeModule } from 'expo';

declare class ExpoPanoramaViewModule extends NativeModule<{}> {}

export default requireNativeModule<ExpoPanoramaViewModule>('ExpoPanoramaView');
