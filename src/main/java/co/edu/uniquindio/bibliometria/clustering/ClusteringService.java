package co.edu.uniquindio.bibliometria.clustering;

import co.edu.uniquindio.bibliometria.corpus.Article;
import co.edu.uniquindio.bibliometria.corpus.Corpus;
import co.edu.uniquindio.bibliometria.similarity.TfIdfModel;
import co.edu.uniquindio.bibliometria.similarity.ai.SentenceEmbeddingClient;
import co.edu.uniquindio.bibliometria.similarity.ai.VectorMath;
import co.edu.uniquindio.bibliometria.similarity.ai.WordEmbeddingSimilarity;
import co.edu.uniquindio.bibliometria.text.TextPreprocessor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Requerimiento 2: preprocesamiento → matriz de distancias → tres criterios de enlace →
 * dendrogramas → evaluación con métricas internas.
 */
public class ClusteringService {

    public static final int MIN_K = 2;
    public static final int MAX_K = 8;

    private final Corpus corpus;
    private final TfIdfModel tfidf;
    private final WordEmbeddingSimilarity wordVectors;
    private final SentenceEmbeddingClient minilm;

    public ClusteringService(Corpus corpus, TfIdfModel tfidf, WordEmbeddingSimilarity wordVectors,
                             SentenceEmbeddingClient minilm) {
        this.corpus = corpus;
        this.tfidf = tfidf;
        this.wordVectors = wordVectors;
        this.minilm = minilm;
    }

    /** Matriz de distancias d(i, j) = 1 − cos(v_i, v_j) según la representación elegida. */
    public double[][] distanceMatrix(List<Article> arts, String representation) throws IOException {
        int n = arts.size();
        double[][] d = new double[n][n];
        switch (representation) {
            case "tfidf" -> {
                List<Map<String, Double>> v = new ArrayList<>();
                for (Article a : arts) {
                    v.add(tfidf.vector(a));
                }
                for (int i = 0; i < n; i++) {
                    for (int j = i + 1; j < n; j++) {
                        d[i][j] = d[j][i] = 1 - TfIdfModel.cosine(v.get(i), v.get(j));
                    }
                }
            }
            case "word-vectors", "minilm" -> {
                List<double[]> v = new ArrayList<>();
                if (representation.equals("minilm")) {
                    v.addAll(minilm.embed(arts).values());
                } else {
                    for (Article a : arts) {
                        v.add(wordVectors.documentVector(a.abstractText()).vector());
                    }
                }
                for (int i = 0; i < n; i++) {
                    for (int j = i + 1; j < n; j++) {
                        d[i][j] = d[j][i] = 1 - VectorMath.cosine(v.get(i), v.get(j));
                    }
                }
            }
            default -> throw new IllegalArgumentException("Representación desconocida: " + representation);
        }
        return d;
    }

    public Map<String, Object> run(String representation, int k) throws IOException {
        List<Article> arts = corpus.all();
        int n = arts.size();
        double[][] d = distanceMatrix(arts, representation);
        List<String> labels = new ArrayList<>();
        for (Article a : arts) {
            labels.add(a.shortLabel());
        }

        List<Map<String, Object>> results = new ArrayList<>();
        String bestLinkage = null;
        double bestCcc = Double.NEGATIVE_INFINITY;
        for (Linkage linkage : Linkage.values()) {
            List<HierarchicalClustering.Merge> merges = HierarchicalClustering.run(d, linkage);
            double ccc = ClusterMetrics.copheneticCorrelation(d, HierarchicalClustering.cophenetic(merges, n));

            Map<String, Object> silhouettes = new LinkedHashMap<>();
            int bestK = MIN_K;
            double bestSil = Double.NEGATIVE_INFINITY;
            for (int kk = MIN_K; kk <= Math.min(MAX_K, n - 1); kk++) {
                double s = ClusterMetrics.silhouette(d, HierarchicalClustering.cut(merges, n, kk));
                silhouettes.put(String.valueOf(kk), s);
                if (s > bestSil) {
                    bestSil = s;
                    bestK = kk;
                }
            }
            int shownK = k > 0 ? k : bestK;
            int[] groups = HierarchicalClustering.cut(merges, n, shownK);

            List<Map<String, Object>> mergeRows = new ArrayList<>();
            for (int t = 0; t < merges.size(); t++) {
                HierarchicalClustering.Merge m = merges.get(t);
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("paso", t + 1);
                row.put("izquierda", nodeName(m.left(), n, labels));
                row.put("derecha", nodeName(m.right(), n, labels));
                row.put("distancia", m.distance());
                row.put("tamano", m.size());
                row.put("nodo", "G" + (m.node() - n + 1));
                mergeRows.add(row);
            }

            List<List<String>> clusters = new ArrayList<>();
            for (int c = 0; c < shownK; c++) {
                clusters.add(new ArrayList<>());
            }
            for (int i = 0; i < n; i++) {
                clusters.get(groups[i]).add(arts.get(i).id());
            }

            Map<String, Object> r = new LinkedHashMap<>();
            r.put("enlace", linkage.name().toLowerCase());
            r.put("nombre", linkage.label());
            r.put("correlacionCofenetica", ccc);
            r.put("silueta", silhouettes);
            r.put("mejorK", bestK);
            r.put("siluetaMejorK", bestSil);
            r.put("kMostrado", shownK);
            r.put("siluetaKMostrado", ClusterMetrics.silhouette(d, groups));
            r.put("grupos", clusters);
            r.put("uniones", mergeRows);
            r.put("svg", DendrogramSvg.render(merges, labels, shownK, linkage.label()));
            results.add(r);
            if (ccc > bestCcc) {
                bestCcc = ccc;
                bestLinkage = linkage.name().toLowerCase();
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("representacion", representation);
        out.put("documentos", n);
        out.put("etiquetas", labels);
        out.put("matrizDistancias", d);
        out.put("resultados", results);
        out.put("mejorPorCorrelacionCofenetica", bestLinkage);
        out.put("ejemploPreprocesamiento", preprocessingExample(arts.get(0)));
        return out;
    }

    private static String nodeName(int node, int n, List<String> labels) {
        return node < n ? labels.get(node) : "G" + (node - n + 1);
    }

    private Map<String, Object> preprocessingExample(Article a) {
        String sentence = a.abstractText();
        int cut = 0;
        for (int i = 0; i < sentence.length(); i++) {
            if (sentence.charAt(i) == '.') {
                cut = i + 1;
                break;
            }
        }
        if (cut == 0) {
            cut = Math.min(200, sentence.length());
        }
        String first = sentence.substring(0, cut);
        Map<String, Object> ex = new LinkedHashMap<>();
        ex.put("articulo", a.shortLabel());
        ex.put("original", first);
        ex.put("normalizado", TextPreprocessor.normalize(first));
        ex.put("tokens", TextPreprocessor.tokenize(first));
        ex.put("sinStopwords", TextPreprocessor.contentTokens(first));
        ex.put("terminos", TextPreprocessor.terms(first));
        return ex;
    }
}
