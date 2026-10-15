import { requireNativeView } from 'expo';
import * as React from 'react';

import type { ExpoPanoramaViewProps, ExpoPanoramaViewRef } from './ExpoPanoramaView.types';

const NativeView = requireNativeView<
  ExpoPanoramaViewProps & React.RefAttributes<ExpoPanoramaViewRef>
>('ExpoPanoramaView');

const ExpoPanoramaView = React.forwardRef<ExpoPanoramaViewRef, ExpoPanoramaViewProps>(
  function ExpoPanoramaView(props, ref) {
    return <NativeView {...props} ref={ref} />;
  }
);

export default ExpoPanoramaView;
