package co.edu.uniquindio.bibliometria.traza;

import co.edu.uniquindio.bibliometria.corpus.Article;
import co.edu.uniquindio.bibliometria.similarity.TfIdfModel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Traza del coseno con TF-IDF: tabla de pesos de los términos compartidos, producto punto y normas. */
public final class TrazaCoseno {

    private TrazaCoseno() {
    }

    public static Map<String, Object> generar(TfIdfModel model, Article a, Article b, int limit) {
        Map<String, Integer> tfA = model.tf(a);
        Map<String, Integer> tfB = model.tf(b);
        Map<String, Double> va = model.vector(a);
        Map<String, Double> vb = model.vector(b);

        List<Map<String, Object>> shared = new ArrayList<>();
        double dot = 0;
        for (String term : new TreeSet<>(va.keySet())) {
            if (vb.containsKey(term)) {
                double prod = va.get(term) * vb.get(term);
                dot += prod;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("termino", term);
                row.put("tfA", tfA.get(term));
                row.put("tfB", tfB.get(term));
                row.put("df", model.df(term));
                row.put("idf", model.idf(term));
                row.put("wA", va.get(term));
                row.put("wB", vb.get(term));
                row.put("producto", prod);
                shared.add(row);
            }
        }
        shared.sort((x, y) -> Double.compare((double) y.get("producto"), (double) x.get("producto")));

        double na = TfIdfModel.norm(va);
        double nb = TfIdfModel.norm(vb);
        double cos = na == 0 || nb == 0 ? 0 : dot / (na * nb);

        Map<String, Object> t = new LinkedHashMap<>();
        t.put("formulaIdf", "idf(t) = ln((1 + N) / (1 + df(t))) + 1, N = " + model.documents());
        t.put("formulaPeso", "w(t,d) = tf(t,d) * idf(t)");
        t.put("formulaCoseno", "cos(θ) = (A·B) / (‖A‖ ‖B‖)");
        t.put("terminosA", va.size());
        t.put("terminosB", vb.size());
        t.put("terminosCompartidos", shared.size());
        t.put("tablaCompartidos", shared.size() > limit ? shared.subList(0, limit) : shared);
        t.put("productoPunto", dot);
        t.put("normaA", na);
        t.put("normaB", nb);
        t.put("coseno", cos);
        t.put("anguloGrados", Math.toDegrees(Math.acos(Math.min(1.0, cos))));
        t.put("similitud", cos);
        return t;
    }
}
