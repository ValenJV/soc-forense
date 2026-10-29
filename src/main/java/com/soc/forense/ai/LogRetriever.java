package com.soc.forense.ai;

import com.soc.forense.model.ParsedLog;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Recuperacion de lineas para fundamentar al agente (Retrieval-Augmented
 * Generation). En vez de enviar siempre las primeras lineas del log, se
 * puntua cada linea segun la pregunta del analista y se envian las mas
 * relevantes, en su orden original. Asi el modelo responde con evidencia
 * real de la organizacion y no con conocimiento general.
 *
 * Puntuacion (lexica, sin dependencias externas):
 * - cada termino de la pregunta presente en la linea suma 1,
 * - IPs, usuarios y numeros exactos suman 3 (son indicadores),
 * - las lineas con senales de ataque suman 1 extra.
 */
public final class LogRetriever {

    private static final Pattern IP = Pattern.compile("(\\d{1,3}\\.){3}\\d{1,3}");
    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}_./:-]{3,}");
    private static final Pattern SIGNAL = Pattern.compile(
            "(?i)fail|invalid|denied|refused|accepted|sudo|useradd|usermod|passwd|authorized_keys|"
            + "error|root|wget|curl|chmod|crontab|\\s[45]\\d\\d\\s|select|union|script|\\.\\./");

    private static final Set<String> STOP = Set.of(
            "que", "qué", "cual", "cuál", "cuales", "como", "cómo", "donde", "dónde", "cuando", "hay", "del",
            "los", "las", "una", "uno", "unos", "unas", "por", "para", "con", "sin", "sobre", "este", "esta",
            "esto", "ese", "esa", "eso", "fue", "son", "está", "estan", "están", "the", "and", "log",
            "logs", "linea", "líneas", "lineas", "línea", "evidencia", "archivo", "dime", "explica", "puedes",
            "algo", "más", "mas", "muy", "sus", "ser", "tiene", "tienen");

    /** Pistas en español que se traducen a lo que realmente aparece en los logs. */
    private static final Map<String, List<String>> SYNONYMS = Map.ofEntries(
            Map.entry("fuerza", List.of("failed", "invalid")),
            Map.entry("bruta", List.of("failed", "invalid")),
            Map.entry("fallid", List.of("failed", "failure")),
            Map.entry("exito", List.of("accepted")),
            Map.entry("ingres", List.of("accepted", "session opened")),
            Map.entry("privilegi", List.of("sudo", "root")),
            Map.entry("escalad", List.of("sudo", "root")),
            Map.entry("cuenta", List.of("useradd", "usermod", "new user")),
            Map.entry("usuario", List.of("user", "useradd")),
            Map.entry("clave", List.of("authorized_keys", "publickey", "passwd")),
            Map.entry("persisten", List.of("authorized_keys", "crontab", "useradd")),
            Map.entry("inyecc", List.of("select", "union", "%27")),
            Map.entry("sql", List.of("select", "union", "sqlmap")),
            Map.entry("escane", List.of(" 404 ", "nikto", "sqlmap")),
            Map.entry("web", List.of("http/", "get ", "post ")));

    private LogRetriever() {
    }

    public static List<ParsedLog> retrieve(List<ParsedLog> lines, String question, int topK) {
        if (lines == null || lines.isEmpty() || topK <= 0) {
            return List.of();
        }
        if (lines.size() <= topK) {
            return lines;
        }
        Set<String> terms = new LinkedHashSet<>();
        Set<String> strong = new LinkedHashSet<>();
        extractTerms(question == null ? "" : question, terms, strong);

        List<Scored> scored = new ArrayList<>(lines.size());
        for (ParsedLog p : lines) {
            String raw = p.getRawLine() == null ? "" : p.getRawLine().toLowerCase(Locale.ROOT);
            int score = 0;
            for (String t : strong) {
                if (raw.contains(t)) {
                    score += 3;
                }
            }
            for (String t : terms) {
                if (raw.contains(t)) {
                    score += 1;
                }
            }
            if (SIGNAL.matcher(raw).find()) {
                score += 1;
            }
            scored.add(new Scored(p, score));
        }

        List<ParsedLog> best = scored.stream()
                .sorted(Comparator.comparingInt((Scored s) -> -s.score)
                        .thenComparingInt(s -> s.line.getLineNumber()))
                .limit(topK)
                .map(s -> s.line)
                .sorted(Comparator.comparingInt(ParsedLog::getLineNumber))
                .toList();
        return best;
    }

    static void extractTerms(String question, Set<String> terms, Set<String> strong) {
        String q = question.toLowerCase(Locale.ROOT);
        Matcher ip = IP.matcher(q);
        while (ip.find()) {
            strong.add(ip.group());
        }
        Matcher w = WORD.matcher(q);
        while (w.find()) {
            String word = w.group();
            if (STOP.contains(word) || strong.contains(word)) {
                continue;
            }
            String plain = sinTildes(word);
            boolean mapped = false;
            for (Map.Entry<String, List<String>> e : SYNONYMS.entrySet()) {
                if (plain.startsWith(e.getKey())) {
                    terms.addAll(e.getValue());
                    mapped = true;
                }
            }
            if (word.matches(".*\\d.*") || word.contains("_") || word.contains(".")) {
                strong.add(word); // numeros, rutas o nombres tecnicos: indicador exacto
            } else if (!mapped) {
                terms.add(word);
            }
        }
    }

    private static String sinTildes(String s) {
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }

    private record Scored(ParsedLog line, int score) {
    }
}
