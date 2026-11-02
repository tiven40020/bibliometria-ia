package co.edu.uniquindio.bibliometria.similarity.ai;

import co.edu.uniquindio.bibliometria.corpus.Article;
import co.edu.uniquindio.bibliometria.similarity.SimilarityAlgorithm;
import co.edu.uniquindio.bibliometria.text.TextPreprocessor;

import java.util.ArrayList;
import java.util.List;

/**
 * Modelo de IA 1: promedio de embeddings estáticos de palabras (cada palabra tiene un único vector,
 * sin importar el contexto, como en Word2Vec, GloVe o FastText).
 *
 * <ol>
 *   <li>Se toman las palabras de contenido del abstract (sin stopwords).</li>
 *   <li>Cada palabra se reemplaza por su vector preentrenado de 300 dimensiones; las palabras
 *       fuera del vocabulario del modelo se descartan.</li>
 *   <li>El abstract se representa por el promedio de esos vectores: v = (1/k) Σ e(w_i).</li>
 *   <li>La similitud es el coseno entre los dos vectores promedio, acotado a [0, 1].</li>
 * </ol>
 */
public class WordEmbeddingSimilarity implements SimilarityAlgorithm {

    private final WordEmbeddings embeddings;

    public WordEmbeddingSimilarity(WordEmbeddings embeddings) {
        this.embeddings = embeddings;
    }

    @Override
    public String key() {
        return "word-vectors";
    }

    @Override
    public String name() {
        return "Embeddings estáticos de palabras (promedio, " + embeddings.dimension() + "d)";
    }

    @Override
    public String family() {
        return "ia";
    }

    /** Vector del documento y las palabras usadas y descartadas al construirlo. */
    public record DocumentVector(double[] vector, List<String> used, List<String> outOfVocabulary) {
    }

    public DocumentVector documentVector(String text) {
        double[] sum = new double[embeddings.dimension()];
        List<String> used = new ArrayList<>();
        List<String> oov = new ArrayList<>();
        // Paso 1: sumar el vector de cada palabra de contenido que esté en el vocabulario del modelo.
        for (String w : TextPreprocessor.contentTokens(text)) {
            double[] e = embeddings.get(w);
            if (e == null) {
                oov.add(w); // palabra sin vector: se descarta
                continue;
            }
            for (int i = 0; i < sum.length; i++) {
                sum[i] += e[i];
            }
            used.add(w);
        }
        // Paso 2: dividir entre k (número de palabras usadas) para obtener el promedio.
        if (!used.isEmpty()) {
            for (int i = 0; i < sum.length; i++) {
                sum[i] /= used.size();
            }
        }
        return new DocumentVector(sum, used, oov);
    }

    public double rawCosine(Article a, Article b) {
        return VectorMath.cosine(documentVector(a.abstractText()).vector(),
                documentVector(b.abstractText()).vector());
    }

    @Override
    public double similarity(Article a, Article b) {
        return VectorMath.cosineToUnit(rawCosine(a, b));
    }

    public WordEmbeddings embeddings() {
        return embeddings;
    }
}
