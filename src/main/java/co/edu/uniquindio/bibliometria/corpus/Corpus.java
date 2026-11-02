package co.edu.uniquindio.bibliometria.corpus;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Corpus documental del proyecto: 20 artículos en PDF entregados por el docente.
 *
 * <p>Al cargar se leen los metadatos verificados (título, autores, año, revista) del archivo
 * {@code metadata.tsv} y el abstract de cada artículo se extrae directamente del PDF con
 * {@link PdfAbstractExtractor}.</p>
 */
public class Corpus {

    private final Map<String, Article> articles = new LinkedHashMap<>();

    public static Corpus load(Path corpusDir) throws IOException {
        // PDFBox reporta advertencias de fuentes que no afectan la extracción.
        Logger.getLogger("org.apache.pdfbox").setLevel(Level.SEVERE);

        Corpus corpus = new Corpus();
        PdfAbstractExtractor extractor = new PdfAbstractExtractor();
        List<String> lines = Files.readAllLines(corpusDir.resolve("metadata.tsv"), StandardCharsets.UTF_8);
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] f = line.split("\t");
            String id = f[0].trim();
            List<String> authors = new ArrayList<>();
            for (String a : f[2].split(";")) {
                authors.add(a.trim());
            }
            File pdf = corpusDir.resolve("pdfs").resolve(id + ".pdf").toFile();
            String abstractText = extractor.extract(pdf);
            if (abstractText.isBlank()) {
                throw new IOException("No se pudo extraer el abstract del artículo " + id);
            }
            corpus.articles.put(id, new Article(id, f[1].trim(), Collections.unmodifiableList(authors),
                    Integer.parseInt(f[3].trim()), f[4].trim(), abstractText));
        }
        return corpus;
    }

    public List<Article> all() {
        return new ArrayList<>(articles.values());
    }

    public Article get(String id) {
        Article a = articles.get(id);
        if (a == null) {
            throw new IllegalArgumentException("Artículo inexistente: " + id);
        }
        return a;
    }

    public int size() {
        return articles.size();
    }

    /**
     * Cruce de metadatos: autores que comparten dos artículos. La comparación se hace sobre el
     * nombre normalizado (minúsculas, sin tildes ni puntos).
     */
    public static List<String> sharedAuthors(Article a, Article b) {
        List<String> shared = new ArrayList<>();
        for (String x : a.authors()) {
            for (String y : b.authors()) {
                if (normalizeName(x).equals(normalizeName(y))) {
                    shared.add(x);
                }
            }
        }
        return shared;
    }

    static String normalizeName(String name) {
        String n = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder();
        for (char c : n.toCharArray()) {
            if (Character.isLetter(c) && c < 128) {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }
}
