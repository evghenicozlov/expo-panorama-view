import { requireNativeView } from 'expo';
import * as React from 'react';

import { ExpoPanoramaViewProps } from './ExpoPanoramaView.types';

const NativeView: React.ComponentType<ExpoPanoramaViewProps> = requireNativeView('ExpoPanoramaView');

export default function ExpoPanoramaView(props: ExpoPanoramaViewProps) {
  return <NativeView {...props} />;
}
