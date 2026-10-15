import * as React from 'react';

import { ExpoPanoramaViewProps } from './ExpoPanoramaView.types';

export default function ExpoPanoramaView(props: ExpoPanoramaViewProps) {
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
      onClick={() => props.onTap({ nativeEvent: {} })}>
      <span>ExpoPanoramaView - native view</span>
      <span>Tap the view to emit a view event</span>
    </div>
  );
}
