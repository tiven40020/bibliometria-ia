package co.edu.uniquindio.bibliometria.text;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Preprocesamiento del texto de los abstracts. Todas las operaciones se implementan
 * recorriendo los caracteres, sin expresiones regulares ni utilidades de alto nivel.
 *
 * <ol>
 *   <li>Normalización: minúsculas y sustitución de todo carácter que no sea letra o dígito por espacio.</li>
 *   <li>Tokenización por espacios.</li>
 *   <li>Eliminación de palabras vacías (stopwords) del inglés y de tokens de una sola letra.</li>
 *   <li>Lematización ligera con el "S-stemmer" de Harman (1991), que solo elimina plurales:
 *       "studies" → "study", "reviews" → "review".</li>
 * </ol>
 */
public final class TextPreprocessor {

    private TextPreprocessor() {
    }

    /** Texto en minúsculas con signos de puntuación reemplazados por espacios (para Levenshtein por caracteres). */
    public static String normalize(String text) {
        StringBuilder sb = new StringBuilder(text.length());
        boolean lastSpace = true;
        for (int i = 0; i < text.length(); i++) {
            char c = Character.toLowerCase(text.charAt(i));
            if (Character.isLetterOrDigit(c)) {
                sb.append(c);
                lastSpace = false;
            } else if (!lastSpace) {
                sb.append(' ');
                lastSpace = true;
            }
        }
        int len = sb.length();
        if (len > 0 && sb.charAt(len - 1) == ' ') {
            sb.setLength(len - 1);
        }
        return sb.toString();
    }

    /** Tokens en minúsculas, sin puntuación, conservando todas las palabras. */
    public static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String norm = normalize(text);
        for (int i = 0; i < norm.length(); i++) {
            char c = norm.charAt(i);
            if (c == ' ') {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(c);
            }
        }
        if (current.length() > 0) {
            tokens.add(current.toString());
        }
        return tokens;
    }

    /** Tokens sin stopwords, sin números aislados y sin tokens de una letra (antes de lematizar). */
    public static List<String> contentTokens(String text) {
        List<String> result = new ArrayList<>();
        for (String t : tokenize(text)) {
            if (t.length() > 1 && !Stopwords.contains(t) && !isNumber(t)) {
                result.add(t);
            }
        }
        return result;
    }

    /** Secuencia final de términos: tokens de contenido lematizados. */
    public static List<String> terms(String text) {
        List<String> result = new ArrayList<>();
        for (String t : contentTokens(text)) {
            result.add(sStem(t));
        }
        return result;
    }

    public static Set<String> termSet(String text) {
        return new HashSet<>(terms(text));
    }

    /**
     * S-stemmer de Harman (1991). Se aplica solo la primera regla que corresponda:
     * "-ies" → "-y" (salvo "-eies" y "-aies"); "-es" → "-e" (salvo "-aes", "-ees", "-oes");
     * "-s" → "" (salvo "-us" y "-ss").
     */
    public static String sStem(String w) {
        int n = w.length();
        if (n > 3 && w.endsWith("ies") && !w.endsWith("eies") && !w.endsWith("aies")) {
            return w.substring(0, n - 3) + "y";
        }
        if (n > 3 && w.endsWith("es") && !w.endsWith("aes") && !w.endsWith("ees") && !w.endsWith("oes")) {
            return w.substring(0, n - 1);
        }
        if (n > 2 && w.endsWith("s") && !w.endsWith("us") && !w.endsWith("ss")) {
            return w.substring(0, n - 1);
        }
        return w;
    }

    private static boolean isNumber(String t) {
        for (int i = 0; i < t.length(); i++) {
            if (!Character.isDigit(t.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
