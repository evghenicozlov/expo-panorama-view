import { ExpoPanoramaView, type ExpoPanoramaViewRef } from 'expo-panorama-view';
import { useRef } from 'react';
import { Button, Platform, View } from 'react-native';

const url = 'https://pannellum.org/images/alma.jpg';

export default function App() {
  const panoramaRef = useRef<ExpoPanoramaViewRef>(null);
  return (
    <View style={styles.view}>
      <ExpoPanoramaView
        ref={panoramaRef}
        style={styles.view}
        source={{ uri: url }}
        enableMotion
        onTap={() => console.log('Tapped!')}
      />
      {Platform.OS === 'ios' && (
        <View style={{ padding: 16, paddingBottom: 32 }}>
          <Button
            title="Reset position"
            onPress={() => {
              panoramaRef.current?.resetMotion().catch(console.warn);
            }}
          />
        </View>
      )}
    </View>
  );
}

const styles = {
  view: { flex: 1 },
};
