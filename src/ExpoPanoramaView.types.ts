import type { StyleProp, ViewStyle } from 'react-native';

export type OnTapEventPayload = Record<string, never>;
export type PanoramaCamera = { yaw: number; pitch: number; zoom: number };
type ViewEvent<T> = { nativeEvent: T };

export type ExpoPanoramaViewProps = {
  /** iOS: equirectangular 360° image, ideally 2:1. Supports HTTP(S) and file URIs. */
  source?: { uri: string };
  /** iOS: horizontal rotation in degrees. Defaults to 0. */
  yaw?: number;
  /** iOS: vertical rotation in degrees, clamped to -89…89. Defaults to 0. */
  pitch?: number;
  /** iOS: magnification, clamped to 1…4. Defaults to 1. */
  zoom?: number;
  /** iOS: enables pan and pinch gestures. Defaults to true. */
  enableGestures?: boolean;
  /** iOS: follows device orientation using CoreMotion. Defaults to false. */
  enableMotion?: boolean;
  onTap?: (event: ViewEvent<OnTapEventPayload>) => void;
  onLoad?: (event: ViewEvent<{ width: number; height: number }>) => void;
  onError?: (event: ViewEvent<{ message: string }>) => void;
  onCameraChange?: (event: ViewEvent<PanoramaCamera>) => void;
  style?: StyleProp<ViewStyle>;
};
