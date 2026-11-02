package co.edu.uniquindio.bibliometria.similarity.ai;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Tabla de vectores de palabras preentrenados (embeddings estáticos), en formato texto:
 * una palabra por línea seguida de sus componentes separados por espacios.
 *
 * <p>Los vectores provienen del modelo en_core_web_md 3.8.0 de spaCy (300 dimensiones; "Explosion
 * Vectors" entrenados sobre OSCAR 21.09, Wikipedia, OpenSubtitles y WMT News Crawl). Para que la
 * aplicación sea liviana, el archivo incluido solo contiene las palabras que aparecen en los
 * abstracts del corpus (ver tools/export_word_vectors.py).</p>
 */
public class WordEmbeddings {

    private final Map<String, double[]> vectors = new HashMap<>();
    private int dimension;

    public static WordEmbeddings load(InputStream in) throws IOException {
        WordEmbeddings e = new WordEmbeddings();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split(" ");
                double[] v = new double[parts.length - 1];
                for (int i = 1; i < parts.length; i++) {
                    v[i - 1] = Double.parseDouble(parts[i]);
                }
                e.dimension = v.length;
                e.vectors.put(parts[0], v);
            }
        }
        return e;
    }

    public double[] get(String word) {
        return vectors.get(word);
    }

    public int dimension() {
        return dimension;
    }

    public int size() {
        return vectors.size();
    }
}
