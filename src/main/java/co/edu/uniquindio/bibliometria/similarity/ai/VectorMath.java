package co.edu.uniquindio.bibliometria.similarity.ai;

/** Operaciones sobre vectores densos implementadas con ciclos explícitos. */
public final class VectorMath {

    private VectorMath() {
    }

    public static double dot(double[] a, double[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) {
            s += a[i] * b[i];
        }
        return s;
    }

    public static double norm(double[] a) {
        return Math.sqrt(dot(a, a));
    }

    /** cos(θ) = (a · b) / (‖a‖ ‖b‖). */
    public static double cosine(double[] a, double[] b) {
        double na = norm(a);
        double nb = norm(b);
        return na == 0 || nb == 0 ? 0 : dot(a, b) / (na * nb);
    }

    /** d(a, b) = sqrt(Σ (a_i − b_i)²). */
    public static double euclidean(double[] a, double[] b) {
        double s = 0;
        for (int i = 0; i < a.length; i++) {
            double d = a[i] - b[i];
            s += d * d;
        }
        return Math.sqrt(s);
    }

    /**
     * Similitud reportada para los embeddings: el coseno, acotado a [0, 1] para compararlo con los
     * algoritmos clásicos (en la práctica los cosenos entre abstracts del mismo dominio son positivos).
     */
    public static double cosineToUnit(double cos) {
        return Math.max(0.0, Math.min(1.0, cos));
    }

    public static double[] head(double[] v, int n) {
        double[] r = new double[Math.min(n, v.length)];
        for (int i = 0; i < r.length; i++) {
            r[i] = Math.round(v[i] * 10000.0) / 10000.0;
        }
        return r;
    }
}
