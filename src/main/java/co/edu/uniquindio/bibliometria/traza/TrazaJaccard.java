package co.edu.uniquindio.bibliometria.traza;

import co.edu.uniquindio.bibliometria.corpus.Article;
import co.edu.uniquindio.bibliometria.text.TextPreprocessor;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Traza de Jaccard: tamaños de los conjuntos y términos compartidos. */
public final class TrazaJaccard {

    private TrazaJaccard() {
    }

    public static Map<String, Object> generar(Article a, Article b) {
        Set<String> sa = new TreeSet<>(TextPreprocessor.termSet(a.abstractText()));
        Set<String> sb = new TreeSet<>(TextPreprocessor.termSet(b.abstractText()));
        Set<String> inter = new TreeSet<>();
        for (String x : sa) {
            if (sb.contains(x)) {
                inter.add(x);
            }
        }
        int union = sa.size() + sb.size() - inter.size();

        Map<String, Object> t = new LinkedHashMap<>();
        t.put("formula", "J(A,B) = |A ∩ B| / |A ∪ B| = |A ∩ B| / (|A| + |B| - |A ∩ B|)");
        t.put("tamanoA", sa.size());
        t.put("tamanoB", sb.size());
        t.put("interseccion", inter);
        t.put("tamanoInterseccion", inter.size());
        t.put("tamanoUnion", union);
        t.put("similitud", union == 0 ? 1.0 : (double) inter.size() / union);
        return t;
    }
}
