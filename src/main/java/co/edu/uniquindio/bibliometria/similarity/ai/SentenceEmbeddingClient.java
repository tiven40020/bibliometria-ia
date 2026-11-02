package co.edu.uniquindio.bibliometria.similarity.ai;

import co.edu.uniquindio.bibliometria.corpus.Article;
import co.edu.uniquindio.bibliometria.web.Json;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Cliente del modelo de lenguaje all-MiniLM-L6-v2 (sentence-transformers) a través de la API de
 * inferencia de Hugging Face. El modelo recibe el abstract completo y devuelve un vector denso de
 * 384 dimensiones que representa el significado de todo el texto.
 *
 * <p>Los vectores se guardan en un archivo de caché ({@code minilm-embeddings.tsv}) junto con un
 * hash del texto. Así la API solo se consulta una vez por abstract y la aplicación desplegada puede
 * funcionar sin token si se publica la caché.</p>
 *
 * <p>Variables de entorno: {@code HF_TOKEN} (token de Hugging Face) y, opcionalmente,
 * {@code EMBEDDINGS_API_URL} para usar otro punto de acceso compatible.</p>
 */
public class SentenceEmbeddingClient {

    public static final String MODEL = "sentence-transformers/all-MiniLM-L6-v2";
    public static final String DEFAULT_URL =
            "https://router.huggingface.co/hf-inference/models/" + MODEL + "/pipeline/feature-extraction";

    private final String url;
    private final String token;
    private final Path cacheFile;
    private final Map<String, double[]> cache = new HashMap<>();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20)).build();

    public SentenceEmbeddingClient(String url, String token, Path cacheFile) {
        this.url = url == null || url.isBlank() ? DEFAULT_URL : url;
        this.token = token;
        this.cacheFile = cacheFile;
        loadCache();
    }

    public static SentenceEmbeddingClient fromEnvironment(Path cacheFile) {
        return new SentenceEmbeddingClient(System.getenv("EMBEDDINGS_API_URL"), System.getenv("HF_TOKEN"), cacheFile);
    }

    public boolean hasToken() {
        return token != null && !token.isBlank();
    }

    public int cachedVectors() {
        return cache.size();
    }

    /** Devuelve el embedding de cada artículo, consultando la API solo para los que no están en caché. */
    public synchronized Map<String, double[]> embed(List<Article> articles) throws IOException {
        List<Article> missing = new ArrayList<>();
        for (Article a : articles) {
            if (!cache.containsKey(hash(a.abstractText()))) {
                missing.add(a);
            }
        }
        if (!missing.isEmpty()) {
            if (!hasToken()) {
                throw new IOException("El modelo MiniLM necesita la variable de entorno HF_TOKEN "
                        + "(o el archivo de caché " + cacheFile + " con los embeddings precalculados).");
            }
            List<String> texts = new ArrayList<>();
            for (Article a : missing) {
                texts.add(a.abstractText());
            }
            List<double[]> vectors = request(texts);
            for (int i = 0; i < missing.size(); i++) {
                cache.put(hash(missing.get(i).abstractText()), vectors.get(i));
            }
            saveCache();
        }
        Map<String, double[]> out = new LinkedHashMap<>();
        for (Article a : articles) {
            out.put(a.id(), cache.get(hash(a.abstractText())));
        }
        return out;
    }

    private List<double[]> request(List<String> texts) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inputs", texts);
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(120))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(body), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp;
        try {
            resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Consulta a la API interrumpida", e);
        }
        if (resp.statusCode() != 200) {
            throw new IOException("La API de embeddings respondió " + resp.statusCode() + ": "
                    + resp.body().substring(0, Math.min(300, resp.body().length())));
        }
        return parseResponse(resp.body(), texts.size());
    }

    /**
     * La API devuelve un vector por texto ([[...], [...]]). Algunos modelos devuelven en cambio un
     * vector por token ([[[...], ...], ...]); en ese caso se aplica mean pooling sobre los tokens.
     */
    static List<double[]> parseResponse(String body, int expected) throws IOException {
        Object parsed = Json.parse(body);
        if (!(parsed instanceof List<?> outer) || outer.size() != expected) {
            throw new IOException("Respuesta inesperada de la API de embeddings");
        }
        List<double[]> result = new ArrayList<>();
        for (Object item : outer) {
            List<?> l = (List<?>) item;
            if (!l.isEmpty() && l.get(0) instanceof List<?>) {
                result.add(meanPooling(l));
            } else {
                result.add(toArray(l));
            }
        }
        return result;
    }

    private static double[] meanPooling(List<?> tokens) {
        double[] sum = null;
        for (Object t : tokens) {
            double[] v = toArray((List<?>) t);
            if (sum == null) {
                sum = new double[v.length];
            }
            for (int i = 0; i < v.length; i++) {
                sum[i] += v[i];
            }
        }
        for (int i = 0; i < sum.length; i++) {
            sum[i] /= tokens.size();
        }
        return sum;
    }

    private static double[] toArray(List<?> l) {
        double[] v = new double[l.size()];
        for (int i = 0; i < v.length; i++) {
            v[i] = ((Number) l.get(i)).doubleValue();
        }
        return v;
    }

    static String hash(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(text.getBytes(StandardCharsets.UTF_8))).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private void loadCache() {
        if (cacheFile == null || !Files.exists(cacheFile)) {
            return;
        }
        try {
            for (String line : Files.readAllLines(cacheFile, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] f = line.split("\t");
                String[] nums = f[1].split(" ");
                double[] v = new double[nums.length];
                for (int i = 0; i < v.length; i++) {
                    v[i] = Double.parseDouble(nums[i]);
                }
                cache.put(f[0], v);
            }
        } catch (IOException e) {
            System.err.println("No se pudo leer la caché de embeddings: " + e.getMessage());
        }
    }

    private void saveCache() {
        if (cacheFile == null) {
            return;
        }
        StringBuilder sb = new StringBuilder("# hash-del-abstract\tvector " + MODEL + "\n");
        for (Map.Entry<String, double[]> e : cache.entrySet()) {
            sb.append(e.getKey()).append('\t');
            double[] v = e.getValue();
            for (int i = 0; i < v.length; i++) {
                if (i > 0) {
                    sb.append(' ');
                }
                sb.append(v[i]);
            }
            sb.append('\n');
        }
        try {
            if (cacheFile.getParent() != null) {
                Files.createDirectories(cacheFile.getParent());
            }
            Files.writeString(cacheFile, sb.toString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("No se pudo guardar la caché de embeddings: " + e.getMessage());
        }
    }
}
