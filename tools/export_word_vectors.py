"""
Exporta los vectores de palabras preentrenados del modelo en_core_web_md de spaCy
para el vocabulario del corpus (archivo generado por la aplicación con la opción --vocabulario).

Uso:
    pip install spacy
    pip install https://github.com/explosion/spacy-models/releases/download/en_core_web_md-3.8.0/en_core_web_md-3.8.0-py3-none-any.whl
    python tools/export_word_vectors.py vocabulario.txt src/main/resources/embeddings/word-vectors-300d.txt

Solo se ejecuta una vez, antes de compilar. La aplicación Java lee el archivo resultante
y hace todos los cálculos (promedio, coseno, distancia euclidiana) por su cuenta.
"""
import sys

import spacy


def main(vocab_path: str, out_path: str) -> None:
    nlp = spacy.load("en_core_web_md")
    meta = nlp.meta["vectors"]
    with open(vocab_path, encoding="utf-8") as f:
        words = [w.strip() for w in f if w.strip()]
    found = 0
    with open(out_path, "w", encoding="utf-8") as out:
        out.write(f"# spaCy {spacy.__version__} en_core_web_md {nlp.meta['version']}: "
                  f"{meta['width']} dimensiones, {meta['vectors']} vectores, {meta['keys']} claves\n")
        for w in words:
            if nlp.vocab.has_vector(w):
                vec = nlp.vocab.get_vector(w)
                out.write(w + " " + " ".join(f"{x:.5f}" for x in vec) + "\n")
                found += 1
    print(f"{found} de {len(words)} palabras tienen vector")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
