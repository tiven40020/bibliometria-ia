package co.edu.uniquindio.bibliometria.matching;

import co.edu.uniquindio.bibliometria.corpus.Article;
import co.edu.uniquindio.bibliometria.corpus.Corpus;
import co.edu.uniquindio.bibliometria.traza.TrazaBoyerMoore;
import co.edu.uniquindio.bibliometria.traza.TrazaKmp;
import co.edu.uniquindio.bibliometria.traza.TrazaRabinKarp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Requerimiento 3: búsqueda exacta de un patrón en los 20 abstracts con KMP, Boyer-Moore y Rabin-Karp. */
public class MatchingService {

    private final Corpus corpus;

    public MatchingService(Corpus corpus) {
        this.corpus = corpus;
    }

    public static List<StringMatcher> matchers() {
        return List.of(new KnuthMorrisPratt(), new BoyerMoore(), new RabinKarp());
    }

    /** Minúsculas carácter por carácter (para la búsqueda que no distingue mayúsculas). */
    static char[] prepare(String s, boolean caseSensitive) {
        char[] c = s.toCharArray();
        if (!caseSensitive) {
            for (int i = 0; i < c.length; i++) {
                c[i] = Character.toLowerCase(c[i]);
            }
        }
        return c;
    }

    public Map<String, Object> search(String pattern, boolean caseSensitive) {
        if (pattern == null || pattern.isEmpty()) {
            throw new IllegalArgumentException("Ingrese un patrón de búsqueda");
        }
        char[] p = prepare(pattern, caseSensitive);
        List<Article> arts = corpus.all();
        List<char[]> texts = new ArrayList<>();
        for (Article a : arts) {
            texts.add(prepare(a.abstractText(), caseSensitive));
        }

        List<Map<String, Object>> algs = new ArrayList<>();
        List<List<Integer>> reference = null;
        boolean allAgree = true;
        for (StringMatcher sm : matchers()) {
            long t0 = System.nanoTime();
            Object pre = sm.preprocess(p);
            long preNs = System.nanoTime() - t0;

            long comparisons = 0;
            long alignments = 0;
            long extra = 0;
            int total = 0;
            List<List<Integer>> perArticle = new ArrayList<>();
            long t1 = System.nanoTime();
            for (char[] text : texts) {
                StringMatcher.Result r = sm.search(text, p, pre);
                perArticle.add(r.positions());
                comparisons += r.comparisons();
                alignments += r.alignments();
                extra += r.extra();
                total += r.positions().size();
            }
            long searchNs = System.nanoTime() - t1;

            if (reference == null) {
                reference = perArticle;
            } else if (!reference.equals(perArticle)) {
                allAgree = false;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", sm.key());
            m.put("nombre", sm.name());
            m.put("ocurrencias", total);
            m.put("comparaciones", comparisons);
            m.put("alineamientos", alignments);
            if (sm instanceof RabinKarp) {
                m.put("colisiones", extra);
            }
            m.put("preprocesamientoNs", preNs);
            m.put("busquedaNs", searchNs);
            algs.add(m);
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (int i = 0; i < arts.size(); i++) {
            List<Integer> pos = reference.get(i);
            if (pos.isEmpty()) {
                continue;
            }
            Article a = arts.get(i);
            List<String> contexts = new ArrayList<>();
            for (int s : pos) {
                contexts.add(context(a.abstractText(), s, pattern.length()));
            }
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("articulo", a.toMap(false));
            r.put("posiciones", pos);
            r.put("contextos", contexts);
            results.add(r);
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("patron", pattern);
        out.put("distingueMayusculas", caseSensitive);
        out.put("m", p.length);
        int n = 0;
        for (char[] t : texts) {
            n += t.length;
        }
        out.put("nTotal", n);
        out.put("algoritmos", algs);
        out.put("coinciden", allAgree);
        out.put("articulosConOcurrencias", results.size());
        out.put("resultados", results);
        return out;
    }

    /** Texto alrededor de la ocurrencia, con la coincidencia marcada entre [[ ]]. */
    static String context(String text, int s, int m) {
        int from = Math.max(0, s - 60);
        int to = Math.min(text.length(), s + m + 60);
        return (from > 0 ? "…" : "") + text.substring(from, s) + "[[" + text.substring(s, s + m) + "]]"
                + text.substring(s + m, to) + (to < text.length() ? "…" : "");
    }

    /**
     * Experimento de rendimiento: repite cada fase varias veces y toma la mediana para reducir el
     * ruido del recolector de basura y del compilador JIT. Además mide el crecimiento del tiempo de
     * búsqueda al duplicar el tamaño del texto (corpus concatenado 1, 2, 4, 8 y 16 veces).
     */
    public Map<String, Object> benchmark(String pattern, boolean caseSensitive, int repetitions) {
        char[] p = prepare(pattern, caseSensitive);
        StringBuilder sb = new StringBuilder();
        for (Article a : corpus.all()) {
            sb.append(a.abstractText()).append('\n');
        }
        char[] base = prepare(sb.toString(), caseSensitive);

        List<Map<String, Object>> algs = new ArrayList<>();
        int[] factors = {1, 2, 4, 8, 16};
        for (StringMatcher sm : matchers()) {
            // Calentamiento del JIT.
            for (int w = 0; w < 20; w++) {
                sm.search(base, p, sm.preprocess(p));
            }
            long[] preTimes = new long[repetitions];
            for (int r = 0; r < repetitions; r++) {
                long t0 = System.nanoTime();
                sm.preprocess(p);
                preTimes[r] = System.nanoTime() - t0;
            }
            Object pre = sm.preprocess(p);

            List<Map<String, Object>> scaling = new ArrayList<>();
            for (int f : factors) {
                char[] text = repeat(base, f);
                long[] times = new long[repetitions];
                StringMatcher.Result last = null;
                for (int r = 0; r < repetitions; r++) {
                    long t0 = System.nanoTime();
                    last = sm.search(text, p, pre);
                    times[r] = System.nanoTime() - t0;
                }
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("factor", f);
                row.put("n", text.length);
                row.put("medianaNs", median(times));
                row.put("comparaciones", last.comparisons());
                row.put("comparacionesPorCaracter", (double) last.comparisons() / text.length);
                row.put("ocurrencias", last.positions().size());
                scaling.add(row);
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("key", sm.key());
            m.put("nombre", sm.name());
            m.put("preprocesamientoMedianaNs", median(preTimes));
            m.put("escalamiento", scaling);
            algs.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("patron", pattern);
        out.put("m", p.length);
        out.put("repeticiones", repetitions);
        out.put("algoritmos", algs);
        return out;
    }

    private static char[] repeat(char[] base, int times) {
        char[] r = new char[base.length * times];
        for (int t = 0; t < times; t++) {
            System.arraycopy(base, 0, r, t * base.length, base.length);
        }
        return r;
    }

    static long median(long[] values) {
        long[] c = values.clone();
        Arrays.sort(c);
        return c[c.length / 2];
    }

    /**
     * Traza paso a paso de los tres algoritmos sobre un fragmento del abstract. Si no se indica un
     * fragmento, se usa la oración del abstract que contiene la primera ocurrencia del patrón.
     */
    public Map<String, Object> trace(String pattern, String articleId, String fragment, boolean caseSensitive,
                                     long prime) {
        Article a = corpus.get(articleId);
        String text = fragment != null && !fragment.isBlank() ? fragment : sentenceWith(a.abstractText(), pattern,
                caseSensitive);
        String t = new String(prepare(text, caseSensitive));
        String p = new String(prepare(pattern, caseSensitive));

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("articulo", a.toMap(false));
        out.put("texto", text);
        out.put("textoProcesado", t);
        out.put("patron", p);
        out.put("n", t.length());
        out.put("m", p.length());
        out.put("kmp", TrazaKmp.generar(t, p));
        out.put("boyerMoore", TrazaBoyerMoore.generar(t, p));
        out.put("rabinKarp", TrazaRabinKarp.generar(t, p, prime));
        return out;
    }

    static String sentenceWith(String text, String pattern, boolean caseSensitive) {
        char[] t = prepare(text, caseSensitive);
        char[] p = prepare(pattern, caseSensitive);
        KnuthMorrisPratt kmp = new KnuthMorrisPratt();
        List<Integer> pos = kmp.search(t, p, kmp.preprocess(p)).positions();
        if (pos.isEmpty()) {
            return text.substring(0, Math.min(250, text.length()));
        }
        int s = pos.get(0);
        int start = s;
        while (start > 0 && !(text.charAt(start - 1) == '.' && start < text.length() && text.charAt(start) == ' ')) {
            start--;
        }
        int end = s + pattern.length();
        while (end < text.length() && text.charAt(end - 1) != '.') {
            end++;
        }
        return text.substring(start, end).trim();
    }
}
