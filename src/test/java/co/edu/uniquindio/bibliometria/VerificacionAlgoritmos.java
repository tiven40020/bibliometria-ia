package co.edu.uniquindio.bibliometria;

import co.edu.uniquindio.bibliometria.traza.TrazaBoyerMoore;
import co.edu.uniquindio.bibliometria.traza.TrazaKmp;
import co.edu.uniquindio.bibliometria.traza.TrazaLevenshtein;
import co.edu.uniquindio.bibliometria.traza.TrazaRabinKarp;
import co.edu.uniquindio.bibliometria.clustering.ClusterMetrics;
import co.edu.uniquindio.bibliometria.clustering.HierarchicalClustering;
import co.edu.uniquindio.bibliometria.clustering.Linkage;
import co.edu.uniquindio.bibliometria.matching.BoyerMoore;
import co.edu.uniquindio.bibliometria.matching.KnuthMorrisPratt;
import co.edu.uniquindio.bibliometria.matching.RabinKarp;
import co.edu.uniquindio.bibliometria.matching.StringMatcher;
import co.edu.uniquindio.bibliometria.similarity.Jaccard;
import co.edu.uniquindio.bibliometria.similarity.Levenshtein;
import co.edu.uniquindio.bibliometria.similarity.NeedlemanWunsch;
import co.edu.uniquindio.bibliometria.text.TextPreprocessor;
import co.edu.uniquindio.bibliometria.web.Json;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Verificación de los algoritmos con casos conocidos y pruebas aleatorias contra una implementación
 * ingenua. No depende de ningún framework de pruebas:
 *
 * <pre>
 * mvn -q compile test-compile
 * java -cp target/classes:target/test-classes co.edu.uniquindio.bibliometria.VerificacionAlgoritmos
 * </pre>
 */
public final class VerificacionAlgoritmos {

    private static int checks = 0;
    private static int failures = 0;

    private VerificacionAlgoritmos() {
    }

    public static void main(String[] args) {
        levenshtein();
        needlemanWunsch();
        jaccardYPreprocesamiento();
        busquedaContraIngenua();
        rabinKarpColisiones();
        tablasConocidas();
        clustering();
        json();
        System.out.printf("%d verificaciones, %d fallos%n", checks, failures);
        if (failures > 0) {
            System.exit(1);
        }
    }

    private static void check(boolean ok, String what) {
        checks++;
        if (!ok) {
            failures++;
            System.out.println("FALLA: " + what);
        }
    }

    private static int[] codes(String s) {
        int[] r = new int[s.length()];
        for (int i = 0; i < r.length; i++) {
            r[i] = s.charAt(i);
        }
        return r;
    }

    private static List<String> chars(String s) {
        List<String> l = new ArrayList<>();
        for (char c : s.toCharArray()) {
            l.add(String.valueOf(c));
        }
        return l;
    }

    static void levenshtein() {
        check(Levenshtein.distance(codes("kitten"), codes("sitting")) == 3, "kitten/sitting = 3");
        check(Levenshtein.distance(codes("flaw"), codes("lawn")) == 2, "flaw/lawn = 2");
        check(Levenshtein.distance(codes(""), codes("abc")) == 3, "vacío/abc = 3");
        int[][] m = Levenshtein.matrix(codes("kitten"), codes("sitting"));
        check(m[6][7] == 3, "matriz completa coincide con la versión de dos filas");
        Random r = new Random(7);
        for (int t = 0; t < 300; t++) {
            String a = random(r, r.nextInt(30), "abc");
            String b = random(r, r.nextInt(30), "abc");
            int[][] mm = Levenshtein.matrix(codes(a), codes(b));
            check(mm[a.length()][b.length()] == Levenshtein.distance(codes(a), codes(b)), "dos filas = matriz: " + a + "/" + b);
            check(TrazaLevenshtein.editScript(mm, chars(a), chars(b)).stream().filter(o -> !o.startsWith("coincide")).count()
                    == mm[a.length()][b.length()], "el guion de edición tiene d operaciones: " + a + "/" + b);
        }
    }

    static void needlemanWunsch() {
        // Ejemplo clásico: GATTACA vs GCATGCU con +1/−1/−1 tiene puntaje óptimo 0.
        NeedlemanWunsch.Alignment al = NeedlemanWunsch.align(codes("GATTACA"), codes("GCATGCU"),
                chars("GATTACA"), chars("GCATGCU"));
        check(al.score() == 0, "GATTACA/GCATGCU puntaje 0");
        int recomputed = 0;
        for (int i = 0; i < al.length(); i++) {
            String x = al.alignedA().get(i);
            String y = al.alignedB().get(i);
            recomputed += x.equals("-") || y.equals("-") ? NeedlemanWunsch.GAP
                    : x.equals(y) ? NeedlemanWunsch.MATCH : NeedlemanWunsch.MISMATCH;
        }
        check(recomputed == al.score(), "el alineamiento recuperado tiene el puntaje de F[n][m]");
        NeedlemanWunsch.Alignment same = NeedlemanWunsch.align(codes("abc"), codes("abc"), chars("abc"), chars("abc"));
        check(same.identity() == 1.0, "textos iguales: identidad 1");
    }

    static void jaccardYPreprocesamiento() {
        check(Jaccard.jaccard(Set.of("a", "b", "c"), Set.of("b", "c", "d")) == 0.5, "Jaccard {a,b,c},{b,c,d} = 0.5");
        check(TextPreprocessor.sStem("studies").equals("study"), "studies → study");
        check(TextPreprocessor.sStem("reviews").equals("review"), "reviews → review");
        check(TextPreprocessor.sStem("analysis").equals("analysi"), "analysis → analysi (regla -s de Harman, igual en todos los documentos)");
        check(TextPreprocessor.sStem("focus").equals("focus"), "focus no cambia (-us)");
        check(TextPreprocessor.terms("The Studies of AI, in 2023!").equals(List.of("study", "ai")),
                "preprocesamiento completo");
    }

