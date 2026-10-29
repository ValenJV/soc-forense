package com.soc.forense.service;

import com.soc.forense.dao.AnalysisResultDao;
import com.soc.forense.dao.EvidenceDao;
import com.soc.forense.dao.ParsedLogDao;
import com.soc.forense.model.Evidence;
import com.soc.forense.model.ParsedLog;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Convierte las lineas crudas del archivo en registros estructurados. Trabaja
 * solo con expresiones regulares, asi que no necesita la IA para esta etapa: la
 * IA entra despues, en el analisis. Reconoce los formatos mas comunes de Linux
 * y de servidores web; lo que no encaja se guarda igual como linea cruda para no
 * perder evidencia.
 */
public class LogParserService {

    private final EvidenceDao evidenceDao = new EvidenceDao();
    private final ParsedLogDao parsedLogDao = new ParsedLogDao();
    private final AnalysisResultDao analysisResultDao = new AnalysisResultDao();

    // syslog clasico: "Aug 12 09:33:21 host servicio[pid]: mensaje"
    private static final Pattern SYSLOG = Pattern.compile(
            "^(?<mon>[A-Z][a-z]{2})\\s+(?<day>\\d{1,2})\\s+(?<time>\\d{2}:\\d{2}:\\d{2})\\s+"
            + "(?<host>\\S+)\\s+(?<svc>[^\\s\\[:]+)(?:\\[(?<pid>\\d+)\\])?:\\s*(?<msg>.*)$");

    // syslog con fecha ISO: "2024-08-12T09:33:21.123+00:00 host servicio[pid]: mensaje"
    private static final Pattern SYSLOG_ISO = Pattern.compile(
            "^(?<ts>\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})\\S*\\s+"
            + "(?<host>\\S+)\\s+(?<svc>[^\\s\\[:]+)(?:\\[(?<pid>\\d+)\\])?:\\s*(?<msg>.*)$");

    // apache/nginx access: 'ip - - [fecha] "GET /x HTTP/1.1" 200 123'
    private static final Pattern ACCESS = Pattern.compile(
            "^(?<ip>\\d+\\.\\d+\\.\\d+\\.\\d+)\\s+\\S+\\s+(?<user>\\S+)\\s+\\[(?<ts>[^\\]]+)\\]\\s+"
            + "\"(?<method>[A-Z]+)\\s+(?<path>\\S+)[^\"]*\"\\s+(?<status>\\d{3})");

    // auditd: 'type=SYSCALL msg=audit(1660293201.123:456): ... '
    private static final Pattern AUDIT = Pattern.compile(
            "^type=(?<type>\\S+)\\s+msg=audit\\((?<epoch>\\d+)\\.\\d+:\\d+\\):\\s*(?<msg>.*)$");

    private static final Pattern IP = Pattern.compile("(\\d{1,3}\\.){3}\\d{1,3}");
    // Del mas especifico al mas general: el primero que coincida gana.
    private static final Pattern[] USER_PATTERNS = {
            Pattern.compile("for invalid user ([\\w.@-]+)"),
            Pattern.compile("(?:password|publickey|keyboard-interactive/pam) for ([\\w.@-]+) from"),
            Pattern.compile("^\\s*([\\w.-]+) : (?:TTY|PWD|USER)="),          // sudo: quien ejecuta
            Pattern.compile("new user: name=([\\w.-]+)"),
            Pattern.compile("\\bacct=\"?([\\w.@-]+)"),                          // auditd
            Pattern.compile("\\buser=([\\w.@-]+)"),                              // pam_unix
            Pattern.compile("\\b(?:for|of) user ([\\w.@-]+)"),
            Pattern.compile("^([\\w.-]+)\\s+\\S+\\s+\\S+\\s+\\[")     // access log con usuario
    };

    private static final DateTimeFormatter ACCESS_FMT =
            DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss", Locale.ENGLISH);

