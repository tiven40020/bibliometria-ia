package co.edu.uniquindio.bibliometria.traza;

import co.edu.uniquindio.bibliometria.matching.RabinKarp;
import co.edu.uniquindio.bibliometria.matching.RabinKarp.Hashes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static co.edu.uniquindio.bibliometria.matching.RabinKarp.BASE;

/** Traza de Rabin-Karp para el caso de estudio: hash del patrón y cálculo de cada ventana. */
public final class TrazaRabinKarp {

    private TrazaRabinKarp() {
    }

    public static Map<String, Object> generar(String text, String pattern, long q) {
        char[] t = text.toCharArray();
        char[] p = pattern.toCharArray();
        int n = t.length;
        int m = p.length;
        Hashes hs = (Hashes) new RabinKarp(q).preprocess(p);

        List<Map<String, Object>> patternSteps = new ArrayList<>();
        long acc = 0;
        for (int i = 0; i < m; i++) {
            long prev = acc;
            acc = (BASE * acc + p[i]) % q;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("i", i);
            row.put("caracter", String.valueOf(p[i]));
            row.put("codigo", (int) p[i]);
            row.put("calculo", "(" + BASE + "·" + prev + " + " + (int) p[i] + ") mod " + q);
            row.put("hash", acc);
            patternSteps.add(row);
        }

        List<Map<String, Object>> windows = new ArrayList<>();
        List<Integer> found = new ArrayList<>();
        long comparisons = 0;
        long spurious = 0;
        long th = 0;
        for (int i = 0; i < Math.min(m, n); i++) {
            th = (BASE * th + t[i]) % q;
        }
        for (int s = 0; s <= n - m; s++) {
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("s", s);
            w.put("ventana", text.substring(s, s + m));
            w.put("hash", th);
            String result = "hash distinto";
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
                    found.add(s);
                    result = "ocurrencia (hash igual y caracteres verificados)";
                } else {
                    spurious++;
                    result = "colisión: hash igual pero la ventana es distinta";
                }
            }
            w.put("resultado", result);
            if (s < n - m) {
                long next = (BASE * (th - t[s] * hs.h()) + t[s + m]) % q;
                if (next < 0) {
                    next += q;
                }
                w.put("siguiente", "(" + BASE + "·(" + th + " − " + (int) t[s] + "·" + hs.h() + ") + "
                        + (int) t[s + m] + ") mod " + q + " = " + next);
                th = next;
            }
            windows.add(w);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("algoritmo", "Rabin-Karp");
        out.put("base", BASE);
        out.put("primo", q);
        out.put("h", hs.h());
        out.put("hashPatron", hs.patternHash());
        out.put("calculoHashPatron", patternSteps);
        out.put("ventanas", windows);
        out.put("ocurrencias", found);
        out.put("colisiones", spurious);
        out.put("comparaciones", comparisons);
        out.put("complejidad", Map.of("preprocesamiento", "Θ(m)", "busqueda",
                "O(n + m) esperado; O(n·m) peor caso", "total", "O(n·m) peor caso"));
        return out;
    }
}
