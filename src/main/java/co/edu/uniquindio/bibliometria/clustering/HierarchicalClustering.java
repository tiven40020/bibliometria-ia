package co.edu.uniquindio.bibliometria.clustering;

import java.util.ArrayList;
import java.util.List;

/**
 * Agrupamiento jerárquico aglomerativo implementado desde cero.
 *
 * <ol>
 *   <li>Cada documento empieza como un grupo propio.</li>
 *   <li>En cada iteración se buscan los dos grupos activos más cercanos en la matriz de distancias.</li>
 *   <li>Se unen en un grupo nuevo y se actualizan sus distancias con la fórmula del criterio de enlace.</li>
 *   <li>Se repite hasta que queda un único grupo (n − 1 uniones).</li>
 * </ol>
 *
 * <p>Complejidad: cada iteración recorre la matriz O(n²) y hay n − 1 iteraciones, por lo que el
 * tiempo es O(n³) y el espacio O(n²). Con n = 20 abstracts son menos de 8 000 comparaciones.</p>
 *
 * <p>Numeración de nodos: las hojas son 0..n−1 y la unión número t crea el nodo n + t.</p>
 */
public final class HierarchicalClustering {

    private HierarchicalClustering() {
    }

    /** Una unión del dendrograma. */
    public record Merge(int left, int right, double distance, int size, int node) {
    }

    public static List<Merge> run(double[][] distances, Linkage linkage) {
        int n = distances.length;
        int total = 2 * n - 1;
        double[][] d = new double[total][total];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                d[i][j] = distances[i][j];
            }
        }
        boolean[] active = new boolean[total];
        int[] size = new int[total];
        for (int i = 0; i < n; i++) {
            active[i] = true;
            size[i] = 1;
        }

        List<Merge> merges = new ArrayList<>();
        for (int step = 0; step < n - 1; step++) {
            // 1. Par de grupos activos más cercano (en empate, el de menores índices).
            int bi = -1;
            int bj = -1;
            double best = Double.POSITIVE_INFINITY;
            for (int i = 0; i < n + step; i++) {
                if (!active[i]) {
                    continue;
                }
                for (int j = i + 1; j < n + step; j++) {
                    if (active[j] && d[i][j] < best) {
                        best = d[i][j];
                        bi = i;
                        bj = j;
                    }
                }
            }

            // 2. Crear el nuevo grupo y calcular su distancia a los demás (Lance-Williams).
            int node = n + step;
            size[node] = size[bi] + size[bj];
            for (int k = 0; k < node; k++) {
                if (active[k] && k != bi && k != bj) {
                    double v = linkage.update(d[bi][k], d[bj][k], size[bi], size[bj]);
                    d[node][k] = v;
                    d[k][node] = v;
                }
            }
            active[bi] = false;
            active[bj] = false;
            active[node] = true;
            merges.add(new Merge(bi, bj, best, size[node], node));
        }
        return merges;
    }

    /** Orden de las hojas para dibujar el dendrograma sin cruces (recorrido en profundidad desde la raíz). */
    public static List<Integer> leafOrder(List<Merge> merges, int n) {
        List<Integer> order = new ArrayList<>();
        if (n == 1) {
            order.add(0);
            return order;
        }
        collect(merges, n, merges.get(merges.size() - 1).node(), order);
        return order;
    }

    private static void collect(List<Merge> merges, int n, int node, List<Integer> order) {
        if (node < n) {
            order.add(node);
            return;
        }
        Merge m = merges.get(node - n);
        collect(merges, n, m.left(), order);
        collect(merges, n, m.right(), order);
    }

    /** Etiqueta de grupo (0..k−1) de cada hoja al cortar el árbol en k grupos: se deshacen las k−1 últimas uniones. */
    public static int[] cut(List<Merge> merges, int n, int k) {
        int[] parent = new int[2 * n - 1];
        for (int i = 0; i < parent.length; i++) {
            parent[i] = i;
        }
        for (int t = 0; t < n - k; t++) {
            Merge m = merges.get(t);
            parent[m.left()] = m.node();
            parent[m.right()] = m.node();
        }
        int[] labels = new int[n];
        int[] rootLabel = new int[2 * n - 1];
        for (int i = 0; i < rootLabel.length; i++) {
            rootLabel[i] = -1;
        }
        int next = 0;
        for (int i = 0; i < n; i++) {
            int r = i;
            while (parent[r] != r) {
                r = parent[r];
            }
            if (rootLabel[r] < 0) {
                rootLabel[r] = next++;
            }
            labels[i] = rootLabel[r];
        }
        return labels;
    }

    /** Matriz cofenética: altura de la unión en la que dos hojas quedan por primera vez en el mismo grupo. */
    public static double[][] cophenetic(List<Merge> merges, int n) {
        double[][] c = new double[n][n];
        List<List<Integer>> members = new ArrayList<>();
        for (int i = 0; i < 2 * n - 1; i++) {
            members.add(new ArrayList<>());
        }
        for (int i = 0; i < n; i++) {
            members.get(i).add(i);
        }
        for (Merge m : merges) {
            for (int x : members.get(m.left())) {
                for (int y : members.get(m.right())) {
                    c[x][y] = m.distance();
                    c[y][x] = m.distance();
                }
            }
            members.get(m.node()).addAll(members.get(m.left()));
            members.get(m.node()).addAll(members.get(m.right()));
        }
        return c;
    }
}
