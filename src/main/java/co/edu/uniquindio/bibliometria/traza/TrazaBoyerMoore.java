package co.edu.uniquindio.bibliometria.traza;

import co.edu.uniquindio.bibliometria.matching.BoyerMoore;
import co.edu.uniquindio.bibliometria.matching.BoyerMoore.Tables;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Traza de Boyer-Moore para el caso de estudio: las dos tablas y cada alineamiento con su salto. */
public final class TrazaBoyerMoore {

    private TrazaBoyerMoore() {
    }

    public static Map<String, Object> generar(String text, String pattern) {
        char[] t = text.toCharArray();
        char[] p = pattern.toCharArray();
        Tables tb = (Tables) new BoyerMoore().preprocess(p);
        int n = t.length;
        int m = p.length;

        List<Map<String, Object>> bcRows = new ArrayList<>();
        for (Map.Entry<Character, Integer> e : new TreeMap<>(tb.badChar()).entrySet()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("caracter", String.valueOf(e.getKey()));
            row.put("bmBc", e.getValue());
            bcRows.add(row);
        }
        List<Map<String, Object>> gsRows = new ArrayList<>();
        for (int i = 0; i < m; i++) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("i", i);
            row.put("caracter", String.valueOf(p[i]));
            row.put("suff", tb.suffixes()[i]);
            row.put("sufijoReconocido", pattern.substring(i + 1));
            row.put("bmGs", tb.goodSuffix()[i]);
            gsRows.add(row);
        }

        List<Map<String, Object>> attempts = new ArrayList<>();
        List<Integer> found = new ArrayList<>();
        long comparisons = 0;
        int s = 0;
        while (s <= n - m) {
            int i = m - 1;
            int cmp = 0;
            while (i >= 0) {
                cmp++;
                if (p[i] != t[s + i]) {
                    break;
                }
                i--;
            }
            comparisons += cmp;
            Map<String, Object> a = new LinkedHashMap<>();
            a.put("s", s);
            a.put("ventana", text.substring(s, s + m));
            a.put("comparaciones", cmp);
            int shift;
            if (i < 0) {
                found.add(s);
                shift = tb.goodSuffix()[0];
                a.put("resultado", "ocurrencia");
                a.put("motivo", "ocurrencia completa: desplazamiento bmGs[0] = " + shift);
            } else {
                char c = t[s + i];
                int bad = tb.badCharShift(c) - m + 1 + i;
                int good = tb.goodSuffix()[i];
                shift = Math.max(good, bad);
                a.put("resultado", "discrepancia");
                a.put("posicionPatron", i);
                a.put("caracterMalo", String.valueOf(c));
                a.put("desplazamientoCaracterMalo", bad);
                a.put("desplazamientoSufijoBueno", good);
                a.put("motivo", (bad >= good ? "carácter malo" : "sufijo bueno")
                        + ": max(" + bad + ", " + good + ") = " + shift);
            }
            a.put("desplazamiento", shift);
            attempts.add(a);
            s += shift;
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("algoritmo", "Boyer-Moore");
        out.put("tablaCaracterMalo", bcRows);
        out.put("caracterAusente", "cualquier carácter que no esté en P[0..m-2]: bmBc = m = " + m);
        out.put("tablaSufijoBueno", gsRows);
        out.put("intentos", attempts);
        out.put("ocurrencias", found);
        out.put("comparaciones", comparisons);
        out.put("complejidad", Map.of("preprocesamiento", "O(m + σ)", "busqueda",
                "O(n·m) peor caso; ~O(n/m) en texto natural", "total", "O(n·m + σ)"));
        return out;
    }
}
