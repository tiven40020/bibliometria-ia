package co.edu.uniquindio.bibliometria.clustering;

import java.util.List;
import java.util.Locale;

/**
 * Dibuja el dendrograma en SVG a partir de la lista de uniones, sin librerías de graficación.
 * Las hojas se ubican a la izquierda (distancia 0) y la raíz a la derecha; cada unión se dibuja
 * como un "codo" que conecta a sus dos hijos a la altura (distancia) en que se unieron.
 * Las ramas se colorean según el grupo al que pertenecen al cortar el árbol en k grupos.
 */
public final class DendrogramSvg {

    private static final String[] COLORS = {
            "#2a6fdb", "#e0703c", "#2e9e6b", "#b4469c", "#c9a227", "#5b5fc7", "#d24d57", "#3a9fb5"};
    private static final String NEUTRAL = "#8a8f98";

    private DendrogramSvg() {
    }

    public static String render(List<HierarchicalClustering.Merge> merges, List<String> labels, int k, String title) {
        int n = labels.size();
        List<Integer> order = HierarchicalClustering.leafOrder(merges, n);
        int[] groups = HierarchicalClustering.cut(merges, n, k);

        double maxDist = 0;
        double minDist = Double.POSITIVE_INFINITY;
        for (HierarchicalClustering.Merge m : merges) {
            maxDist = Math.max(maxDist, m.distance());
            minDist = Math.min(minDist, m.distance());
        }
        // El eje empieza en la décima inferior a la primera unión para aprovechar el ancho del gráfico;
        // las hojas se dibujan en ese origen (se indica en la etiqueta del eje).
        double axisMin = Math.max(0, Math.floor(minDist * 10 - 1) / 10);
        if (maxDist - axisMin <= 0) {
            maxDist = axisMin + 1;
        }
        double span = maxDist - axisMin;

        int labelWidth = 230;
        int plotWidth = 520;
        int rowHeight = 24;
        int top = 40;
        int height = top + n * rowHeight + 50;
        int width = labelWidth + plotWidth + 30;

        double[] x = new double[2 * n - 1];
        double[] y = new double[2 * n - 1];
        int[] group = new int[2 * n - 1];
        for (int pos = 0; pos < n; pos++) {
            int leaf = order.get(pos);
            x[leaf] = labelWidth;
            y[leaf] = top + pos * rowHeight + rowHeight / 2.0;
            group[leaf] = groups[leaf];
        }
        for (HierarchicalClustering.Merge m : merges) {
            x[m.node()] = labelWidth + (m.distance() - axisMin) / span * plotWidth;
            y[m.node()] = (y[m.left()] + y[m.right()]) / 2;
            group[m.node()] = group[m.left()] == group[m.right()] ? group[m.left()] : -1;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.US,
                "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 %d %d\" width=\"%d\" height=\"%d\" "
                        + "font-family=\"system-ui, sans-serif\" font-size=\"12\" role=\"img\">",
                width, height, width, height));
        sb.append("<title>").append(escape(title)).append("</title>");
        sb.append(String.format(Locale.US, "<text x=\"%d\" y=\"22\" font-size=\"14\" font-weight=\"600\" fill=\"currentColor\">%s</text>",
                10, escape(title)));

        // Línea de corte entre la unión n−k−1 y la n−k.
        if (k > 1 && k <= n) {
            double low = n - k - 1 >= 0 ? merges.get(n - k - 1).distance() : 0;
            double high = merges.get(n - k).distance();
            double cx = labelWidth + ((low + high) / 2 - axisMin) / span * plotWidth;
            sb.append(String.format(Locale.US,
                    "<line x1=\"%.1f\" y1=\"%d\" x2=\"%.1f\" y2=\"%d\" stroke=\"%s\" stroke-dasharray=\"4 4\"/>",
                    cx, top - 6, cx, top + n * rowHeight, NEUTRAL));
            sb.append(String.format(Locale.US,
                    "<text x=\"%.1f\" y=\"%d\" fill=\"%s\" font-size=\"11\" text-anchor=\"middle\">corte k=%d</text>",
                    cx, top - 10, NEUTRAL, k));
        }

        for (HierarchicalClustering.Merge m : merges) {
            String color = group[m.node()] >= 0 ? COLORS[group[m.node()] % COLORS.length] : NEUTRAL;
            double xm = x[m.node()];
            // Codo: hijo izquierdo → altura de la unión → hijo derecho.
            sb.append(String.format(Locale.US,
                    "<path d=\"M%.1f %.1f H%.1f V%.1f H%.1f\" fill=\"none\" stroke=\"%s\" stroke-width=\"1.6\"/>",
                    x[m.left()], y[m.left()], xm, y[m.right()], x[m.right()], color));
        }

        for (int pos = 0; pos < n; pos++) {
            int leaf = order.get(pos);
            String color = COLORS[groups[leaf] % COLORS.length];
            sb.append(String.format(Locale.US,
                    "<circle cx=\"%.1f\" cy=\"%.1f\" r=\"3\" fill=\"%s\"/>", x[leaf], y[leaf], color));
            sb.append(String.format(Locale.US,
                    "<text x=\"%d\" y=\"%.1f\" text-anchor=\"end\" dominant-baseline=\"middle\" fill=\"currentColor\">%s</text>",
                    labelWidth - 8, y[leaf], escape(labels.get(leaf))));
        }

        // Eje de distancias.
        int axisY = top + n * rowHeight + 10;
        sb.append(String.format(Locale.US, "<line x1=\"%d\" y1=\"%d\" x2=\"%d\" y2=\"%d\" stroke=\"%s\"/>",
                labelWidth, axisY, labelWidth + plotWidth, axisY, NEUTRAL));
        for (int t = 0; t <= 5; t++) {
            double v = axisMin + span * t / 5;
            double tx = labelWidth + plotWidth * t / 5.0;
            sb.append(String.format(Locale.US,
                    "<line x1=\"%.1f\" y1=\"%d\" x2=\"%.1f\" y2=\"%d\" stroke=\"%s\"/>"
                            + "<text x=\"%.1f\" y=\"%d\" text-anchor=\"middle\" font-size=\"11\" fill=\"%s\">%.3f</text>",
                    tx, axisY, tx, axisY + 4, NEUTRAL, tx, axisY + 17, NEUTRAL, v));
        }
        sb.append(String.format(Locale.US,
                "<text x=\"%d\" y=\"%d\" text-anchor=\"middle\" font-size=\"11\" fill=\"%s\">distancia (1 − similitud del coseno)%s</text>",
                labelWidth + plotWidth / 2, axisY + 34, NEUTRAL,
                axisMin > 0 ? String.format(Locale.US, " · el eje inicia en %.1f", axisMin) : ""));
        sb.append("</svg>");
        return sb.toString();
    }

    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
