package co.edu.uniquindio.bibliometria.similarity;

import co.edu.uniquindio.bibliometria.corpus.Article;

/**
 * Distancia de Levenshtein por programación dinámica (documento, apartado 7.1.1).
 *
 * <p>D[i][j] = número mínimo de inserciones, eliminaciones y sustituciones para convertir los
 * primeros i símbolos de A en los primeros j símbolos de B.</p>
 * <pre>
 * D[i][0] = i,  D[0][j] = j
 * D[i][j] = min( D[i-1][j] + 1,  D[i][j-1] + 1,  D[i-1][j-1] + (a_i == b_j ? 0 : 1) )
 * </pre>
 * <p>Tiempo O(n·m). Similitud = 1 − D[n][m] / max(n, m).</p>
 */
public class Levenshtein implements SimilarityAlgorithm {

    private final Sequences.Level level;

    public Levenshtein(Sequences.Level level) {
        this.level = level;
    }

    public Sequences.Level level() {
        return level;
    }

    @Override
    public String key() {
        return "levenshtein";
    }

    @Override
    public String name() {
        return "Distancia de Levenshtein (" + level.name().toLowerCase() + ")";
    }

    @Override
    public String family() {
        return "clasico";
    }

    @Override
    public double similarity(Article a, Article b) {
        Sequences.Pair p = Sequences.encode(a.abstractText(), b.abstractText(), level, Integer.MAX_VALUE);
        return normalized(distance(p.a(), p.b()), p.a().length, p.b().length);
    }

    /** Similitud en [0, 1] a partir de la distancia. */
    public static double normalized(int d, int n, int m) {
        int max = Math.max(n, m);
        return max == 0 ? 1.0 : 1.0 - (double) d / max;
    }

    /**
     * Distancia D[n][m]. Como cada fila solo depende de la anterior, basta con guardar dos filas
     * (espacio O(m)) en lugar de la matriz completa.
     */
    public static int distance(int[] a, int[] b) {
        int m = b.length;
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];

        // Paso 1: fila 0. Convertir el texto vacío en los primeros j símbolos de B cuesta j inserciones.
        for (int j = 0; j <= m; j++) {
            prev[j] = j;
        }
        for (int i = 1; i <= a.length; i++) {
            // Paso 2: columna 0. Convertir i símbolos de A en el texto vacío cuesta i eliminaciones.
            curr[0] = i;
            // Paso 3: cada celda toma la mejor de sus tres vecinas ya calculadas.
            for (int j = 1; j <= m; j++) {
                int cost = a[i - 1] == b[j - 1] ? 0 : 1;
                curr[j] = min3(prev[j] + 1,          // eliminar a_i
                        curr[j - 1] + 1,               // insertar b_j
                        prev[j - 1] + cost);           // coincidencia (0) o sustitución (1)
            }
            // Paso 4: la fila actual pasa a ser la anterior.
            int[] tmp = prev;
            prev = curr;
            curr = tmp;
        }
        // Paso 5: la distancia es la última celda, D[n][m].
        return prev[m];
    }

    /** La misma recurrencia, pero guardando la matriz completa (la usa la traza del caso de estudio). */
    public static int[][] matrix(int[] a, int[] b) {
        int[][] d = new int[a.length + 1][b.length + 1];
        for (int i = 0; i <= a.length; i++) {
            d[i][0] = i;
        }
        for (int j = 0; j <= b.length; j++) {
            d[0][j] = j;
        }
        for (int i = 1; i <= a.length; i++) {
            for (int j = 1; j <= b.length; j++) {
                int cost = a[i - 1] == b[j - 1] ? 0 : 1;
                d[i][j] = min3(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost);
            }
        }
        return d;
    }

    private static int min3(int x, int y, int z) {
        int m = x < y ? x : y;
        return m < z ? m : z;
    }
}
