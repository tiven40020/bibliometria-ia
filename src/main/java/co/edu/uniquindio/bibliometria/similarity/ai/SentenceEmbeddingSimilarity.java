package co.edu.uniquindio.bibliometria.similarity.ai;

import co.edu.uniquindio.bibliometria.corpus.Article;
import co.edu.uniquindio.bibliometria.similarity.SimilarityAlgorithm;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

/**
 * Modelo de IA 2: embeddings de oración con el modelo de lenguaje all-MiniLM-L6-v2.
 *
 * <p>A diferencia del promedio de palabras, este modelo (un Transformer de 6 capas) produce un
 * vector que depende del contexto: la misma palabra aporta distinto según las palabras que la
 * rodean. La aplicación solo obtiene el vector; el coseno y la distancia euclidiana se calculan
 * con {@link VectorMath}.</p>
 */
public class SentenceEmbeddingSimilarity implements SimilarityAlgorithm {

    private final SentenceEmbeddingClient client;

    public SentenceEmbeddingSimilarity(SentenceEmbeddingClient client) {
        this.client = client;
    }

    @Override
    public String key() {
        return "minilm";
    }

    @Override
    public String name() {
        return "Embeddings de oración all-MiniLM-L6-v2 (384d)";
    }

    @Override
    public String family() {
        return "ia";
    }

    /** Vectores de los dos abstracts (desde la caché o consultando la API). */
    public double[][] vectors(Article a, Article b) {
        try {
            Map<String, double[]> v = client.embed(List.of(a, b));
            return new double[][]{v.get(a.id()), v.get(b.id())};
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Precalcula (y guarda en caché) los embeddings de una lista de artículos en una sola consulta. */
    public void warmUp(List<Article> articles) throws IOException {
        client.embed(articles);
    }

    @Override
    public double similarity(Article a, Article b) {
        // Paso 1: obtener los dos vectores. Paso 2: coseno entre ellos, acotado a [0, 1].
        double[][] v = vectors(a, b);
        return VectorMath.cosineToUnit(VectorMath.cosine(v[0], v[1]));
    }
}