    static void busquedaContraIngenua() {
        Random r = new Random(42);
        List<StringMatcher> matchers = List.of(new KnuthMorrisPratt(), new BoyerMoore(), new RabinKarp(), new RabinKarp(7));
        String[] alphabets = {"ab", "abc", "acgt", "ab "};
        for (int t = 0; t < 4000; t++) {
            String alpha = alphabets[t % alphabets.length];
            String text = random(r, r.nextInt(200), alpha);
            String pattern = random(r, 1 + r.nextInt(8), alpha);
            List<Integer> expected = naive(text, pattern);
            for (StringMatcher sm : matchers) {
                char[] p = pattern.toCharArray();
                List<Integer> got = sm.search(text.toCharArray(), p, sm.preprocess(p)).positions();
                check(got.equals(expected), sm.name() + " '" + pattern + "' en '" + text + "'");
            }
            // Las trazas deben encontrar lo mismo que la búsqueda normal.
            check(TrazaKmp.generar(text, pattern).get("ocurrencias").equals(expected), "traza KMP");
            check(TrazaBoyerMoore.generar(text, pattern).get("ocurrencias").equals(expected), "traza BM");
            check(TrazaRabinKarp.generar(text, pattern, 13).get("ocurrencias").equals(expected), "traza RK");
        }
    }

    static void rabinKarpColisiones() {
        // Con un primo pequeño aparecen colisiones, pero nunca falsos positivos en el resultado.
        String text = "generative artificial intelligence transforms assessment in higher education";
        RabinKarp rk = new RabinKarp(11);
        char[] p = "in".toCharArray();
        StringMatcher.Result res = rk.search(text.toCharArray(), p, rk.preprocess(p));
        check(res.positions().equals(naive(text, "in")), "RK q=11 sin falsos positivos");
        check(res.extra() > 0, "RK q=11 produce colisiones (" + res.extra() + ")");
    }

    static void tablasConocidas() {
        check(java.util.Arrays.equals(KnuthMorrisPratt.prefixFunction("ababaca".toCharArray()),
                new int[]{0, 0, 1, 2, 3, 0, 1}), "π(ababaca) = 0 0 1 2 3 0 1 (Cormen et al.)");
        check(java.util.Arrays.equals(KnuthMorrisPratt.prefixFunction("aabaaab".toCharArray()),
                new int[]{0, 1, 0, 1, 2, 2, 3}), "π(aabaaab)");
        // Ejemplo de Charras y Lecroq: x = GCAGAGAG.
        char[] x = "GCAGAGAG".toCharArray();
        check(java.util.Arrays.equals(BoyerMoore.goodSuffixTable(x, BoyerMoore.suffixes(x)),
                new int[]{7, 7, 7, 2, 7, 4, 7, 1}), "bmGs(GCAGAGAG) = 7 7 7 2 7 4 7 1");
        Map<Character, Integer> bc = BoyerMoore.badCharacterTable(x);
        check(bc.get('A') == 1 && bc.get('C') == 6 && bc.get('G') == 2 && !bc.containsKey('T'),
                "bmBc(GCAGAGAG): A=1, C=6, G=2, T=8");
    }

    static void clustering() {
        // Cuatro puntos en una recta: 0, 1, 5, 6 → primero {0,1} y {5,6}, luego todo.
        double[] pts = {0, 1, 5, 6};
        double[][] d = new double[4][4];
        for (int i = 0; i < 4; i++) {
            for (int j = 0; j < 4; j++) {
                d[i][j] = Math.abs(pts[i] - pts[j]);
            }
        }
        var single = HierarchicalClustering.run(d, Linkage.SINGLE);
        var complete = HierarchicalClustering.run(d, Linkage.COMPLETE);
        var average = HierarchicalClustering.run(d, Linkage.AVERAGE);
        check(single.get(2).distance() == 4, "single: última unión a distancia 4");
        check(complete.get(2).distance() == 6, "complete: última unión a distancia 6");
        check(average.get(2).distance() == 5, "average: última unión a distancia (4+5+5+6)/4 = 5");
        int[] labels = HierarchicalClustering.cut(single, 4, 2);
        check(labels[0] == labels[1] && labels[2] == labels[3] && labels[0] != labels[2], "corte en 2 grupos");
        // s(0) = (5.5 − 1)/5.5, s(1) = (4.5 − 1)/4.5 y simétricos: promedio = 0.79798.
        check(Math.abs(ClusterMetrics.silhouette(d, labels) - (4.5 / 5.5 + 3.5 / 4.5) / 2) < 1e-12,
                "silueta calculada a mano = 0.79798");
    }

    static void json() {
        Object parsed = Json.parse(Json.write(Map.of("a", List.of(1, 2.5, "x\"y"), "b", true)));
        check(parsed instanceof Map<?, ?> m && ((List<?>) m.get("a")).get(2).equals("x\"y"), "JSON ida y vuelta");
    }

    private static List<Integer> naive(String text, String pattern) {
        List<Integer> r = new ArrayList<>();
        for (int s = 0; s + pattern.length() <= text.length(); s++) {
            int j = 0;
            while (j < pattern.length() && text.charAt(s + j) == pattern.charAt(j)) {
                j++;
            }
            if (j == pattern.length()) {
                r.add(s);
            }
        }
        return r;
    }

    private static String random(Random r, int n, String alphabet) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            sb.append(alphabet.charAt(r.nextInt(alphabet.length())));
        }
        return sb.toString();
    }
}
