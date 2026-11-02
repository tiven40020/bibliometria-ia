package co.edu.uniquindio.bibliometria.matching;

import java.util.List;

/**
 * Algoritmo de coincidencia exacta de cadenas. El trabajo se divide en dos fases para poder
 * medirlas por separado: el preprocesamiento del patrón y la búsqueda sobre el texto.
 */
public interface StringMatcher {

    String key();

    String name();

    /** Construye las estructuras auxiliares a partir del patrón. */
    Object preprocess(char[] pattern);

    /** Busca todas las ocurrencias usando las estructuras ya construidas. */
    Result search(char[] text, char[] pattern, Object preprocessed);

    /**
     * Resultado de una búsqueda: posiciones de inicio de cada ocurrencia, número de comparaciones
     * de caracteres, número de alineamientos probados y un contador propio de cada algoritmo
     * (por ejemplo, colisiones de hash en Rabin-Karp).
     */
    record Result(List<Integer> positions, long comparisons, long alignments, long extra) {
    }
}
