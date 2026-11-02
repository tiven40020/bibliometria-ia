package co.edu.uniquindio.bibliometria.matching;

import java.util.ArrayList;
import java.util.List;

/**
 * Algoritmo Knuth-Morris-Pratt (documento, apartado 7.3.1).
 *
 * <p>Preprocesamiento: función de prefijos π, donde π[q] es la longitud del prefijo propio más
 * largo de P[0..q] que también es sufijo de P[0..q]. Costo Θ(m).</p>
 * <p>Búsqueda: recorre el texto una sola vez sin retroceder. Costo Θ(n): como máximo 2n
 * comparaciones. Total O(n + m).</p>
 */
public class KnuthMorrisPratt implements StringMatcher {

    @Override
    public String key() {
        return "kmp";
    }

    @Override
    public String name() {
        return "Knuth-Morris-Pratt";
    }

    /** Función de prefijos π: compara el patrón contra sí mismo. */
    public static int[] prefixFunction(char[] p) {
        int m = p.length;
        int[] pi = new int[m];
        int k = 0; // longitud del borde reconocido hasta ahora
        for (int q = 1; q < m; q++) {
            // Si el siguiente carácter no extiende el borde, se prueba con el borde más corto.
            while (k > 0 && p[k] != p[q]) {
                k = pi[k - 1];
            }
            // Si coincide, el borde crece en 1.
            if (p[k] == p[q]) {
                k++;
            }
            pi[q] = k;
        }
        return pi;
    }

    @Override
    public Object preprocess(char[] pattern) {
        return prefixFunction(pattern);
    }

    @Override
    public Result search(char[] t, char[] p, Object preprocessed) {
        int[] pi = (int[]) preprocessed;
        List<Integer> positions = new ArrayList<>();
        int n = t.length;
        int m = p.length;
        long comparisons = 0;
        long shifts = 0;
        if (m == 0 || m > n) {
            return new Result(positions, 0, 0, 0);
        }
        int q = 0; // número de caracteres del patrón reconocidos
        for (int i = 0; i < n; i++) {
            // Paso 1: mientras haya discrepancia, retroceder en el patrón con q = π[q − 1].
            while (q > 0) {
                comparisons++;
                if (p[q] == t[i]) {
                    break;
                }
                q = pi[q - 1];
                shifts++;
            }
            if (q == 0) {
                comparisons++;
            }
            // Paso 2: si el carácter coincide, avanzar.
            if (p[q] == t[i]) {
                q++;
            }
            // Paso 3: si se reconoció todo el patrón, registrar la ocurrencia y continuar con π[m − 1].
            if (q == m) {
                positions.add(i - m + 1);
                q = pi[q - 1];
                shifts++;
            }
        }
        return new Result(positions, comparisons, shifts + 1, 0);
    }
}
