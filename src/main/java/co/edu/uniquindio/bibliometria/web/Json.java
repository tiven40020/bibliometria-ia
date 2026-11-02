package co.edu.uniquindio.bibliometria.web;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializador y analizador JSON mínimo. Evita dependencias externas: la aplicación solo
 * necesita convertir mapas, listas, cadenas, números y arreglos.
 */
public final class Json {

    private Json() {
    }

    // ------------------------------------------------------------------ escritura

    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        write(value, sb);
        return sb.toString();
    }

    private static void write(Object v, StringBuilder sb) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof String s) {
            string(s, sb);
        } else if (v instanceof Double d) {
            number(d, sb);
        } else if (v instanceof Float f) {
            number(f.doubleValue(), sb);
        } else if (v instanceof Number || v instanceof Boolean) {
            sb.append(v);
        } else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                string(String.valueOf(e.getKey()), sb);
                sb.append(':');
                write(e.getValue(), sb);
            }
            sb.append('}');
        } else if (v instanceof Collection<?> c) {
            sb.append('[');
            boolean first = true;
            for (Object o : c) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                write(o, sb);
            }
            sb.append(']');
        } else if (v instanceof int[] a) {
            sb.append('[');
            for (int i = 0; i < a.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(a[i]);
            }
            sb.append(']');
        } else if (v instanceof long[] a) {
            sb.append('[');
            for (int i = 0; i < a.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(a[i]);
            }
            sb.append(']');
        } else if (v instanceof double[] a) {
            sb.append('[');
            for (int i = 0; i < a.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                number(a[i], sb);
            }
            sb.append(']');
        } else if (v instanceof Object[] a) {
            sb.append('[');
            for (int i = 0; i < a.length; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                write(a[i], sb);
            }
            sb.append(']');
        } else {
            string(v.toString(), sb);
        }
    }

    private static void number(double d, StringBuilder sb) {
        if (Double.isNaN(d) || Double.isInfinite(d)) {
            sb.append("null");
        } else if (d == Math.rint(d) && Math.abs(d) < 1e15) {
            sb.append((long) d);
        } else {
            sb.append(d);
        }
    }

    private static void string(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    // ------------------------------------------------------------------ lectura

    /** Analiza un documento JSON y devuelve Map, List, String, Double, Boolean o null. */
    public static Object parse(String text) {
        Parser p = new Parser(text);
        p.skipWs();
        Object v = p.value();
        p.skipWs();
        if (p.pos != text.length()) {
            throw new IllegalArgumentException("JSON con contenido sobrante en la posición " + p.pos);
        }
        return v;
    }

    private static final class Parser {
        private final String s;
        private int pos;

        Parser(String s) {
            this.s = s;
        }

        void skipWs() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) {
                pos++;
            }
        }

        Object value() {
            if (pos >= s.length()) {
                throw new IllegalArgumentException("JSON incompleto");
            }
            char c = s.charAt(pos);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> str();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> num();
            };
        }

        Object literal(String word, Object v) {
            if (!s.startsWith(word, pos)) {
                throw new IllegalArgumentException("Literal inválido en la posición " + pos);
            }
            pos += word.length();
            return v;
        }

        Map<String, Object> object() {
            Map<String, Object> m = new LinkedHashMap<>();
            pos++;
            skipWs();
            if (s.charAt(pos) == '}') {
                pos++;
                return m;
            }
            while (true) {
                skipWs();
                String k = str();
                skipWs();
                expect(':');
                skipWs();
                m.put(k, value());
                skipWs();
                if (s.charAt(pos) == ',') {
                    pos++;
                } else {
                    expect('}');
                    return m;
                }
            }
        }

        List<Object> array() {
            List<Object> l = new ArrayList<>();
            pos++;
            skipWs();
            if (s.charAt(pos) == ']') {
                pos++;
                return l;
            }
            while (true) {
                skipWs();
                l.add(value());
                skipWs();
                if (s.charAt(pos) == ',') {
                    pos++;
                } else {
                    expect(']');
                    return l;
                }
            }
        }

        String str() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = s.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    char e = s.charAt(pos++);
                    switch (e) {
                        case 'n' -> sb.append('\n');
                        case 't' -> sb.append('\t');
                        case 'r' -> sb.append('\r');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            sb.append((char) Integer.parseInt(s.substring(pos, pos + 4), 16));
                            pos += 4;
                        }
                        default -> sb.append(e);
                    }
                } else {
                    sb.append(c);
                }
            }
        }

        Double num() {
            int start = pos;
            while (pos < s.length() && "+-0123456789.eE".indexOf(s.charAt(pos)) >= 0) {
                pos++;
            }
            if (start == pos) {
                throw new IllegalArgumentException("Valor JSON inválido en la posición " + pos);
            }
            return Double.parseDouble(s.substring(start, pos));
        }

        void expect(char c) {
            if (pos >= s.length() || s.charAt(pos) != c) {
                throw new IllegalArgumentException("Se esperaba '" + c + "' en la posición " + pos);
            }
            pos++;
        }
    }
}
