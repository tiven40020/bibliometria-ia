package co.edu.uniquindio.bibliometria.similarity;

import co.edu.uniquindio.bibliometria.corpus.Article;
import co.edu.uniquindio.bibliometria.text.TextPreprocessor;

import java.util.Set;

/**
 * Coeficiente de Jaccard sobre los conjuntos de términos de cada abstract (documento, apartado 7.1.1):
 * J(A, B) = |A ∩ B| / |A ∪ B|. Ignora la frecuencia y el orden de las palabras.
 * Costo esperado: O(|A| + |B|) con conjuntos hash.
 */
public class Jaccard implements SimilarityAlgorithm {

    @Override
    public String key() {
        return "jaccard";
    }

    @Override
    public String name() {
        return "Coeficiente de Jaccard";
    }

    @Override
    public String family() {
        return "clasico";
    }

    public static double jaccard(Set<String> a, Set<String> b) {
        if (a.isEmpty() && b.isEmpty()) {
            return 1.0;
        }
        // Paso 1: contar la intersección recorriendo A y consultando B.
        int inter = 0;
        for (String x : a) {
            if (b.contains(x)) {
                inter++;
            }
        }
        // Paso 2: |A ∪ B| = |A| + |B| − |A ∩ B|.
        int union = a.size() + b.size() - inter;
        // Paso 3: dividir.
        return (double) inter / union;
    }

    @Override
    public double similarity(Article a, Article b) {
        return jaccard(TextPreprocessor.termSet(a.abstractText()), TextPreprocessor.termSet(b.abstractText()));
    }
}
