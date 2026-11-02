package co.edu.uniquindio.bibliometria.traza;

import co.edu.uniquindio.bibliometria.corpus.Article;
import co.edu.uniquindio.bibliometria.similarity.Levenshtein;
import co.edu.uniquindio.bibliometria.similarity.Sequences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Traza de Levenshtein para el caso de estudio: matriz, camino óptimo y operaciones de edición. */
public final class TrazaLevenshtein {

    private TrazaLevenshtein() {
    }

    /** Recupera una secuencia óptima de operaciones recorriendo la matriz desde D[n][m]. */
    public static List<String> editScript(int[][] d, List<String> sa, List<String> sb) {
        List<String> ops = new ArrayList<>();
        int i = sa.size();
        int j = sb.size();
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0 && sa.get(i - 1).equals(sb.get(j - 1)) && d[i][j] == d[i - 1][j - 1]) {
                ops.add("coincide '" + sa.get(i - 1) + "'");
                i--;
                j--;
            } else if (i > 0 && j > 0 && d[i][j] == d[i - 1][j - 1] + 1) {
                ops.add("sustituir '" + sa.get(i - 1) + "' por '" + sb.get(j - 1) + "'");
                i--;
                j--;
            } else if (i > 0 && d[i][j] == d[i - 1][j] + 1) {
                ops.add("eliminar '" + sa.get(i - 1) + "'");
                i--;
            } else {
                ops.add("insertar '" + sb.get(j - 1) + "'");
                j--;
            }
        }
        Collections.reverse(ops);
        return ops;
    }

    public static Map<String, Object> generar(Levenshtein alg, Article a, Article b, int limit) {
        Sequences.Level level = alg.level();
        Sequences.Pair full = Sequences.encode(a.abstractText(), b.abstractText(), level, Integer.MAX_VALUE);
        int dFull = Levenshtein.distance(full.a(), full.b());

        Sequences.Pair p = Sequences.encode(a.abstractText(), b.abstractText(), level, limit);
        int[][] d = Levenshtein.matrix(p.a(), p.b());
        int dPrefix = d[p.a().length][p.b().length];

        Map<String, Object> t = new LinkedHashMap<>();
        t.put("nivel", level.name().toLowerCase());
        t.put("formula", "D[i][j] = min(D[i-1][j]+1, D[i][j-1]+1, D[i-1][j-1] + (a_i==b_j ? 0 : 1))");
        t.put("prefijoA", p.symbolsA());
        t.put("prefijoB", p.symbolsB());
        t.put("matriz", d);
        t.put("operaciones", editScript(d, p.symbolsA(), p.symbolsB()));
        t.put("distanciaPrefijo", dPrefix);
        t.put("similitudPrefijo", Levenshtein.normalized(dPrefix, p.a().length, p.b().length));
        t.put("longitudA", full.a().length);
        t.put("longitudB", full.b().length);
        t.put("distanciaCompleta", dFull);
        t.put("celdasCalculadas", (long) full.a().length * full.b().length);
        t.put("similitud", Levenshtein.normalized(dFull, full.a().length, full.b().length));
        return t;
    }
}
