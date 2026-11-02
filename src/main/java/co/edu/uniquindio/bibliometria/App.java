package co.edu.uniquindio.bibliometria;

import co.edu.uniquindio.bibliometria.clustering.ClusteringService;
import co.edu.uniquindio.bibliometria.corpus.Article;
import co.edu.uniquindio.bibliometria.corpus.Corpus;
import co.edu.uniquindio.bibliometria.matching.MatchingService;
import co.edu.uniquindio.bibliometria.similarity.SimilarityService;
import co.edu.uniquindio.bibliometria.similarity.ai.SentenceEmbeddingClient;
import co.edu.uniquindio.bibliometria.similarity.ai.SentenceEmbeddingSimilarity;
import co.edu.uniquindio.bibliometria.similarity.ai.WordEmbeddingSimilarity;
import co.edu.uniquindio.bibliometria.similarity.ai.WordEmbeddings;
import co.edu.uniquindio.bibliometria.text.TextPreprocessor;
import co.edu.uniquindio.bibliometria.web.ApiServer;
import co.edu.uniquindio.bibliometria.web.Json;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Punto de entrada. Variables de entorno:
 * <ul>
 *   <li>PORT: puerto HTTP (8080 por defecto).</li>
 *   <li>CORPUS_DIR: carpeta con metadata.tsv y pdfs/ ("corpus" por defecto).</li>
 *   <li>HF_TOKEN: token de Hugging Face para el modelo MiniLM (opcional si existe la caché).</li>
 * </ul>
 * Opciones de línea de comandos:
 * <ul>
 *   <li>--vocabulario archivo.txt: escribe el vocabulario del corpus (para exportar los vectores de palabras).</li>
 *   <li>--exportar-corpus archivo.json: escribe los metadatos y abstracts extraídos.</li>
 *   <li>--precalcular-minilm: consulta la API una vez y guarda la caché de embeddings.</li>
 * </ul>
 */
public final class App {

    private App() {
    }

    public static void main(String[] args) throws Exception {
        Path corpusDir = resolveCorpusDir();
        System.out.println("Carpeta del corpus: " + corpusDir.toAbsolutePath().normalize());
        long t0 = System.nanoTime();
        Corpus corpus = Corpus.load(corpusDir);
        System.out.printf("Corpus cargado: %d artículos (%.1f s)%n", corpus.size(), (System.nanoTime() - t0) / 1e9);

        if (args.length >= 2 && args[0].equals("--vocabulario")) {
            TreeSet<String> vocab = new TreeSet<>();
            for (Article a : corpus.all()) {
                vocab.addAll(TextPreprocessor.contentTokens(a.abstractText()));
            }
            Files.write(Path.of(args[1]), vocab, StandardCharsets.UTF_8);
            System.out.println(vocab.size() + " palabras escritas en " + args[1]);
            return;
        }
        if (args.length >= 2 && args[0].equals("--exportar-corpus")) {
            List<Map<String, Object>> list = new ArrayList<>();
            for (Article a : corpus.all()) {
                list.add(a.toMap(true));
            }
            Files.writeString(Path.of(args[1]), Json.write(list), StandardCharsets.UTF_8);
            System.out.println("Corpus exportado en " + args[1]);
            return;
        }

        WordEmbeddings embeddings;
        try (InputStream in = App.class.getResourceAsStream("/embeddings/word-vectors-300d.txt")) {
            if (in == null) {
                throw new IllegalStateException("Falta el recurso embeddings/word-vectors-300d.txt");
            }
            embeddings = WordEmbeddings.load(in);
        }
        WordEmbeddingSimilarity wordVectors = new WordEmbeddingSimilarity(embeddings);

        Path cache = Path.of(env("MINILM_CACHE", corpusDir.resolve("cache").resolve("minilm-embeddings.tsv").toString()));
        SentenceEmbeddingClient minilmClient = SentenceEmbeddingClient.fromEnvironment(cache);
        SentenceEmbeddingSimilarity minilm = new SentenceEmbeddingSimilarity(minilmClient);

        if (args.length >= 1 && args[0].equals("--precalcular-minilm")) {
            minilmClient.embed(corpus.all());
            System.out.println("Caché de MiniLM guardada en " + cache + " (" + minilmClient.cachedVectors() + " vectores)");
            return;
        }

        SimilarityService similarity = new SimilarityService(corpus, wordVectors, minilm);
        ClusteringService clustering = new ClusteringService(corpus, similarity.tfidf(), wordVectors, minilmClient);
        MatchingService matching = new MatchingService(corpus);

        int port = Integer.parseInt(env("PORT", "8080"));
        new ApiServer(corpus, similarity, clustering, matching, minilmClient, embeddings.size()).start(port);
        System.out.printf("Vectores de palabras: %d · MiniLM: token=%s, caché=%d vectores%n",
                embeddings.size(), minilmClient.hasToken(), minilmClient.cachedVectors());
        System.out.println("Aplicación disponible en http://localhost:" + port);
    }

    /**
     * Ubica la carpeta del corpus. Si la variable CORPUS_DIR está definida, se usa esa ruta. Si no,
     * se busca una carpeta "corpus" con metadata.tsv en la carpeta de trabajo y, después, subiendo
     * desde la ubicación de las clases compiladas (target/classes o el JAR). Así la aplicación
     * funciona aunque el IDE la ejecute desde otra carpeta.
     */
    static Path resolveCorpusDir() {
        String configured = System.getenv("CORPUS_DIR");
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        List<Path> candidates = new ArrayList<>();
        Path cwd = Path.of("").toAbsolutePath();
        candidates.add(cwd.resolve("corpus"));
        candidates.add(cwd.resolve("bibliometria-ia").resolve("corpus"));
        try {
            Path code = Path.of(App.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            for (Path p = code; p != null; p = p.getParent()) {
                candidates.add(p.resolve("corpus"));
            }
        } catch (Exception e) {
            // Sin ubicación de las clases: solo se usan las rutas relativas a la carpeta de trabajo.
        }
        for (Path c : candidates) {
            if (Files.isRegularFile(c.resolve("metadata.tsv"))) {
                return c;
            }
        }
        throw new IllegalStateException("No se encontró la carpeta 'corpus' con metadata.tsv. Se buscó en: "
                + candidates + ". Ejecute la aplicación desde la carpeta del proyecto o defina la variable "
                + "de entorno CORPUS_DIR con la ruta de la carpeta corpus.");
    }

    private static String env(String name, String def) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? def : v;
    }
}
