package co.edu.uniquindio.bibliometria.corpus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Artículo del corpus: metadatos (título y autores) y abstract extraído del PDF. */
public record Article(String id, String title, List<String> authors, int year, String journal, String abstractText) {

    public String firstAuthorSurname() {
        if (authors.isEmpty()) {
            return "";
        }
        String[] parts = authors.get(0).split(" ");
        return parts[parts.length - 1];
    }

    /** Etiqueta corta usada en tablas y dendrogramas, por ejemplo "05 Chen (2020)". */
    public String shortLabel() {
        return id + " " + firstAuthorSurname() + " (" + year + ")";
    }

    public Map<String, Object> toMap(boolean includeAbstract) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id);
        m.put("title", title);
        m.put("authors", authors);
        m.put("year", year);
        m.put("journal", journal);
        m.put("label", shortLabel());
        if (includeAbstract) {
            m.put("abstract", abstractText);
            m.put("characters", abstractText.length());
        }
        return m;
    }
}
