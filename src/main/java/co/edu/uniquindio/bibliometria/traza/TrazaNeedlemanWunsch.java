package co.edu.uniquindio.bibliometria.traza;

import co.edu.uniquindio.bibliometria.corpus.Article;
import co.edu.uniquindio.bibliometria.similarity.NeedlemanWunsch;
import co.edu.uniquindio.bibliometria.similarity.NeedlemanWunsch.Alignment;
import co.edu.uniquindio.bibliometria.similarity.Sequences;

import java.util.LinkedHashMap;
import java.util.Map;

/** Traza de Needleman-Wunsch para el caso de estudio: matriz F y alineamiento recuperado. */
public final class TrazaNeedlemanWunsch {

    private TrazaNeedlemanWunsch() {
    }

    public static Map<String, Object> generar(NeedlemanWunsch alg, Article a, Article b, int limit) {
        Sequences.Level level = alg.level();
        Sequences.Pair full = Sequences.encode(a.abstractText(), b.abstractText(), level, Integer.MAX_VALUE);
        Alignment all = NeedlemanWunsch.align(full.a(), full.b(), full.symbolsA(), full.symbolsB());

        Sequences.Pair p = Sequences.encode(a.abstractText(), b.abstractText(), level, limit);
        Alignment small = NeedlemanWunsch.align(p.a(), p.b(), p.symbolsA(), p.symbolsB());

        Map<String, Object> t = new LinkedHashMap<>();
        t.put("nivel", level.name().toLowerCase());
        t.put("parametros", Map.of("coincidencia", NeedlemanWunsch.MATCH, "sustitucion", NeedlemanWunsch.MISMATCH,
                "hueco", NeedlemanWunsch.GAP));
        t.put("formula", "F[i][j] = max(F[i-1][j-1] + s(a_i,b_j), F[i-1][j] + g, F[i][j-1] + g)");
        t.put("prefijoA", p.symbolsA());
        t.put("prefijoB", p.symbolsB());
        t.put("matriz", small.matrix());
        t.put("alineamientoPrefijoA", small.alignedA());
        t.put("alineamientoPrefijoB", small.alignedB());
        t.put("puntajePrefijo", small.score());
        t.put("identidadPrefijo", small.identity());
        t.put("longitudA", full.a().length);
        t.put("longitudB", full.b().length);
        t.put("puntajeCompleto", all.score());
        t.put("longitudAlineamiento", all.length());
        t.put("coincidencias", all.matches());
        t.put("sustituciones", all.mismatches());
        t.put("huecos", all.gaps());
        t.put("similitud", all.identity());
        return t;
    }
}
