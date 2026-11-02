package co.edu.uniquindio.bibliometria.similarity;

import co.edu.uniquindio.bibliometria.corpus.Article;
import co.edu.uniquindio.bibliometria.text.TextPreprocessor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Modelo de espacio vectorial TF-IDF construido sobre todo el corpus.
 *
 * <ul>
 *   <li>tf(t, d): número de veces que el término t aparece en el abstract d.</li>
 *   <li>df(t): número de abstracts que contienen t.</li>
 *   <li>idf(t) = ln((1 + N) / (1 + df(t))) + 1 (versión suavizada, evita divisiones por cero
 *       y que un término presente en todos los documentos tenga peso nulo).</li>
 *   <li>w(t, d) = tf(t, d) · idf(t).</li>
 * </ul>
 */
public class TfIdfModel {

    private final int documents;
    private final Map<String, Integer> df = new HashMap<>();
    private final Map<String, Map<String, Integer>> tfByArticle = new HashMap<>();

    public TfIdfModel(List<Article> corpus) {
        this.documents = corpus.size();
        for (Article a : corpus) {
            Map<String, Integer> tf = termFrequencies(TextPreprocessor.terms(a.abstractText()));
            tfByArticle.put(a.id(), tf);
            for (String term : tf.keySet()) {
                df.merge(term, 1, Integer::sum);
            }
        }
    }

    static Map<String, Integer> termFrequencies(List<String> terms) {
        Map<String, Integer> tf = new HashMap<>();
        for (String t : terms) {
            tf.merge(t, 1, Integer::sum);
        }
        return tf;
    }

    public int documents() {
        return documents;
    }

    public int df(String term) {
        return df.getOrDefault(term, 0);
    }

    public double idf(String term) {
        return Math.log((1.0 + documents) / (1.0 + df(term))) + 1.0;
    }

    public Map<String, Integer> tf(Article a) {
        Map<String, Integer> tf = tfByArticle.get(a.id());
        return tf != null ? tf : termFrequencies(TextPreprocessor.terms(a.abstractText()));
    }

    public Map<String, Double> vector(Article a) {
        Map<String, Double> v = new HashMap<>();
        for (Map.Entry<String, Integer> e : tf(a).entrySet()) {
            v.put(e.getKey(), e.getValue() * idf(e.getKey()));
        }
        return v;
    }

    public Set<String> vocabulary() {
        return new HashSet<>(df.keySet());
    }

    public static double cosine(Map<String, Double> x, Map<String, Double> y) {
        double dot = 0;
        for (Map.Entry<String, Double> e : x.entrySet()) {
            Double other = y.get(e.getKey());
            if (other != null) {
                dot += e.getValue() * other;
            }
        }
        double nx = norm(x);
        double ny = norm(y);
        return nx == 0 || ny == 0 ? 0 : dot / (nx * ny);
    }

    public static double norm(Map<String, Double> v) {
        double s = 0;
        for (double w : v.values()) {
            s += w * w;
        }
        return Math.sqrt(s);
    }
}
