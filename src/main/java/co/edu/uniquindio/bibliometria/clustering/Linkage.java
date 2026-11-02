package co.edu.uniquindio.bibliometria.clustering;

/**
 * Criterios de enlace para el agrupamiento jerárquico aglomerativo. Al unir los grupos i y j,
 * la distancia del nuevo grupo a cualquier otro grupo k se actualiza con la fórmula de
 * Lance-Williams correspondiente.
 */
public enum Linkage {

    /** Enlace simple: distancia entre los dos puntos más cercanos. d(i∪j, k) = min(d(i,k), d(j,k)). */
    SINGLE("Enlace simple (single linkage)") {
        @Override
        double update(double dik, double djk, int ni, int nj) {
            return Math.min(dik, djk);
        }
    },

    /** Enlace completo: distancia entre los dos puntos más lejanos. d(i∪j, k) = max(d(i,k), d(j,k)). */
    COMPLETE("Enlace completo (complete linkage)") {
        @Override
        double update(double dik, double djk, int ni, int nj) {
            return Math.max(dik, djk);
        }
    },

    /**
     * Enlace promedio (UPGMA): promedio de todas las distancias entre los puntos de ambos grupos.
     * d(i∪j, k) = (|i|·d(i,k) + |j|·d(j,k)) / (|i| + |j|).
     */
    AVERAGE("Enlace promedio (average linkage, UPGMA)") {
        @Override
        double update(double dik, double djk, int ni, int nj) {
            return (ni * dik + nj * djk) / (ni + nj);
        }
    };

    private final String label;

    Linkage(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    abstract double update(double dik, double djk, int ni, int nj);
}
