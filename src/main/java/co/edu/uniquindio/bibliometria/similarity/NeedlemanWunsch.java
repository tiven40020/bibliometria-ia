package co.edu.uniquindio.bibliometria.similarity;

import co.edu.uniquindio.bibliometria.corpus.Article;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Alineamiento global de Needleman-Wunsch (documento, apartado 7.1.1).
 *
 * <pre>
 * F[i][0] = i·g,  F[0][j] = j·g
 * F[i][j] = max( F[i-1][j-1] + s(a_i, b_j),  F[i-1][j] + g,  F[i][j-1] + g )
 * </pre>
 * <p>s = +1 si los símbolos coinciden y −1 si no; hueco g = −1. Tiempo y espacio O(n·m).
 * Similitud = coincidencias / longitud del alineamiento.</p>
 */
public class NeedlemanWunsch implements SimilarityAlgorithm {

    public static final int MATCH = 1;
    public static final int MISMATCH = -1;
    public static final int GAP = -1;

    private final Sequences.Level level;

    public NeedlemanWunsch(Sequences.Level level) {
        this.level = level;
    }

    public Sequences.Level level() {
        return level;
    }

    @Override
    public String key() {
        return "needleman-wunsch";
    }

    @Override
    public String name() {
        return "Needleman-Wunsch (" + level.name().toLowerCase() + ")";
    }

    @Override
    public String family() {
        return "clasico";
    }

    /** Resultado del alineamiento: puntaje, matriz y las dos secuencias alineadas. */
    public record Alignment(int score, int[][] matrix, List<String> alignedA, List<String> alignedB,
                            int matches, int mismatches, int gaps) {
        public int length() {
            return alignedA.size();
        }

        public double identity() {
            return length() == 0 ? 1.0 : (double) matches / length();
        }
    }

    public static Alignment align(int[] a, int[] b, List<String> sa, List<String> sb) {
        int n = a.length;
        int m = b.length;
        int[][] f = new int[n + 1][m + 1];

        // Paso 1: primera columna y primera fila. Cada hueco inicial resta 1.
        for (int i = 0; i <= n; i++) {
            f[i][0] = i * GAP;
        }
        for (int j = 0; j <= m; j++) {
            f[0][j] = j * GAP;
        }

        // Paso 2: cada celda toma el mejor de los tres caminos posibles.
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int diag = f[i - 1][j - 1] + (a[i - 1] == b[j - 1] ? MATCH : MISMATCH); // emparejar a_i con b_j
                int up = f[i - 1][j] + GAP;                                              // hueco en B
                int left = f[i][j - 1] + GAP;                                            // hueco en A
                int best = diag;
                if (up > best) {
                    best = up;
                }
                if (left > best) {
                    best = left;
                }
                f[i][j] = best;
            }
        }

        // Paso 3: traceback desde F[n][m] hasta F[0][0] para recuperar el alineamiento.
        // En caso de empate se prefiere la diagonal, luego arriba y luego izquierda.
        List<String> ra = new ArrayList<>();
        List<String> rb = new ArrayList<>();
        int matches = 0;
        int mismatches = 0;
        int gaps = 0;
        int i = n;
        int j = m;
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0
                    && f[i][j] == f[i - 1][j - 1] + (a[i - 1] == b[j - 1] ? MATCH : MISMATCH)) {
                ra.add(sa.get(i - 1));
                rb.add(sb.get(j - 1));
                if (a[i - 1] == b[j - 1]) {
                    matches++;
                } else {
                    mismatches++;
                }
                i--;
                j--;
            } else if (i > 0 && f[i][j] == f[i - 1][j] + GAP) {
                ra.add(sa.get(i - 1));
                rb.add("-");
                gaps++;
                i--;
            } else {
                ra.add("-");
                rb.add(sb.get(j - 1));
                gaps++;
                j--;
            }
        }
        // El traceback recorre el alineamiento al revés.
        Collections.reverse(ra);
        Collections.reverse(rb);
        return new Alignment(f[n][m], f, ra, rb, matches, mismatches, gaps);
    }

    @Override
    public double similarity(Article a, Article b) {
        Sequences.Pair p = Sequences.encode(a.abstractText(), b.abstractText(), level, Integer.MAX_VALUE);
        return align(p.a(), p.b(), p.symbolsA(), p.symbolsB()).identity();
    }
}
