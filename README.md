# Análisis de algoritmos en el contexto de la bibliometría

Proyecto final de Análisis de Algoritmos (Universidad del Quindío, 2026-2).
Integrantes: Giovanny Steven Contreras y Andrés Felipe Ortega.

Aplicación web en **Java 21** que analiza los abstracts de 20 artículos sobre inteligencia artificial en educación:

| Requerimiento | Qué hace | Algoritmos (implementados desde cero) |
|---|---|---|
| 1. Similitud textual | Compara los abstracts de 2 o más artículos y muestra la traza paso a paso | Levenshtein, Needleman-Wunsch, coseno con TF-IDF, Jaccard; IA: promedio de embeddings de palabras y embeddings de oración all-MiniLM-L6-v2 |
| 2. Agrupamiento jerárquico | Construye un dendrograma por criterio y los evalúa | Enlace simple, completo y promedio (UPGMA); correlación cofenética y silueta |
| 3. Búsqueda de patrones | Encuentra un patrón en los 20 abstracts, mide tiempos y muestra las tablas auxiliares | Knuth-Morris-Pratt, Boyer-Moore (carácter malo + sufijo bueno), Rabin-Karp |

## Requisitos

- JDK 21 y Maven 3.9 (o solo Docker).
- Conexión a internet la primera vez (Maven descarga PDFBox).

## Ejecutar en el computador

```bash
mvn package
java -jar target/bibliometria-ia.jar
```

Abrir http://localhost:8080. Al iniciar, la aplicación lee `corpus/metadata.tsv` y extrae el abstract de cada PDF de `corpus/pdfs/`.

### Activar el modelo MiniLM (IA 2)

El modelo all-MiniLM-L6-v2 se consulta en la API de inferencia de Hugging Face, y la respuesta se guarda en `corpus/cache/minilm-embeddings.tsv`.

1. Crear una cuenta gratuita en https://huggingface.co y un token de lectura en *Settings → Access Tokens*.
2. Ejecutar una sola vez:

   ```bash
   # Linux / macOS
   HF_TOKEN=hf_xxx java -jar target/bibliometria-ia.jar --precalcular-minilm
   # Windows PowerShell
   $env:HF_TOKEN="hf_xxx"; java -jar target/bibliometria-ia.jar --precalcular-minilm
   ```

3. Subir al repositorio el archivo `corpus/cache/minilm-embeddings.tsv`. Con la caché, la aplicación desplegada funciona sin token.

## Verificar los algoritmos

```bash
mvn test-compile
java -cp target/classes:target/test-classes co.edu.uniquindio.bibliometria.VerificacionAlgoritmos
```

En Windows, el separador del classpath es `;` en lugar de `:`.

La verificación ejecuta más de 28 000 comprobaciones:

- **Casos conocidos de la literatura:** kitten/sitting = 3; GATTACA/GCATGCU = 0; π(ababaca); y la tabla bmGs del ejemplo de Charras y Lecroq.
- **Búsqueda de patrones:** compara KMP, Boyer-Moore y Rabin-Karp con una búsqueda ingenua en 4 000 textos aleatorios.
- **Clustering:** compara los resultados con valores calculados a mano.

Además, durante el desarrollo se contrastaron los resultados con librerías de referencia de Python. No hubo diferencias o, en algunos casos, las diferencias fueron menores que 1e-14:

- Levenshtein frente a rapidfuzz.
- Coseno TF-IDF frente a scikit-learn.
- Alturas de las uniones y correlación cofenética frente a scipy.
- Silueta frente a scikit-learn.

## Desplegar en Render (gratis)

1. Subir este proyecto a un repositorio de GitHub. Debe incluir la carpeta `corpus/`, con los PDF y, si ya la generaron, la caché de MiniLM.
2. En https://render.com: *New → Blueprint* y elegir el repositorio. Render lee `render.yaml` y construye la imagen con el `Dockerfile`.
3. Si no publican la caché, agregar la variable de entorno `HF_TOKEN` en el panel del servicio.
4. El plan gratuito suspende el servicio tras 15 minutos sin uso; la primera visita después tarda alrededor de un minuto.

## Estructura

```
corpus/                 metadata.tsv (título, autores, año, revista) y pdfs/01..20.pdf
src/main/java/co/edu/uniquindio/bibliometria/
  corpus/               extracción del abstract (heurística propia sobre el texto de PDFBox)
  text/                 normalización, tokenización, stopwords y S-stemmer de Harman
  similarity/           Levenshtein, Needleman-Wunsch, TF-IDF + coseno, Jaccard
  similarity/ai/        embeddings de palabras (promedio) y cliente de MiniLM
  clustering/           enlace simple/completo/promedio, métricas y dendrograma SVG
  matching/             KMP, Boyer-Moore, Rabin-Karp y medición de rendimiento
  web/                  servidor HTTP del JDK, API REST y JSON
src/main/resources/
  static/               interfaz web (HTML, CSS y JavaScript sin librerías)
  embeddings/           vectores de spaCy en_core_web_md para las 962 palabras del corpus con vector
tools/export_word_vectors.py   script que generó el archivo de vectores (se ejecuta una sola vez)
```

## Dependencias y uso de herramientas externas

- **PDFBox** solo convierte el PDF en texto plano. La localización del abstract es una heurística propia: `PdfAbstractExtractor`.
- **Vectores de palabras:** archivo preentrenado de spaCy `en_core_web_md` 3.8.0 (300 dimensiones). La aplicación carga el archivo y calcula ella misma el promedio, el coseno y la distancia euclidiana.
- **MiniLM:** la API solo devuelve el vector de cada abstract. El coseno y la distancia se calculan en `VectorMath`.
- **Algoritmos propios:** los algoritmos de similitud, el clustering, las métricas, el dendrograma y la búsqueda de patrones se implementan con arreglos, ciclos y estructuras básicas del lenguaje.

## API

| Método | Ruta | Parámetros |
|---|---|---|
| GET | `/api/articles` | — |
| POST | `/api/similarity` | `{"ids":["05","06"], "algorithms":[...], "levels":{"levenshtein":"caracter"}}` |
| GET | `/api/similarity/trace` | `a, b, algorithm, level, limit` |
| GET | `/api/clustering` | `representation` (tfidf, word-vectors, minilm), `k` |
| GET | `/api/search` | `pattern, caseSensitive` |
| GET | `/api/search/trace` | `pattern, article, fragment, prime` |
| GET | `/api/search/benchmark` | `pattern, repetitions` |
