package co.edu.uniquindio.bibliometria.traza;

import co.edu.uniquindio.bibliometria.corpus.Article;
import co.edu.uniquindio.bibliometria.similarity.ai.SentenceEmbeddingClient;
import co.edu.uniquindio.bibliometria.similarity.ai.SentenceEmbeddingSimilarity;
import co.edu.uniquindio.bibliometria.similarity.ai.VectorMath;
import co.edu.uniquindio.bibliometria.similarity.ai.WordEmbeddingSimilarity;
import co.edu.uniquindio.bibliometria.similarity.ai.WordEmbeddingSimilarity.DocumentVector;
import co.edu.uniquindio.bibliometria.similarity.ai.WordEmbeddings;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Trazas de los dos modelos de IA: vectores, producto punto, normas, coseno y distancia euclidiana. */
public final class TrazaEmbeddings {

    private TrazaEmbeddings() {
    }

    /** Modelo 1: promedio de embeddings estáticos de palabras. */
    public static Map<String, Object> palabras(WordEmbeddingSimilarity alg, Article a, Article b, int limit) {
        WordEmbeddings embeddings = alg.embeddings();
        DocumentVector va = alg.documentVector(a.abstractText());
        DocumentVector vb = alg.documentVector(b.abstractText());
        double dot = VectorMath.dot(va.vector(), vb.vector());
        double na = VectorMath.norm(va.vector());
        double nb = VectorMath.norm(vb.vector());
        double cos = na == 0 || nb == 0 ? 0 : dot / (na * nb);

        List<Map<String, Object>> sampleA = new ArrayList<>();
        for (int i = 0; i < Math.min(5, va.used().size()); i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("palabra", va.used().get(i));
            row.put("primerasComponentes", VectorMath.head(embeddings.get(va.used().get(i)), 6));
            sampleA.add(row);
        }

        Map<String, Object> t = new LinkedHashMap<>();
        t.put("modelo", "Vectores preentrenados de spaCy en_core_web_md 3.8.0, " + embeddings.dimension() + " dimensiones");
        t.put("formula", "v = (1/k) Σ e(w_i);  cos(θ) = (vA·vB)/(‖vA‖‖vB‖);  similitud = max(0, cos)");
        t.put("palabrasUsadasA", va.used().size());
        t.put("palabrasUsadasB", vb.used().size());
        t.put("fueraDeVocabularioA", va.outOfVocabulary());
        t.put("fueraDeVocabularioB", vb.outOfVocabulary());
        t.put("ejemploVectoresA", sampleA);
        t.put("vectorPromedioA", VectorMath.head(va.vector(), limit));
        t.put("vectorPromedioB", VectorMath.head(vb.vector(), limit));
        t.put("productoPunto", dot);
        t.put("normaA", na);
        t.put("normaB", nb);
        t.put("coseno", cos);
        t.put("anguloGrados", Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos)))));
        t.put("distanciaEuclidiana", VectorMath.euclidean(va.vector(), vb.vector()));
        t.put("similitud", VectorMath.cosineToUnit(cos));
        return t;
    }

    /** Modelo 2: embeddings de oración con all-MiniLM-L6-v2. */
    public static Map<String, Object> oracion(SentenceEmbeddingSimilarity alg, Article a, Article b, int limit) {
        double[][] v = alg.vectors(a, b);
        double dot = VectorMath.dot(v[0], v[1]);
        double na = VectorMath.norm(v[0]);
        double nb = VectorMath.norm(v[1]);
        double cos = na == 0 || nb == 0 ? 0 : dot / (na * nb);

        Map<String, Object> t = new LinkedHashMap<>();
        t.put("modelo", SentenceEmbeddingClient.MODEL + " (Transformer de 6 capas, mean pooling)");
        t.put("formula", "cos(θ) = (vA·vB)/(‖vA‖‖vB‖);  similitud = max(0, cos)");
        t.put("dimension", v[0].length);
        t.put("vectorA", VectorMath.head(v[0], limit));
        t.put("vectorB", VectorMath.head(v[1], limit));
        t.put("productoPunto", dot);
        t.put("normaA", na);
        t.put("normaB", nb);
        t.put("coseno", cos);
        t.put("anguloGrados", Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, cos)))));
        t.put("distanciaEuclidiana", VectorMath.euclidean(v[0], v[1]));
        t.put("similitud", VectorMath.cosineToUnit(cos));
        return t;
    }
}
