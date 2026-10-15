import * as React from 'react';

import { ExpoPanoramaViewProps, ExpoPanoramaViewRef } from './ExpoPanoramaView.types';

const ExpoPanoramaView = React.forwardRef<ExpoPanoramaViewRef, ExpoPanoramaViewProps>(
  function ExpoPanoramaView(props, ref) {
    React.useImperativeHandle(ref, () => ({
      async resetMotion() {
        throw new Error('resetMotion is only available on iOS.');
      },
    }));
    return (
      <div
        style={{
          backgroundColor: '#aabbcc',
          flex: 1,
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
        }}
        onClick={() => props.onTap?.({ nativeEvent: {} })}>
        <span>ExpoPanoramaView - native view</span>
        <span>Tap the view to emit a view event</span>
      </div>
    );
  }
);

export default ExpoPanoramaView;
