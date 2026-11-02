package co.edu.uniquindio.bibliometria.similarity;

import co.edu.uniquindio.bibliometria.corpus.Article;

/**
 * Similitud del coseno entre los vectores TF-IDF de dos abstracts (documento, apartado 7.1.1):
 * cos(θ) = (A · B) / (‖A‖ · ‖B‖).
 *
 * <p>Los pesos w(t, d) = tf(t, d) · idf(t) se calculan en {@link TfIdfModel}, y el coseno en
 * {@link TfIdfModel#cosine}. Como los pesos son no negativos, el resultado está en [0, 1].
 * Costo por par: O(|A| + |B|).</p>
 */
public class CosineTfIdf implements SimilarityAlgorithm {

    private final TfIdfModel model;

    public CosineTfIdf(TfIdfModel model) {
        this.model = model;
    }

    public TfIdfModel model() {
        return model;
    }

    @Override
    public String key() {
        return "coseno-tfidf";
    }

    @Override
    public String name() {
        return "Similitud del coseno (TF-IDF)";
    }

    @Override
    public String family() {
        return "clasico";
    }

    @Override
    public double similarity(Article a, Article b) {
        // Paso 1: vector TF-IDF de cada abstract. Paso 2: coseno entre los dos vectores.
        return TfIdfModel.cosine(model.vector(a), model.vector(b));
    }
}
