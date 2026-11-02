package co.edu.uniquindio.bibliometria.text;

import java.util.Set;

/** Lista de palabras vacías del inglés (artículos, preposiciones, pronombres y verbos auxiliares). */
public final class Stopwords {

    private static final Set<String> WORDS = Set.of(
            "a", "about", "above", "across", "after", "again", "against", "all", "almost", "also", "although",
            "am", "among", "an", "and", "another", "any", "are", "as", "at", "be", "because", "been", "before",
            "being", "below", "between", "both", "but", "by", "can", "could", "did", "do", "does", "doing",
            "down", "during", "e", "each", "eg", "either", "et", "etc", "few", "for", "from", "further", "g",
            "had", "has", "have", "having", "he", "her", "here", "hers", "herself", "him", "himself", "his",
            "how", "however", "i", "ie", "if", "in", "into", "is", "it", "its", "itself", "just", "least",
            "less", "may", "me", "might", "more", "most", "much", "must", "my", "myself", "namely", "neither",
            "no", "nor", "not", "now", "of", "off", "often", "on", "once", "only", "or", "other", "others",
            "our", "ours", "ourselves", "out", "over", "own", "per", "rather", "same", "several", "she",
            "should", "since", "so", "some", "such", "than", "that", "the", "their", "theirs", "them",
            "themselves", "then", "there", "therefore", "these", "they", "this", "those", "thus", "through",
            "to", "too", "toward", "towards", "under", "until", "up", "upon", "us", "very", "via", "was",
            "we", "were", "what", "whatever", "when", "where", "whereas", "whether", "which", "while", "who",
            "whom", "whose", "why", "will", "with", "within", "without", "would", "yet", "you", "your",
            "yours", "yourself", "yourselves", "s", "t", "ii", "iii", "iv");

    private Stopwords() {
    }

    public static boolean contains(String word) {
        return WORDS.contains(word);
    }

    public static int size() {
        return WORDS.size();
    }
}
