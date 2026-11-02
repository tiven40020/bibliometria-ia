package co.edu.uniquindio.bibliometria.matching;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Algoritmo Boyer-Moore con las dos reglas, en la formulación de Charras y Lecroq
 * (documento, apartado 7.3.1).
 *
 * <p>El patrón se compara con el texto de derecha a izquierda. Ante una discrepancia en la
 * posición i del patrón, frente al carácter c del texto, se desplaza el patrón:</p>
 * <pre>
 * desplazamiento = max( bmGs[i],  bmBc[c] − (m − 1 − i) )
 * </pre>
 * <p>Preprocesamiento O(m + σ). Búsqueda O(n·m) en el peor caso; en texto natural cercana a n/m
 * comparaciones, porque el carácter malo permite saltar hasta m posiciones.</p>
 */
public class BoyerMoore implements StringMatcher {

    @Override
    public String key() {
        return "boyer-moore";
    }

    @Override
    public String name() {
        return "Boyer-Moore";
    }

    /** Estructuras auxiliares del patrón: las dos tablas y el arreglo suff que usa la segunda. */
    public record Tables(Map<Character, Integer> badChar, int[] goodSuffix, int[] suffixes, int m) {
        /** bmBc[c]; un carácter que no está en P[0..m−2] permite saltar m posiciones. */
        public int badCharShift(char c) {
            Integer v = badChar.get(c);
            return v == null ? m : v;
        }
    }

    /**
     * Regla del carácter malo: bmBc[c] = m − 1 − (última posición de c en P[0..m−2]).
     * Es la distancia desde la última aparición de c hasta el final del patrón.
     */
    public static Map<Character, Integer> badCharacterTable(char[] p) {
        int m = p.length;
        Map<Character, Integer> bc = new HashMap<>();
        for (int i = 0; i < m - 1; i++) {
            bc.put(p[i], m - 1 - i); // si c se repite, la aparición más a la derecha sobrescribe a las anteriores
        }
        return bc;
    }

    /**
     * suff[i] = longitud del sufijo más largo de P que también termina en la posición i.
     * Por ejemplo, en "GCAGAGAG", suff[5] = 4, porque P[2..5] = "AGAG" es también el sufijo de
     * longitud 4 del patrón.
     * Se calcula de derecha a izquierda reutilizando lo ya comparado ([g, f] es la última zona que
     * coincidió con un sufijo), lo que da un costo O(m).
     */
    public static int[] suffixes(char[] p) {
        int m = p.length;
        int[] suff = new int[m];
        suff[m - 1] = m;
        int g = m - 1;
        int f = m - 1;
        for (int i = m - 2; i >= 0; i--) {
            if (i > g && suff[i + m - 1 - f] < i - g) {
                // i está dentro de una zona ya comparada: se copia el valor de la posición equivalente.
                suff[i] = suff[i + m - 1 - f];
            } else {
                // Hay que comparar caracteres nuevos hacia la izquierda.
                if (i < g) {
                    g = i;
                }
                f = i;
                while (g >= 0 && p[g] == p[g + m - 1 - f]) {
                    g--;
                }
                suff[i] = f - g;
            }
        }
        return suff;
    }

    /** Regla del sufijo bueno: bmGs[i] según la definición formal del apartado 7.3.1. */
    public static int[] goodSuffixTable(char[] p, int[] suff) {
        int m = p.length;
        int[] gs = new int[m];
        // Paso 1: si no se encuentra nada mejor, el patrón salta completo.
        for (int i = 0; i < m; i++) {
            gs[i] = m;
        }
        // Paso 2: un prefijo de P coincide con el final del sufijo reconocido.
        int j = 0;
        for (int i = m - 1; i >= 0; i--) {
            if (suff[i] == i + 1) {
                for (; j < m - 1 - i; j++) {
                    if (gs[j] == m) {
                        gs[j] = m - 1 - i;
                    }
                }
            }
        }
        // Paso 3: el sufijo reconocido vuelve a aparecer completo dentro del patrón.
        for (int i = 0; i <= m - 2; i++) {
            gs[m - 1 - suff[i]] = m - 1 - i;
        }
        return gs;
    }

    @Override
    public Object preprocess(char[] pattern) {
        int[] suff = suffixes(pattern);
        return new Tables(badCharacterTable(pattern), goodSuffixTable(pattern, suff), suff, pattern.length);
    }

    @Override
    public Result search(char[] t, char[] p, Object preprocessed) {
        Tables tb = (Tables) preprocessed;
        List<Integer> positions = new ArrayList<>();
        int n = t.length;
        int m = p.length;
        long comparisons = 0;
        long alignments = 0;
        if (m == 0 || m > n) {
            return new Result(positions, 0, 0, 0);
        }
        int s = 0; // alineamiento actual del patrón sobre el texto
        while (s <= n - m) {
            alignments++;
            // Paso 1: comparar de derecha a izquierda.
            int i = m - 1;
            while (i >= 0) {
                comparisons++;
                if (p[i] != t[s + i]) {
                    break;
                }
                i--;
            }
            if (i < 0) {
                // Paso 2: ocurrencia completa; se desplaza según bmGs[0].
                positions.add(s);
                s += tb.goodSuffix()[0];
            } else {
                // Paso 3: discrepancia en i; se aplica la regla que permita el salto mayor.
                int bad = tb.badCharShift(t[s + i]) - m + 1 + i;
                int good = tb.goodSuffix()[i];
                s += Math.max(good, bad);
            }
        }
        return new Result(positions, comparisons, alignments, 0);
    }
}
