package co.edu.uniquindio.bibliometria.web;

import co.edu.uniquindio.bibliometria.clustering.ClusteringService;
import co.edu.uniquindio.bibliometria.corpus.Article;
import co.edu.uniquindio.bibliometria.corpus.Corpus;
import co.edu.uniquindio.bibliometria.matching.MatchingService;
import co.edu.uniquindio.bibliometria.matching.RabinKarp;
import co.edu.uniquindio.bibliometria.similarity.SimilarityService;
import co.edu.uniquindio.bibliometria.similarity.ai.SentenceEmbeddingClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Servidor HTTP basado en {@code com.sun.net.httpserver} (incluido en el JDK). Expone la API REST
 * de los tres requerimientos y sirve la interfaz web estática.
 *
 * <pre>
 * GET  /api/info                      estado de la aplicación y de los modelos
 * GET  /api/articles                  corpus: título, autores, año, revista y abstract
 * POST /api/similarity                {ids, algorithms, levels}            → matrices y pares
 * GET  /api/similarity/trace          ?a&b&algorithm&level&limit           → paso a paso
 * GET  /api/clustering                ?representation&k                    → 3 dendrogramas + métricas
 * GET  /api/search                    ?pattern&caseSensitive               → ocurrencias y métricas
 * GET  /api/search/trace              ?pattern&article&fragment&prime      → paso a paso
 * GET  /api/search/benchmark          ?pattern&repetitions                 → tiempos y escalamiento
 * </pre>
 */
public class ApiServer {

    private final Corpus corpus;
    private final SimilarityService similarity;
    private final ClusteringService clustering;
    private final MatchingService matching;
    private final SentenceEmbeddingClient minilmClient;
    private final int wordVectorCount;

    public ApiServer(Corpus corpus, SimilarityService similarity, ClusteringService clustering,
                     MatchingService matching, SentenceEmbeddingClient minilmClient, int wordVectorCount) {
        this.corpus = corpus;
        this.similarity = similarity;
        this.clustering = clustering;
        this.matching = matching;
        this.minilmClient = minilmClient;
        this.wordVectorCount = wordVectorCount;
    }

    public HttpServer start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/api/", this::handleApi);
        server.createContext("/", this::handleStatic);
        server.start();
        return server;
    }

    private void handleApi(HttpExchange ex) throws IOException {
        try {
            String path = ex.getRequestURI().getPath();
            Map<String, String> q = query(ex.getRequestURI().getRawQuery());
            Object body = switch (path) {
                case "/api/info" -> info();
                case "/api/articles" -> articles();
                case "/api/similarity" -> similarityCompare(readJson(ex));
                case "/api/similarity/trace" -> similarity.trace(q.get("a"), q.get("b"), q.get("algorithm"),
                        q.get("level"), intParam(q, "limit", 12));
                case "/api/clustering" -> clustering.run(q.getOrDefault("representation", "tfidf"),
                        intParam(q, "k", 0));
                case "/api/search" -> matching.search(q.get("pattern"), boolParam(q, "caseSensitive"));
                case "/api/search/trace" -> matching.trace(q.get("pattern"), q.getOrDefault("article", "01"),
                        q.get("fragment"), boolParam(q, "caseSensitive"),
                        Long.parseLong(q.getOrDefault("prime", String.valueOf(RabinKarp.DEFAULT_PRIME))));
                case "/api/search/benchmark" -> matching.benchmark(q.get("pattern"), boolParam(q, "caseSensitive"),
                        Math.min(200, intParam(q, "repetitions", 31)));
                default -> throw new NotFound(path);
            };
            send(ex, 200, "application/json; charset=utf-8", Json.write(body).getBytes(StandardCharsets.UTF_8));
        } catch (NotFound e) {
            sendError(ex, 404, "Recurso no encontrado: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            sendError(ex, 400, e.getMessage());
        } catch (UncheckedIOException e) {
            sendError(ex, 503, e.getCause().getMessage());
        } catch (IOException e) {
            sendError(ex, 503, e.getMessage());
        } catch (RuntimeException e) {
            e.printStackTrace();
            sendError(ex, 500, "Error interno: " + e);
        }
    }

    private Map<String, Object> info() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("articulos", corpus.size());
        m.put("vectoresDePalabras", wordVectorCount);
        m.put("minilmToken", minilmClient.hasToken());
        m.put("minilmCache", minilmClient.cachedVectors());
        m.put("algoritmosSimilitud", SimilarityService.allKeys());
        return m;
    }

    private List<Map<String, Object>> articles() {
        List<Map<String, Object>> list = new ArrayList<>();
        for (Article a : corpus.all()) {
            list.add(a.toMap(true));
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> similarityCompare(Object body) {
        if (!(body instanceof Map<?, ?> m)) {
            throw new IllegalArgumentException("Se esperaba un objeto JSON");
        }
        List<String> ids = new ArrayList<>();
        for (Object o : (List<Object>) m.get("ids")) {
            ids.add(String.valueOf(o));
        }
        List<String> keys = new ArrayList<>();
        Object algs = m.get("algorithms");
        if (algs instanceof List<?> l && !l.isEmpty()) {
            for (Object o : l) {
                keys.add(String.valueOf(o));
            }
        } else {
            keys.addAll(SimilarityService.allKeys());
        }
        Map<String, String> levels = new HashMap<>();
        if (m.get("levels") instanceof Map<?, ?> lv) {
            for (Map.Entry<?, ?> e : lv.entrySet()) {
                levels.put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
            }
        }
        return similarity.compare(ids, keys, levels);
    }

    // ------------------------------------------------------------------ archivos estáticos

    private void handleStatic(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) {
            path = "/index.html";
        }
        if (path.contains("..")) {
            sendError(ex, 400, "Ruta inválida");
            return;
        }
        try (InputStream in = ApiServer.class.getResourceAsStream("/static" + path)) {
            if (in == null) {
                sendError(ex, 404, "No encontrado");
                return;
            }
            send(ex, 200, contentType(path), in.readAllBytes());
        }
    }

    private static String contentType(String path) {
        if (path.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (path.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (path.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (path.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "application/octet-stream";
    }

    // ------------------------------------------------------------------ utilidades HTTP

    private static Object readJson(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            throw new IllegalArgumentException("Use POST con un cuerpo JSON");
        }
        String text = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return Json.parse(text);
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> q = new HashMap<>();
        if (raw == null || raw.isEmpty()) {
            return q;
        }
        for (String part : raw.split("&")) {
            int eq = part.indexOf('=');
            String k = eq < 0 ? part : part.substring(0, eq);
            String v = eq < 0 ? "" : part.substring(eq + 1);
            q.put(URLDecoder.decode(k, StandardCharsets.UTF_8), URLDecoder.decode(v, StandardCharsets.UTF_8));
        }
        return q;
    }

    private static int intParam(Map<String, String> q, String name, int def) {
        String v = q.get(name);
        if (v == null || v.isBlank()) {
            return def;
        }
        try {
            return Integer.parseInt(v.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Parámetro numérico inválido: " + name);
        }
    }

    private static boolean boolParam(Map<String, String> q, String name) {
        return "true".equalsIgnoreCase(q.get(name)) || "1".equals(q.get(name));
    }

    private static void sendError(HttpExchange ex, int status, String message) throws IOException {
        send(ex, status, "application/json; charset=utf-8",
                Json.write(Map.of("error", message == null ? "Error" : message)).getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange ex, int status, String type, byte[] bytes) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static final class NotFound extends RuntimeException {
        NotFound(String m) {
            super(m);
        }
    }
}
