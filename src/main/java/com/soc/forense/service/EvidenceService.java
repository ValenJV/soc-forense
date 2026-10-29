package com.soc.forense.service;

import com.soc.forense.config.AppConfig;
import com.soc.forense.dao.AnalysisResultDao;
import com.soc.forense.dao.EvidenceDao;
import com.soc.forense.dao.ParsedLogDao;
import com.soc.forense.model.Evidence;
import com.soc.forense.model.User;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Maneja la carga de archivos de evidencia: calcula el hash para la cadena de
 * custodia, guarda el archivo en disco, evita duplicados y adivina el tipo de
 * log a partir del contenido y el nombre.
 */
public class EvidenceService {

    private final EvidenceDao evidenceDao = new EvidenceDao();
    private final ParsedLogDao parsedLogDao = new ParsedLogDao();
    private final AnalysisResultDao analysisResultDao = new AnalysisResultDao();

    /** Directorio donde se guardan los archivos subidos. */
    private Path storageDir() {
        String dir = AppConfig.evidenceDir();
        Path base;
        if (dir == null || dir.isBlank()) {
            // Carpeta del usuario y no la temporal: el sistema operativo puede vaciar /tmp y
            // perder evidencia rompe la cadena de custodia.
            base = Paths.get(System.getProperty("user.home"), "soc-forense", "evidencias");
        } else {
            base = Paths.get(dir);
        }
        try {
            Files.createDirectories(base);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo preparar el directorio de evidencias: " + base, e);
        }
        return base;
    }

    /**
     * Guarda un archivo cargado. Si ya existe evidencia con el mismo hash,
     * devuelve la existente en lugar de duplicar (cadena de custodia).
     */
    public Evidence store(String originalName, InputStream data, User uploader) {
        String safeName = sanitize(originalName);
        Path tmp;
        String sha256;
        long size;
        try {
            tmp = Files.createTempFile("subida-", ".tmp");
            size = Files.copy(data, tmp, StandardCopyOption.REPLACE_EXISTING);
            sha256 = sha256Of(tmp);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo leer el archivo subido", e);
        }

        Optional<Evidence> existing = evidenceDao.findBySha256(sha256);
        if (existing.isPresent()) {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
            }
            return existing.get();
        }

        Path dest = storageDir().resolve(sha256 + "-" + safeName);
        try {
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("No se pudo guardar el archivo de evidencia", e);
        }

        Evidence ev = new Evidence();
        ev.setFilename(safeName);
        ev.setLogType(detectLogType(safeName, dest));
        ev.setFileSize(size);
        ev.setSha256(sha256);
        ev.setStoredPath(dest.toString());
        ev.setUploadUserId(uploader.getId());
        ev.setUploadUsername(uploader.getUsername());
        ev.setStatus("ingested");
        return evidenceDao.create(ev);
    }

    public List<Evidence> listAll() {
        return evidenceDao.findAll();
    }

    public Optional<Evidence> find(long id) {
        return evidenceDao.findById(id);
    }

    public long count() {
        return evidenceDao.count();
    }

    /** Borra la evidencia, sus logs, sus hallazgos y el archivo en disco. */
    public void delete(long id) {
        Optional<Evidence> ev = evidenceDao.findById(id);
        if (ev.isEmpty()) {
            return;
        }
        analysisResultDao.deleteByEvidence(id);
        parsedLogDao.deleteByEvidence(id);
        evidenceDao.delete(id);
        try {
            String stored = ev.get().getStoredPath();
            if (stored != null && !stored.isBlank()) {
                Files.deleteIfExists(Paths.get(stored));
            }
        } catch (IOException ignored) {
        }
    }

    /** Ruta del archivo en disco para descargas. */
    public Path resolveStoredFile(Evidence ev) {
        if (ev.getStoredPath() == null || ev.getStoredPath().isBlank()) {
            return null;
        }
        return Paths.get(ev.getStoredPath());
    }

    // ---- utilidades internas ----

    private String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "evidencia.log";
        }
        String base = name.replace("\\", "/");
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        base = base.replaceAll("[^A-Za-z0-9._-]", "_");
        if (base.isBlank()) {
            base = "evidencia.log";
        }
        if (base.length() > 120) {
            base = base.substring(0, 120);
        }
        return base;
    }

    private String sha256Of(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    md.update(buffer, 0, read);
                }
            }
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible en esta JVM", e);
        }
    }

    /**
     * Adivina el tipo de log mirando el nombre y una muestra del contenido.
     * Sirve para elegir el parser adecuado despues.
     */
    private String detectLogType(String name, Path file) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.contains("audit")) {
            return "audit";
        }
        if (lower.contains("secure") || lower.contains("auth")) {
            return "auth";
        }
        if (lower.contains("sudo")) {
            return "sudo";
        }
        if (lower.contains("access") || lower.contains("apache") || lower.contains("httpd")) {
            return "apache";
        }
        if (lower.contains("nginx")) {
            return "nginx";
        }
        if (lower.contains("firewall") || lower.contains("iptables") || lower.contains("ufw")) {
            return "firewall";
        }
        if (lower.contains("windows") || lower.contains("evtx") || lower.contains("security.log")) {
            return "windows";
        }
        String sample = sampleContent(file);
        if (sample.contains("type=SYSCALL") || sample.contains("audit(")) {
            return "audit";
        }
        if (sample.contains("sshd[") && sample.contains("Failed password")) {
            return "auth";
        }
        if (sample.contains("sudo:") || sample.contains("COMMAND=")) {
            return "sudo";
        }
        if (sample.matches("(?s).*\\d+\\.\\d+\\.\\d+\\.\\d+ - - \\[.*")) {
            return "apache";
        }
        if (sample.contains("kernel:") || sample.contains("systemd")) {
            return "syslog";
        }
        return "generic";
    }

    private String sampleContent(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[4096];
            int read = in.read(buffer);
            if (read <= 0) {
                return "";
            }
            return new String(buffer, 0, read, java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "";
        }
    }
}
