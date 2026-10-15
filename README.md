# expo-panorama-view

Native 360° photo viewer for Expo and React Native. The panorama renderer is currently implemented on **iOS**; Android and web still contain the generated placeholder views.

## Usage (iOS)

Use an equirectangular panorama covering 360° × 180°, ideally with a 2:1 aspect ratio. Ordinary photos will appear stretched. With `yaw={0}` and `pitch={0}` (the defaults), the camera starts at the center of the photo. `resetMotion()` returns to this center unless custom base angles are configured.

```tsx
import { ExpoPanoramaView } from 'expo-panorama-view';

export function PanoramaScreen() {
  return (
    <ExpoPanoramaView
      source={{ uri: 'https://your-server.example/panorama.jpg' }}
      yaw={0}
      pitch={0}
      zoom={1}
      enableGestures
      enableMotion
      onLoad={({ nativeEvent }) => console.log(nativeEvent.width, nativeEvent.height)}
      onError={({ nativeEvent }) => console.warn(nativeEvent.message)}
      style={{ flex: 1 }}
    />
  );
}
```

`source` accepts `{ uri: string }` with an HTTPS, HTTP, or local `file://` URI. Prefer HTTPS; iOS transport security can block HTTP. Resolve bundled assets to a local URI before passing them. Changing or removing the source cancels the previous request and clears the previous image.

Drag to look around; pinch to zoom. Dragging rotates around the current camera’s screen axes, including when the phone is tilted or in landscape. Gesture rotation is stored separately from the base `yaw` and `pitch` props, so dragging can look past the base pitch limits. `yaw` and `pitch` are degrees. Pitch is limited to −89…89; zoom is limited to 1…4. Gestures update the camera internally and emit `onCameraChange` with `{ yaw, pitch, zoom }` under `nativeEvent`. Changing a camera prop updates that camera value. `enableGestures` defaults to `true`. `onTap` is also available.

The renderer uses SceneKit without third-party native dependencies. Video and Street View are not implemented.

## Motion controls (iOS)

Set `enableMotion` to follow the phone using CoreMotion (gyroscope and accelerometer). It defaults to `false`. The first sensor sample anchors the current view, so enabling motion does not jump to an absolute heading. Portrait and landscape screen axes are supported. Drag and pinch remain available; `enableGestures={false}` disables them independently.

Motion stops when the view leaves its window or the app becomes inactive, and resumes with a new anchor. Turning motion off freezes the current orientation. `onCameraChange` reports the rendered camera angles (Euler degrees, which can wrap at ±180°). Sensor errors and unavailable hardware are reported through `onError`; gestures still work. Test on a physical iPhone — the simulator does not provide device motion.

## Development

This module requires a native development build; it is not included in Expo Go. After changing Swift code, rebuild the example:

```sh
cd example
npx expo run:ios
```

## Rotation regression check

```sh
xcrun swiftc ios/PanoramaCameraRotation.swift tests/panorama-rotation/main.swift -o /tmp/panorama-rotation-test
/tmp/panorama-rotation-test
```

Checks screen-relative pan in portrait, landscape, upside-down and tilted poses, and quaternion stability over repeated gestures.

If the example cannot find the native module after updating CocoaPods, run `pod install` with `example/ios` as the working directory, then rebuild the app. Running CocoaPods from the package root can generate the module provider using the wrong Expo configuration. JavaScript refresh alone cannot restore a missing native registration.

```sh
cd example/ios
pod install
cd ..
npx expo run:ios
```

## Reset motion (iOS)

Use a typed ref to reset the fused gyroscope/accelerometer reference:

```tsx
import { useRef } from 'react';
import { Button } from 'react-native';
import { ExpoPanoramaView, type ExpoPanoramaViewRef } from 'expo-panorama-view';

export function Panorama() {
  const ref = useRef<ExpoPanoramaViewRef>(null);
  return (
    <>
      <ExpoPanoramaView
        ref={ref}
        source={{ uri: 'https://pannellum.org/images/alma.jpg' }}
        enableMotion
        style={{ flex: 1 }}
      />
      <Button title="Reset" onPress={() => {
        ref.current?.resetMotion().catch(console.warn);
      }} />
    </>
  );
}
```

`resetMotion(): Promise<void>` clears device and drag rotation, keeps the configured `yaw`, `pitch`, and current zoom, emits `onCameraChange`, and takes the next fresh motion sample as its reference. It also works with motion disabled. It recenters the viewer rather than physically calibrating the sensors. The promise resolves after the reset is applied; the next sensor sample arrives asynchronously. Rebuild the native app after adding this method.
