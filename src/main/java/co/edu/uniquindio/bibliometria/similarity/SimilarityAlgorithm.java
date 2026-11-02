package co.edu.uniquindio.bibliometria.similarity;

import co.edu.uniquindio.bibliometria.corpus.Article;

/**
 * Algoritmo de similitud textual entre los abstracts de dos artículos. Las trazas paso a paso de
 * cada algoritmo están aparte, en el paquete {@code traza}.
 */
public interface SimilarityAlgorithm {

    /** Identificador corto usado por la API, por ejemplo "levenshtein". */
    String key();

    /** Nombre legible del algoritmo. */
    String name();

    /** "clasico" o "ia". */
    String family();

    /** Similitud normalizada en el intervalo [0, 1]; 1 significa textos idénticos. */
    double similarity(Article a, Article b);
}
