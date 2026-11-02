package co.edu.uniquindio.bibliometria.corpus;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrae el abstract de un artículo científico en PDF.
 *
 * <p>PDFBox solo se usa para obtener el texto plano de las dos primeras páginas.
 * La localización del abstract es una heurística propia basada en marcadores:</p>
 * <ol>
 *   <li>Inicio: el encabezado "A B S T R A C T" (Elsevier) o una línea que empieza por "Abstract".</li>
 *   <li>Si justo después aparece el bloque de palabras clave (Elsevier), se salta.</li>
 *   <li>Fin: un marcador como "Keywords", "1. Introduction" o "K E Y W O R D S", aunque aparezca
 *       pegado a la última frase, o un cambio de ancho de columna (el abstract ocupa todo el ancho
 *       y la introducción está a dos columnas).</li>
 * </ol>
 */
public class PdfAbstractExtractor {

    private static final Pattern SPACED_START = Pattern.compile("A\\s?B\\s?S\\s?T\\s?R\\s?A\\s?C\\s?T");
    private static final Pattern LINE_START = Pattern.compile("^\\s*Abstract\\s*[:.]?\\s*(.*)$");

    /** Marcadores de fin que pueden aparecer en cualquier posición de la línea. */
    private static final Pattern INLINE_END = Pattern.compile(
            "(1\\.\\s*Introduction|1\\.\\s*INTRODUCTION|K\\s?E\\s?Y\\s?W\\s?O\\s?R\\s?D\\s?S"
                    + "|Keywords?\\s*:|Key\\s?words\\s*:)");

    /** Marcadores de fin que deben iniciar la línea. */
    private static final Pattern LINE_END = Pattern.compile(
            "^\\s*(Keywords?|KEYWORDS|Key\\s*words|Introduction\\s*$|INTRODUCTION|Background\\s*$"
                    + "|Citation:|©|Copyright|Paper type|Received:|Academic Editor|Correspondence"
                    + "|\\*\\s*Correspondence|Article history)\\b.*");

    /** Líneas de cabecera, pie de página o barra lateral que se descartan. */
    private static final Pattern NOISE = Pattern.compile(
            "^\\s*(\\d{1,4}|https?://\\S+|doi:\\S+|DOI:.*|E-mail.*|\\*?\\s*Corresponding author.*"
                    + "|AI for|education)\\s*$");

    private static final int MIN_WORDS_BEFORE_END = 40;

    public String extract(File pdf) throws IOException {
        try (PDDocument doc = PDDocument.load(pdf)) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setStartPage(1);
            stripper.setEndPage(Math.min(2, doc.getNumberOfPages()));
            return extractFromText(stripper.getText(doc));
        }
    }

    /** Separado del manejo de PDF para poder probar la heurística con texto plano. */
    public String extractFromText(String text) {
        String[] lines = text.split("\\r?\\n");

        // 1. Localizar el inicio del abstract.
        int start = -1;
        String rest = "";
        for (int i = 0; i < lines.length && start < 0; i++) {
            Matcher spaced = SPACED_START.matcher(lines[i]);
            if (spaced.find()) {
                start = i;
                rest = lines[i].substring(spaced.end());
            }
        }
        for (int i = 0; i < lines.length && start < 0; i++) {
            Matcher m = LINE_START.matcher(lines[i]);
            if (m.matches()) {
                start = i;
                rest = m.group(1);
            }
        }
        if (start < 0) {
            return "";
        }

        // 2. Acumular líneas hasta encontrar un marcador de fin.
        List<String> collected = new ArrayList<>();
        int maxWidth = 0;
        boolean skippingKeywords = false;
        List<String> pending = new ArrayList<>();
        if (!rest.isBlank()) {
            pending.add(rest);
        }
        for (int i = start + 1; i < lines.length; i++) {
            pending.add(lines[i]);
        }

        for (int i = 0; i < pending.size(); i++) {
            String line = pending.get(i).trim();
            if (line.isEmpty()) {
                continue;
            }
            int words = wordCount(collected);
            if (skippingKeywords) {
                if (line.length() < 60) {
                    continue; // las palabras clave son líneas cortas
                }
                skippingKeywords = false;
            }
            if (LINE_END.matcher(line).matches()) {
                if (words < MIN_WORDS_BEFORE_END) {
                    skippingKeywords = true;
                    continue;
                }
                break;
            }
            Matcher inline = INLINE_END.matcher(line);
            if (inline.find() && words >= MIN_WORDS_BEFORE_END) {
                String prefix = line.substring(0, inline.start()).trim();
                if (!prefix.isEmpty()) {
                    collected.add(prefix);
                }
                break;
            }
            if (NOISE.matcher(line).matches()) {
                continue;
            }
            collected.add(line);
            maxWidth = Math.max(maxWidth, line.length());

            // Cambio de columna: el párrafo terminó y las líneas siguientes son mucho más estrechas.
            if (words >= 80 && line.endsWith(".") && narrowerBlockFollows(pending, i + 1, maxWidth)) {
                break;
            }
        }
        return normalize(joinLines(collected));
    }

    private static boolean narrowerBlockFollows(List<String> lines, int from, int maxWidth) {
        int seen = 0;
        int total = 0;
        for (int i = from; i < lines.size() && seen < 4; i++) {
            String l = lines.get(i).trim();
            if (l.isEmpty()) {
                continue;
            }
            total += l.length();
            seen++;
        }
        return seen == 4 && maxWidth > 90 && (double) total / seen < 0.75 * maxWidth;
    }

    private static int wordCount(List<String> lines) {
        int count = 0;
        for (String l : lines) {
            boolean inWord = false;
            for (int i = 0; i < l.length(); i++) {
                boolean nonSpace = !Character.isWhitespace(l.charAt(i));
                if (nonSpace && !inWord) {
                    count++;
                }
                inWord = nonSpace;
            }
        }
        return count;
    }

    /** Une las líneas y repara las palabras partidas con guion al final de línea ("in-" + "clusion"). */
    static String joinLines(List<String> lines) {
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            if (sb.length() > 0) {
                int n = sb.length();
                boolean brokenWord = sb.charAt(n - 1) == '-' && n > 1 && Character.isLowerCase(sb.charAt(n - 2))
                        && !line.isEmpty() && Character.isLowerCase(line.charAt(0));
                if (brokenWord) {
                    sb.setLength(n - 1);
                } else {
                    sb.append(' ');
                }
            }
            sb.append(line);
        }
        return sb.toString();
    }

    /** Normaliza ligaduras tipográficas, guiones blandos y espacios. */
    static String normalize(String s) {
        String r = s.replace("ﬁ", "fi").replace("ﬂ", "fl").replace("ﬀ", "ff")
                .replace("ﬃ", "ffi").replace("ﬄ", "ffl").replace("­", "")
                .replace(' ', ' ');
        // Wiley inserta un espacio después del guion de las palabras compuestas ("well- defined"), y al unir
        // líneas queda el mismo espacio cuando la línea termina en "AI-" ("AI- driven"). No se toca un guion
        // suspendido como "pre- and misconceptions".
        r = r.replaceAll("(?<=[\\p{L}\\p{N}])- (?=\\p{Ll})(?!and\\b|or\\b|to\\b)", "-");
        return r.replaceAll("\\s+", " ").trim();
    }
}
