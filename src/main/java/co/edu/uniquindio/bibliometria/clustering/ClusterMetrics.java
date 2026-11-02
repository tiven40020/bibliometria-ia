package co.edu.uniquindio.bibliometria.clustering;

/**
 * Métricas internas de evaluación del agrupamiento (no requieren etiquetas reales).
 *
 * <ul>
 *   <li><b>Coeficiente de correlación cofenética (CCC)</b> de Sokal y Rohlf (1962): correlación de
 *       Pearson entre las distancias originales y las distancias cofenéticas del dendrograma.
 *       Mide qué tan fielmente el árbol conserva las distancias reales; cuanto más cerca de 1, mejor.</li>
 *   <li><b>Coeficiente de silueta</b> de Rousseeuw (1987): para cada documento i,
 *       s(i) = (b(i) − a(i)) / max(a(i), b(i)), donde a(i) es la distancia media a su propio grupo y
 *       b(i) la menor distancia media a otro grupo. El promedio está en [−1, 1]; valores altos indican
 *       grupos compactos y bien separados.</li>
 * </ul>
 */
public final class ClusterMetrics {

    private ClusterMetrics() {
    }

    public static double copheneticCorrelation(double[][] original, double[][] cophenetic) {
        int n = original.length;
        int pairs = n * (n - 1) / 2;
        double[] x = new double[pairs];
        double[] y = new double[pairs];
        int k = 0;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                x[k] = original[i][j];
                y[k] = cophenetic[i][j];
                k++;
            }
        }
        return pearson(x, y);
    }

    static double pearson(double[] x, double[] y) {
        double mx = 0;
        double my = 0;
        for (int i = 0; i < x.length; i++) {
            mx += x[i];
            my += y[i];
        }
        mx /= x.length;
        my /= y.length;
        double num = 0;
        double sx = 0;
        double sy = 0;
        for (int i = 0; i < x.length; i++) {
            num += (x[i] - mx) * (y[i] - my);
            sx += (x[i] - mx) * (x[i] - mx);
            sy += (y[i] - my) * (y[i] - my);
        }
        return sx == 0 || sy == 0 ? 0 : num / Math.sqrt(sx * sy);
    }

    /** Silueta media. Los documentos que forman un grupo de un solo elemento tienen s(i) = 0. */
    public static double silhouette(double[][] d, int[] labels) {
        int n = labels.length;
        int k = 0;
        for (int l : labels) {
            k = Math.max(k, l + 1);
        }
        if (k < 2 || k >= n) {
            return 0;
        }
        double total = 0;
        for (int i = 0; i < n; i++) {
            double[] sum = new double[k];
            int[] count = new int[k];
            for (int j = 0; j < n; j++) {
                if (j != i) {
                    sum[labels[j]] += d[i][j];
                    count[labels[j]]++;
                }
            }
            if (count[labels[i]] == 0) {
                continue; // grupo unitario: s(i) = 0
            }
            double a = sum[labels[i]] / count[labels[i]];
            double b = Double.POSITIVE_INFINITY;
            for (int c = 0; c < k; c++) {
                if (c != labels[i] && count[c] > 0) {
                    b = Math.min(b, sum[c] / count[c]);
                }
            }
            double max = Math.max(a, b);
            total += max == 0 ? 0 : (b - a) / max;
        }
        return total / n;
    }
}
