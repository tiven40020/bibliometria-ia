package co.edu.uniquindio.bibliometria.similarity;

import co.edu.uniquindio.bibliometria.corpus.Article;
import co.edu.uniquindio.bibliometria.corpus.Corpus;
import co.edu.uniquindio.bibliometria.similarity.ai.SentenceEmbeddingSimilarity;
import co.edu.uniquindio.bibliometria.similarity.ai.WordEmbeddingSimilarity;
import co.edu.uniquindio.bibliometria.traza.TrazaCoseno;
import co.edu.uniquindio.bibliometria.traza.TrazaEmbeddings;
import co.edu.uniquindio.bibliometria.traza.TrazaJaccard;
import co.edu.uniquindio.bibliometria.traza.TrazaLevenshtein;
import co.edu.uniquindio.bibliometria.traza.TrazaNeedlemanWunsch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Requerimiento 1: ejecuta los seis algoritmos de similitud sobre los artículos seleccionados. */
public class SimilarityService {

    private final Corpus corpus;
    private final TfIdfModel tfidf;
    private final WordEmbeddingSimilarity wordVectors;
    private final SentenceEmbeddingSimilarity minilm;

    public SimilarityService(Corpus corpus, WordEmbeddingSimilarity wordVectors, SentenceEmbeddingSimilarity minilm) {
        this.corpus = corpus;
        this.tfidf = new TfIdfModel(corpus.all());
        this.wordVectors = wordVectors;
        this.minilm = minilm;
    }

    public TfIdfModel tfidf() {
        return tfidf;
    }

    /** Instancia el algoritmo pedido. Levenshtein y Needleman-Wunsch aceptan el nivel (caracter o palabra). */
    public SimilarityAlgorithm algorithm(String key, String level) {
        return switch (key) {
            case "levenshtein" -> new Levenshtein(Sequences.parseLevel(level, Sequences.Level.CARACTER));
            case "needleman-wunsch" -> new NeedlemanWunsch(Sequences.parseLevel(level, Sequences.Level.PALABRA));
            case "coseno-tfidf" -> new CosineTfIdf(tfidf);
            case "jaccard" -> new Jaccard();
            case "word-vectors" -> wordVectors;
            case "minilm" -> minilm;
            default -> throw new IllegalArgumentException("Algoritmo desconocido: " + key);
        };
    }

    public static List<String> allKeys() {
        return List.of("levenshtein", "needleman-wunsch", "coseno-tfidf", "jaccard", "word-vectors", "minilm");
    }

    /**
     * Compara todos los pares de artículos seleccionados con cada algoritmo. Si un algoritmo falla
     * (por ejemplo, la API de MiniLM no está configurada) se informa el error sin afectar a los demás.
     */
    public Map<String, Object> compare(List<String> ids, List<String> keys, Map<String, String> levels) {
        if (ids.size() < 2) {
            throw new IllegalArgumentException("Seleccione al menos dos artículos");
        }
        List<Article> selected = new ArrayList<>();
        for (String id : ids) {
            selected.add(corpus.get(id));
        }

        List<Map<String, Object>> pairs = new ArrayList<>();
        for (int i = 0; i < selected.size(); i++) {
            for (int j = i + 1; j < selected.size(); j++) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("a", selected.get(i).id());
                p.put("b", selected.get(j).id());
                p.put("autoresCompartidos", Corpus.sharedAuthors(selected.get(i), selected.get(j)));
                p.put("valores", new LinkedHashMap<String, Object>());
                pairs.add(p);
            }
        }

        List<Map<String, Object>> algorithms = new ArrayList<>();
        for (String key : keys) {
            SimilarityAlgorithm alg = algorithm(key, levels.get(key));
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("key", key);
            info.put("nombre", alg.name());
            info.put("familia", alg.family());
            try {
                if (alg instanceof SentenceEmbeddingSimilarity s) {
                    s.warmUp(selected); // una sola consulta a la API para todos los abstracts
                }
                long start = System.nanoTime();
                double[][] m = new double[selected.size()][selected.size()];
                int k = 0;
                for (int i = 0; i < selected.size(); i++) {
                    m[i][i] = 1.0;
                    for (int j = i + 1; j < selected.size(); j++) {
                        double s = alg.similarity(selected.get(i), selected.get(j));
                        m[i][j] = s;
                        m[j][i] = s;
                        @SuppressWarnings("unchecked")
                        Map<String, Object> vals = (Map<String, Object>) pairs.get(k++).get("valores");
                        vals.put(key, s);
                    }
                }
                info.put("matriz", m);
                info.put("tiempoMs", (System.nanoTime() - start) / 1e6);
            } catch (java.io.UncheckedIOException e) {
                info.put("error", e.getCause().getMessage());
            } catch (RuntimeException | IOException e) {
                info.put("error", e.getMessage());
            }
            algorithms.add(info);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        List<Map<String, Object>> arts = new ArrayList<>();
        for (Article a : selected) {
            arts.add(a.toMap(false));
        }
        out.put("articulos", arts);
        out.put("algoritmos", algorithms);
        out.put("pares", pairs);
        return out;
    }

    public Map<String, Object> trace(String idA, String idB, String key, String level, int limit) {
        Article a = corpus.get(idA);
        Article b = corpus.get(idB);
        SimilarityAlgorithm alg = algorithm(key, level);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("algoritmo", alg.name());
        out.put("a", a.toMap(true));
        out.put("b", b.toMap(true));
        out.put("autoresCompartidos", Corpus.sharedAuthors(a, b));
        out.put("traza", trace(alg, a, b, limit));
        return out;
    }

    /** Elige la clase de traza que corresponde a cada algoritmo. */
    private static Map<String, Object> trace(SimilarityAlgorithm alg, Article a, Article b, int limit) {
        if (alg instanceof Levenshtein lev) {
            return TrazaLevenshtein.generar(lev, a, b, limit);
        }
        if (alg instanceof NeedlemanWunsch nw) {
            return TrazaNeedlemanWunsch.generar(nw, a, b, limit);
        }
        if (alg instanceof CosineTfIdf cos) {
            return TrazaCoseno.generar(cos.model(), a, b, limit);
        }
        if (alg instanceof Jaccard) {
            return TrazaJaccard.generar(a, b);
        }
        if (alg instanceof WordEmbeddingSimilarity wv) {
            return TrazaEmbeddings.palabras(wv, a, b, limit);
        }
        if (alg instanceof SentenceEmbeddingSimilarity minilm) {
            return TrazaEmbeddings.oracion(minilm, a, b, limit);
        }
        throw new IllegalArgumentException("Algoritmo sin traza: " + alg.key());
    }
}
