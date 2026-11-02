package co.edu.uniquindio.bibliometria.matching;

import java.util.ArrayList;
import java.util.List;

/**
 * Algoritmo Rabin-Karp (1987) con hash rodante (rolling hash) en aritmética modular.
 *
 * <p>Cada cadena de longitud m se interpreta como un número en base d = 256 y se reduce módulo un
 * primo q:</p>
 * <pre>
 * hash(P) = (P[0]·d^(m−1) + P[1]·d^(m−2) + … + P[m−1]) mod q          (regla de Horner)
 * t(s+1)  = (d·(t(s) − T[s]·h) + T[s+m]) mod q,   con h = d^(m−1) mod q
 * </pre>
 * <p>Si hash(P) = t(s) se verifican los caracteres, porque dos cadenas distintas pueden tener el
 * mismo hash (colisión o "falso positivo"). Con un primo grande las colisiones son muy raras.</p>
 * <p>Preprocesamiento Θ(m). Búsqueda O(n + m) en el caso esperado y O(n·m) en el peor caso
 * (cuando casi todas las ventanas colisionan).</p>
 */
public class RabinKarp implements StringMatcher {

    public static final int BASE = 256;
    public static final long DEFAULT_PRIME = 1_000_000_007L;

    private final long q;

    public RabinKarp() {
        this(DEFAULT_PRIME);
    }

    public RabinKarp(long prime) {
        this.q = prime;
    }

    /** Primo q del módulo; la traza lo necesita para mostrar los cálculos. */
    public long primo() {
        return q;
    }

    @Override
    public String key() {
        return "rabin-karp";
    }

    @Override
    public String name() {
        return "Rabin-Karp";
    }

    /** Hash del patrón y factor h = d^(m−1) mod q. */
    public record Hashes(long patternHash, long h) {
    }

    @Override
    public Object preprocess(char[] p) {
        long h = 1;
        for (int i = 0; i < p.length - 1; i++) {
            h = (h * BASE) % q;
        }
        long ph = 0;
        for (char c : p) {
            ph = (BASE * ph + c) % q;
        }
        return new Hashes(ph, h);
    }

    @Override
    public Result search(char[] t, char[] p, Object preprocessed) {
        Hashes hs = (Hashes) preprocessed;
        List<Integer> positions = new ArrayList<>();
        int n = t.length;
        int m = p.length;
        long comparisons = 0;
        long spurious = 0;
        if (m == 0 || m > n) {
            return new Result(positions, 0, 0, 0);
        }
        // Paso 1: hash de la primera ventana T[0..m−1] con la regla de Horner.
        long th = 0;
        for (int i = 0; i < m; i++) {
            th = (BASE * th + t[i]) % q;
        }
        for (int s = 0; s <= n - m; s++) {
            // Paso 2: si los hashes coinciden, se verifican los caracteres uno por uno.
            if (th == hs.patternHash()) {
                int j = 0;
                while (j < m) {
                    comparisons++;
                    if (p[j] != t[s + j]) {
                        break;
                    }
                    j++;
                }
                if (j == m) {
                    positions.add(s);
                } else {
                    spurious++;
                }
            }
            // Paso 3: hash rodante; se quita T[s] y se agrega T[s+m] en tiempo constante.
            if (s < n - m) {
                th = (BASE * (th - t[s] * hs.h()) + t[s + m]) % q;
                if (th < 0) {
                    th += q;
                }
            }
        }
        return new Result(positions, comparisons, n - m + 1, spurious);
    }
}
