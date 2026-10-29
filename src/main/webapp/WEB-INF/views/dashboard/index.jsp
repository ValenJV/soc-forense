<%@ include file="/WEB-INF/views/includes/top.jspf" %>
<c:set var="sev" value="${stats.severity}" />
<c:set var="tot" value="${stats.findingsTotal}" />

<header class="topbar">
    <div>
        <div class="eyebrow">Tablero</div>
        <h1>Estado del SOC</h1>
        <p class="sub">Lo que hay cargado, lo que encontró el análisis y lo que sigue abierto.</p>
    </div>
    <div class="topbar-actions">
        <span class="pill" id="ai-pill" title="Servidor de IA"><span class="dot"></span><span>Comprobando IA…</span></span>
        <c:if test="${u.canAnalyze()}">
            <a class="btn btn-rosa" href="${ctx}/evidencias#subir"><svg class="ic"><use href="#i-subir"/></svg>Subir evidencia</a>
        </c:if>
    </div>
</header>

<div class="grid g-hero">
    <section class="card">
        <div class="card-head">
            <h2>Índice de riesgo</h2>
            <span class="hint">Se actualiza cada 30 s</span>
        </div>
        <div class="gauge-wrap">
            <div class="gauge" id="gauge" data-risk="${stats.riskIndex}">
                <svg viewBox="0 0 300 170" role="img" aria-label="Índice de riesgo ${stats.riskIndex} de 100">
                    <defs>
                        <linearGradient id="g-arc" x1="0" x2="1" y1="0" y2="0">
                            <stop offset="0" stop-color="#4dd0ff"/>
                            <stop offset=".55" stop-color="#ff9dc0"/>
                            <stop offset="1" stop-color="#ff2e74"/>
                        </linearGradient>
                    </defs>
                    <path class="track" d="M30 150 A120 120 0 0 1 270 150" stroke-width="18" fill="none" stroke-linecap="round"/>
                    <path id="gauge-arc" d="M30 150 A120 120 0 0 1 270 150" stroke="url(#g-arc)" stroke-width="18" fill="none"
                          stroke-linecap="round" pathLength="100" stroke-dasharray="0 100"/>
                    <g id="gauge-ticks"></g>
                    <g class="needle" id="gauge-needle" style="transform: rotate(-90deg)">
                        <line x1="150" y1="84" x2="150" y2="38" stroke="#ffffff" stroke-width="3.5" stroke-linecap="round"/>
                        <circle cx="150" cy="84" r="4.5" fill="#ffffff"/>
                    </g>
                </svg>
                <div class="gauge-read">
                    <div class="gauge-num" id="gauge-num">${stats.riskIndex}</div>
                    <div class="gauge-label">Nivel <b id="gauge-label"><c:out value="${stats.riskLabel}" /></b></div>
                </div>
            </div>

            <div>
                <div class="sevbar" id="sevbar">
                    <span class="b-critical" style="width:${tot == 0 ? 0 : sev.critical * 100 / tot}%"></span>
                    <span class="b-high" style="width:${tot == 0 ? 0 : sev.high * 100 / tot}%"></span>
                    <span class="b-medium" style="width:${tot == 0 ? 0 : sev.medium * 100 / tot}%"></span>
                    <span class="b-low" style="width:${tot == 0 ? 0 : sev.low * 100 / tot}%"></span>
                </div>
                <div class="sevlegend">
                    <a href="${ctx}/hallazgos?sev=critical"><div><span class="sev sev-critical">Crítica</span><strong data-sev="critical">${sev.critical}</strong></div></a>
                    <a href="${ctx}/hallazgos?sev=high"><div><span class="sev sev-high">Alta</span><strong data-sev="high">${sev.high}</strong></div></a>
                    <a href="${ctx}/hallazgos?sev=medium"><div><span class="sev sev-medium">Media</span><strong data-sev="medium">${sev.medium}</strong></div></a>
                    <a href="${ctx}/hallazgos?sev=low"><div><span class="sev sev-low">Baja</span><strong data-sev="low">${sev.low}</strong></div></a>
                </div>
                <p style="color:var(--apagado);font-size:12.5px;margin:14px 0 0">
                    Pondera los hallazgos por severidad. Los primeros críticos mueven mucho la aguja; los siguientes, cada vez menos.
                </p>
            </div>
        </div>
    </section>

    <div class="grid g-2">
        <a class="card metric" href="${ctx}/evidencias" style="color:inherit">
            <div class="k"><svg class="ic"><use href="#i-evidencia"/></svg>Evidencias</div>
            <div class="v" data-stat="evidenceTotal">${stats.evidenceTotal}</div>
            <div class="foot">${soc:cant(stats.evidenceByStatus.analyzed, 'analizada', 'analizadas')} · ${stats.evidenceByStatus.error} con error</div>
        </a>
        <a class="card metric cel" href="${ctx}/hallazgos" style="color:inherit">
            <div class="k"><svg class="ic"><use href="#i-hallazgo"/></svg>Hallazgos</div>
            <div class="v" data-stat="findingsTotal">${stats.findingsTotal}</div>
            <div class="foot">Todas las evidencias</div>
        </a>
        <a class="card metric rosa" href="${ctx}/hallazgos?sev=critical" style="color:inherit">
            <div class="k"><svg class="ic"><use href="#i-alerta"/></svg>Críticos</div>
            <div class="v" data-stat="criticalTotal">${stats.criticalTotal}</div>
            <div class="foot">Requieren acción inmediata</div>
        </a>
        <a class="card metric" href="${ctx}/incidentes" style="color:inherit">
            <div class="k"><svg class="ic"><use href="#i-incidente"/></svg>Incidentes abiertos</div>
            <div class="v" data-stat="openIncidents">${stats.openIncidents}</div>
            <div class="foot">${stats.incidentsByStatus.investigating} en investigación</div>
        </a>
    </div>
