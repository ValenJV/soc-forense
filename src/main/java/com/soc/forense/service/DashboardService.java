package com.soc.forense.service;

import com.soc.forense.ai.AiClient;
import com.soc.forense.config.AppConfig;
import com.soc.forense.dao.AnalysisResultDao;
import com.soc.forense.dao.EvidenceDao;
import com.soc.forense.dao.IncidentDao;
import com.soc.forense.dao.UserDao;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Reune los numeros del tablero. El "indice de riesgo" pondera los hallazgos
 * por severidad y los comprime a 0-100 con una curva que satura: los primeros
 * criticos mueven mucho la aguja, los siguientes cada vez menos.
 */
public class DashboardService {

    private final EvidenceDao evidenceDao = new EvidenceDao();
    private final AnalysisResultDao analysisDao = new AnalysisResultDao();
    private final IncidentDao incidentDao = new IncidentDao();
    private final UserDao userDao = new UserDao();
    private final AiClient aiClient = new AiClient();

    public Map<String, Object> snapshot(boolean checkAi) {
        Map<String, Object> m = new LinkedHashMap<>();

        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (String s : new String[]{"ingested", "parsed", "analyzed", "error"}) {
            byStatus.put(s, evidenceDao.countByStatus(s));
        }
        m.put("evidenceTotal", evidenceDao.count());
        m.put("evidenceByStatus", byStatus);

        Map<String, Long> sev = analysisDao.countBySeverity();
        for (String s : new String[]{"critical", "high", "medium", "low"}) {
            sev.putIfAbsent(s, 0L);
        }
        m.put("severity", sev);
        m.put("findingsTotal", analysisDao.count());
        m.put("criticalTotal", sev.get("critical"));

        m.put("openIncidents", incidentDao.countOpen());
        m.put("incidentsByStatus", incidentDao.countByStatus());
        m.put("usersTotal", userDao.count());

        int risk = riskIndex(sev);
        m.put("riskIndex", risk);
        m.put("riskLabel", riskLabel(risk));

        if (checkAi) {
            Map<String, Object> ai = new LinkedHashMap<>();
            ai.put("enabled", aiClient.isEnabled());
            ai.put("reachable", aiClient.isReachable());
            ai.put("model", AppConfig.aiModel());
            ai.put("baseUrl", AppConfig.aiBaseUrl());
            m.put("ai", ai);
        }
        return m;
    }

    static int riskIndex(Map<String, Long> sev) {
        double weighted = sev.getOrDefault("critical", 0L) * 10.0
                + sev.getOrDefault("high", 0L) * 5.0
                + sev.getOrDefault("medium", 0L) * 2.0
                + sev.getOrDefault("low", 0L) * 0.5;
        return (int) Math.round(100 * (1 - Math.exp(-weighted / 45.0)));
    }

    static String riskLabel(int risk) {
        if (risk >= 75) {
            return "Crítico";
        }
        if (risk >= 50) {
            return "Alto";
        }
        if (risk >= 25) {
            return "Moderado";
        }
        return "Bajo";
    }
}
