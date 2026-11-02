package co.edu.uniquindio.bibliometria.traza;

import co.edu.uniquindio.bibliometria.matching.KnuthMorrisPratt;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Traza de KMP para el caso de estudio: tabla π e intentos de alineamiento sobre el texto. */
public final class TrazaKmp {

    private TrazaKmp() {
    }

    public static Map<String, Object> generar(String text, String pattern) {
        char[] t = text.toCharArray();
        char[] p = pattern.toCharArray();
        int[] pi = KnuthMorrisPratt.prefixFunction(p);

        List<Map<String, Object>> piSteps = new ArrayList<>();
        for (int q = 0; q < p.length; q++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("q", q);
            row.put("prefijo", pattern.substring(0, q + 1));
            row.put("pi", pi[q]);
            row.put("bordeMasLargo", pi[q] == 0 ? "" : pattern.substring(0, pi[q]));
            piSteps.add(row);
        }

        // Cada "intento" registra el alineamiento s = i − q del patrón sobre el texto en el momento
        // en que se produce una discrepancia o se completa una ocurrencia.
        List<Map<String, Object>> attempts = new ArrayList<>();
        List<Integer> found = new ArrayList<>();
        int q = 0;
        long comparisons = 0;
        for (int i = 0; i < t.length; i++) {
            while (q > 0 && p[q] != t[i]) {
                comparisons++;
                int newQ = pi[q - 1];
                attempts.add(attempt(i - q, q, i, t[i], p[q], "discrepancia",
                        "q = π[" + (q - 1) + "] = " + newQ + " (se conservan " + newQ + " caracteres ya reconocidos)",
                        q - newQ));
                q = newQ;
            }
            comparisons++;
            if (p[q] == t[i]) {
                q++;
            } else {
                attempts.add(attempt(i, 0, i, t[i], p[0], "discrepancia", "q = 0: el patrón avanza una posición", 1));
            }
            if (q == p.length) {
                found.add(i - p.length + 1);
                int newQ = pi[q - 1];
                attempts.add(attempt(i - q + 1, q, i, t[i], p[q - 1], "ocurrencia",
                        "q = π[" + (q - 1) + "] = " + newQ, q - newQ));
                q = newQ;
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("algoritmo", "Knuth-Morris-Pratt");
        out.put("funcionPrefijos", pi);
        out.put("tablaPrefijos", piSteps);
        out.put("intentos", attempts);
        out.put("ocurrencias", found);
        out.put("comparaciones", comparisons);
        out.put("caracteresLeidos", t.length);
        out.put("complejidad", Map.of("preprocesamiento", "Θ(m)", "busqueda", "Θ(n)", "total", "O(n + m)"));
        return out;
    }

    private static Map<String, Object> attempt(int s, int matched, int i, char tc, char pc, String result,
                                               String action, int shift) {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("s", s);
        a.put("coincidentes", matched);
        a.put("posicionTexto", i);
        a.put("caracterTexto", String.valueOf(tc));
        a.put("caracterPatron", String.valueOf(pc));
        a.put("resultado", result);
        a.put("accion", action);
        a.put("desplazamiento", shift);
        return a;
    }
}