    /** Parsea la evidencia completa y persiste los registros por lotes. */
    public int parse(Evidence ev) {
        Path file = Paths.get(ev.getStoredPath());
        if (!Files.exists(file)) {
            evidenceDao.updateStatus(ev.getId(), "error", "El archivo ya no está en disco");
            throw new RuntimeException("El archivo de evidencia no existe en disco");
        }

        // Re-parsear reemplaza todo: los hallazgos viejos apuntarian a lineas que ya no existen.
        analysisResultDao.deleteByEvidence(ev.getId());
        parsedLogDao.deleteByEvidence(ev.getId());

        List<ParsedLog> batch = new ArrayList<>();
        int lineNumber = 0;
        int total = 0;
        java.nio.charset.CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPLACE)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPLACE);
        try (BufferedReader reader = new BufferedReader(
                new java.io.InputStreamReader(Files.newInputStream(file), decoder))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                batch.add(parseLine(ev, lineNumber, line));
                if (batch.size() >= 500) {
                    parsedLogDao.insertBatch(batch);
                    total += batch.size();
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                parsedLogDao.insertBatch(batch);
                total += batch.size();
            }
        } catch (IOException e) {
            evidenceDao.updateStatus(ev.getId(), "error", "Error leyendo el archivo: " + e.getMessage());
            throw new RuntimeException("No se pudo leer la evidencia", e);
        }

        evidenceDao.markParsed(ev.getId(), total);
        return total;
    }

    private ParsedLog parseLine(Evidence ev, int number, String raw) {
        ParsedLog p = new ParsedLog();
        p.setEvidenceId(ev.getId());
        p.setLineNumber(number);
        p.setRawLine(raw.length() > 4000 ? raw.substring(0, 4000) : raw);

        Matcher m = SYSLOG.matcher(raw);
        if (m.matches()) {
            p.setHostname(m.group("host"));
            p.setService(m.group("svc"));
            String pid = m.group("pid");
            if (pid != null) {
                p.setPid(Integer.valueOf(pid));
            }
            p.setEventTime(syslogTime(m.group("mon"), m.group("day"), m.group("time")));
            String msg = m.group("msg");
            p.setAction(shorten(msg));
            fillFromMessage(p, msg);
            return p;
        }

        Matcher iso = SYSLOG_ISO.matcher(raw);
        if (iso.matches()) {
            p.setHostname(iso.group("host"));
            p.setService(iso.group("svc"));
            String pid = iso.group("pid");
            if (pid != null) {
                p.setPid(Integer.valueOf(pid));
            }
            try {
                p.setEventTime(LocalDateTime.parse(iso.group("ts")));
            } catch (DateTimeParseException ignored) {
            }
            String msg = iso.group("msg");
            p.setAction(shorten(msg));
            fillFromMessage(p, msg);
            return p;
        }

        Matcher a = ACCESS.matcher(raw);
        if (a.lookingAt()) {
            p.setSourceIp(a.group("ip"));
            String user = a.group("user");
            if (user != null && !"-".equals(user)) {
                p.setUsername(user);
            }
            p.setService("http");
            p.setAction(a.group("method") + " " + a.group("path") + " -> " + a.group("status"));
            p.setTarget(a.group("path"));
            try {
                p.setEventTime(LocalDateTime.parse(a.group("ts").split(" ")[0], ACCESS_FMT));
            } catch (DateTimeParseException ignored) {
            }
            return p;
        }

        Matcher au = AUDIT.matcher(raw);
        if (au.matches()) {
            p.setService("auditd:" + au.group("type").toLowerCase(Locale.ROOT));
            try {
                long epoch = Long.parseLong(au.group("epoch"));
                p.setEventTime(LocalDateTime.ofEpochSecond(epoch, 0, java.time.ZoneOffset.UTC));
            } catch (NumberFormatException ignored) {
            }
            String msg = au.group("msg");
            p.setAction(shorten(msg));
            fillFromMessage(p, msg);
            return p;
        }

        // Sin formato reconocido: se guarda la linea cruda y lo que se pueda extraer.
        fillFromMessage(p, raw);
        p.setAction(shorten(raw));
        return p;
    }

    private void fillFromMessage(ParsedLog p, String msg) {
        if (p.getSourceIp() == null) {
            Matcher ip = IP.matcher(msg);
            if (ip.find()) {
                p.setSourceIp(ip.group());
            }
        }
        if (p.getUsername() == null) {
            for (Pattern up : USER_PATTERNS) {
                Matcher u = up.matcher(msg);
                if (u.find()) {
                    String name = u.group(1).replaceAll("\\.+$", "");
                    if (!name.isEmpty() && !name.matches("\\d+")) {
                        p.setUsername(name);
                        break;
                    }
                }
            }
        }
    }

    private LocalDateTime syslogTime(String mon, String day, String time) {
        try {
            Month month = MONTHS.get(mon);
            if (month == null) {
                return null;
            }
            int year = LocalDateTime.now().getYear();
            String[] hms = time.split(":");
            return LocalDateTime.of(year, month, Integer.parseInt(day.trim()),
                    Integer.parseInt(hms[0]), Integer.parseInt(hms[1]), Integer.parseInt(hms[2]));
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String shorten(String s) {
        if (s == null) {
            return null;
        }
        s = s.trim();
        return s.length() > 250 ? s.substring(0, 250) : s;
    }

    private static final Map<String, Month> MONTHS = Map.ofEntries(
            Map.entry("Jan", Month.JANUARY), Map.entry("Feb", Month.FEBRUARY),
            Map.entry("Mar", Month.MARCH), Map.entry("Apr", Month.APRIL),
            Map.entry("May", Month.MAY), Map.entry("Jun", Month.JUNE),
            Map.entry("Jul", Month.JULY), Map.entry("Aug", Month.AUGUST),
            Map.entry("Sep", Month.SEPTEMBER), Map.entry("Oct", Month.OCTOBER),
            Map.entry("Nov", Month.NOVEMBER), Map.entry("Dec", Month.DECEMBER));
}