</div>

<div class="grid g-2 mt">
    <section class="card">
        <div class="card-head">
            <h2>Amenazas más graves</h2>
            <a class="hint" href="${ctx}/hallazgos">Ver todas</a>
        </div>
        <c:choose>
            <c:when test="${empty criticalThreats}">
                <div class="empty"><svg class="ic"><use href="#i-check"/></svg><h3>Sin amenazas altas o críticas</h3><p>Cuando un análisis encuentre algo serio, aparecerá aquí.</p></div>
            </c:when>
            <c:otherwise>
                <c:forEach var="r" items="${criticalThreats}">
                    <div class="finding f-${r.severity}">
                        <div class="finding-head">
                            <span class="sev sev-${r.severity}">${soc:severidad(r.severity)}</span>
                            <h3><c:out value="${r.threatType}" /></h3>
                            <c:if test="${not empty r.mitreTechnique}"><span class="mitre"><c:out value="${r.mitreTechnique}" /></span></c:if>
                        </div>
                        <p><c:out value="${soc:recortar(r.threatDescription, 180)}" /></p>
                        <div class="finding-meta">
                            <a href="${ctx}/evidencias/detalle?id=${r.evidenceId}">Evidencia #${r.evidenceId}</a>
                            <c:forEach var="ioc" items="${r.indicatorList}" end="2"><span class="ioc"><c:out value="${ioc}" /></span></c:forEach>
                            <span>${soc:hace(r.createdAt)}</span>
                        </div>
                    </div>
                </c:forEach>
            </c:otherwise>
        </c:choose>
    </section>

    <section class="card flush">
        <div class="card-head">
            <h2>Incidentes abiertos</h2>
            <a class="hint" href="${ctx}/incidentes">Gestionar</a>
        </div>
        <c:choose>
            <c:when test="${empty openIncidentList}">
                <div class="empty"><svg class="ic"><use href="#i-incidente"/></svg><h3>No hay incidentes abiertos</h3><p>Puede abrir uno desde cualquier evidencia analizada.</p></div>
            </c:when>
            <c:otherwise>
                <div class="table-wrap" style="margin-top:12px">
                    <table class="t">
                        <thead><tr><th>Incidente</th><th>Severidad</th><th>Estado</th></tr></thead>
                        <tbody>
                        <c:forEach var="i" items="${openIncidentList}">
                            <tr>
                                <td><a class="cell-title" href="${ctx}/incidentes/detalle?id=${i.id}"><c:out value="${i.title}" /></a>
                                    <div class="cell-sub">#${i.id} · ${soc:hace(i.createdAt)}<c:if test="${not empty i.assignedTo}"> · <c:out value="${i.assignedTo}" /></c:if></div></td>
                                <td><span class="sev sev-${i.severity}">${soc:severidad(i.severity)}</span></td>
                                <td><span class="st st-${i.status}">${soc:estadoIncidente(i.status)}</span></td>
                            </tr>
                        </c:forEach>
                        </tbody>
                    </table>
                </div>
            </c:otherwise>
        </c:choose>
    </section>
</div>

<section class="card flush mt">
    <div class="card-head">
        <h2>Evidencias recientes</h2>
        <a class="hint" href="${ctx}/evidencias">Todas las evidencias</a>
    </div>
    <c:choose>
        <c:when test="${empty recentEvidence}">
            <div class="empty"><svg class="ic"><use href="#i-vacio"/></svg><h3>Todavía no hay evidencias</h3><p>Suba un archivo de log para empezar.</p></div>
        </c:when>
        <c:otherwise>
            <div class="table-wrap" style="margin-top:12px">
                <table class="t">
                    <thead><tr><th>Archivo</th><th>Tipo</th><th>Estado</th><th class="num">Líneas</th><th class="num">Hallazgos</th><th>Subida</th></tr></thead>
                    <tbody>
                    <c:forEach var="e" items="${recentEvidence}">
                        <tr>
                            <td><a class="cell-title" href="${ctx}/evidencias/detalle?id=${e.id}"><c:out value="${e.filename}" /></a>
                                <div class="cell-sub mono">${e.shortHash}…</div></td>
                            <td><span class="pill"><c:out value="${e.logType}" /></span></td>
                            <td><span class="st st-${e.status}">${soc:estadoEvidencia(e.status)}</span></td>
                            <td class="num">${e.lineCount}</td>
                            <td class="num">${e.threatCount}<c:if test="${e.criticalCount > 0}"> <span class="sev sev-critical">${e.criticalCount}</span></c:if></td>
                            <td class="muted"><c:out value="${e.uploadUsername}" /> · ${soc:hace(e.createdAt)}</td>
                        </tr>
                    </c:forEach>
                    </tbody>
                </table>
            </div>
        </c:otherwise>
    </c:choose>
</section>

<%@ include file="/WEB-INF/views/includes/bottom.jspf" %>
