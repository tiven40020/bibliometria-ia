package co.edu.uniquindio.bibliometria.similarity;

import co.edu.uniquindio.bibliometria.text.TextPreprocessor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Convierte un texto en una secuencia de símbolos enteros para los algoritmos de programación
 * dinámica. Con nivel "caracter" cada símbolo es un carácter del texto normalizado; con nivel
 * "palabra" cada símbolo es una palabra (se usa un diccionario compartido para que la misma
 * palabra tenga el mismo código en ambos textos).
 */
public final class Sequences {

    public enum Level { CARACTER, PALABRA }

    private Sequences() {
    }

    public static Level parseLevel(String s, Level def) {
        if (s == null || s.isBlank()) {
            return def;
        }
        return s.toLowerCase().startsWith("p") ? Level.PALABRA : Level.CARACTER;
    }

    /** Par de secuencias codificadas y los símbolos legibles correspondientes. */
    public record Pair(int[] a, int[] b, List<String> symbolsA, List<String> symbolsB) {
    }

    public static Pair encode(String textA, String textB, Level level, int limit) {
        List<String> sa = symbols(textA, level, limit);
        List<String> sb = symbols(textB, level, limit);
        Map<String, Integer> dict = new HashMap<>();
        return new Pair(codes(sa, dict), codes(sb, dict), sa, sb);
    }

    private static List<String> symbols(String text, Level level, int limit) {
        List<String> out = new ArrayList<>();
        if (level == Level.CARACTER) {
            String norm = TextPreprocessor.normalize(text);
            for (int i = 0; i < norm.length() && out.size() < limit; i++) {
                out.add(String.valueOf(norm.charAt(i)));
            }
        } else {
            for (String t : TextPreprocessor.tokenize(text)) {
                if (out.size() >= limit) {
                    break;
                }
                out.add(t);
            }
        }
        return out;
    }

    private static int[] codes(List<String> symbols, Map<String, Integer> dict) {
        int[] r = new int[symbols.size()];
        for (int i = 0; i < r.length; i++) {
            Integer code = dict.get(symbols.get(i));
            if (code == null) {
                code = dict.size();
                dict.put(symbols.get(i), code);
            }
            r[i] = code;
        }
        return r;
    }
}
