import { ExpoPanoramaView } from 'expo-panorama-view';

const url = 'https://pannellum.org/images/alma.jpg';

export default function App() {
  return (
    <ExpoPanoramaView
      style={styles.view}
      source={{ uri: url }}
      enableMotion
      onTap={() => console.log('Tapped!')}
    />
  );
}

const styles = {
  view: { flex: 1 },
};
