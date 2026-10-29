<%@ include file="/WEB-INF/views/includes/top.jspf" %>
<c:set var="ev" value="${evidence}" />
<c:set var="parsed" value="${ev.status == 'parsed' or ev.status == 'analyzed'}" />
<c:set var="analyzed" value="${ev.status == 'analyzed'}" />

<header class="topbar">
    <div>
        <div class="eyebrow"><a href="${ctx}/evidencias">Evidencias</a> / #${ev.id}</div>
        <h1><c:out value="${ev.filename}" /></h1>
        <div class="pipeline" style="margin-top:12px">
            <span class="step done"><i>1</i>Ingerida</span>
            <span class="bar ${parsed ? 'done' : ''}"></span>
            <span class="step ${parsed ? 'done' : ''}"><i>2</i>Parseada</span>
            <span class="bar ${analyzed ? 'done' : ''}"></span>
            <span class="step ${analyzed ? 'done' : ''}"><i>3</i>Analizada</span>
        </div>
    </div>
    <div class="topbar-actions">
        <a class="btn btn-ghost" href="${ctx}/agente?evidencia=${ev.id}"><svg class="ic"><use href="#i-agente"/></svg>Preguntar al agente</a>
        <a class="btn btn-ghost" href="${ctx}/evidencias/descargar?id=${ev.id}"><svg class="ic"><use href="#i-bajar"/></svg>Original</a>
        <c:if test="${u.canAnalyze()}">
            <form method="post" action="${ctx}/evidencias/accion" class="inline" data-busy="Analizando ${fn:escapeXml(ev.filename)}">
                <input type="hidden" name="_csrf" value="${csrf}">
                <input type="hidden" name="id" value="${ev.id}">
                <input type="hidden" name="action" value="${parsed ? 'analyze' : 'pipeline'}">
                <button class="btn btn-cel" type="submit"><svg class="ic"><use href="#i-play"/></svg>${analyzed ? 'Reanalizar' : (parsed ? 'Analizar' : 'Parsear y analizar')}</button>
            </form>
            <c:if test="${analyzed and not empty findings}">
                <form method="post" action="${ctx}/evidencias/accion" class="inline">
                    <input type="hidden" name="_csrf" value="${csrf}">
                    <input type="hidden" name="id" value="${ev.id}">
                    <input type="hidden" name="action" value="incident">
                    <button class="btn btn-rosa" type="submit"><svg class="ic"><use href="#i-incidente"/></svg>Abrir incidente</button>
                </form>
            </c:if>
        </c:if>
    </div>
</header>

<c:if test="${ev.status == 'error'}">
    <div class="flash flash-error"><svg class="ic"><use href="#i-alerta"/></svg><span><c:out value="${ev.errorMessage}" /></span></div>
</c:if>

<dl class="facts">
    <div class="wide"><dt>SHA-256</dt><dd class="hash"><c:out value="${ev.sha256}" /></dd></div>
    <div><dt>Tipo</dt><dd><c:out value="${ev.logType}" /></dd></div>
    <div><dt>Tamaño</dt><dd>${ev.humanSize}</dd></div>
    <div><dt>Líneas</dt><dd>${ev.lineCount}</dd></div>
    <div><dt>Hallazgos</dt><dd>${ev.threatCount} <c:if test="${ev.criticalCount > 0}"><span class="sev sev-critical">${ev.criticalCount} críticos</span></c:if></dd></div>
    <div><dt>Subida por</dt><dd><c:out value="${ev.uploadUsername}" /><br><span style="color:var(--tenue);font-weight:400;font-size:12.5px">${soc:fechaSeg(ev.createdAt)}</span></dd></div>
</dl>

<div class="grid g-2 mt" style="align-items:start">
    <section>
        <div class="card-head" style="margin:6px 0 12px">
            <h2>Hallazgos</h2>
            <span class="hint">${fn:length(findings)} en total</span>
        </div>
        <c:choose>
            <c:when test="${empty findings}">
                <div class="card empty">
                    <svg class="ic"><use href="#i-hallazgo"/></svg>
                    <c:choose>
                        <c:when test="${analyzed}"><h3>Nada sospechoso</h3><p>El análisis terminó sin marcar líneas. Puede pedirle al agente una segunda lectura.</p></c:when>
                        <c:otherwise><h3>Sin analizar</h3><p>Lance el análisis para ver amenazas, técnicas MITRE y acciones de contención.</p></c:otherwise>
                    </c:choose>
                </div>
            </c:when>
            <c:otherwise>
                <c:forEach var="r" items="${findings}">
                    <article class="finding f-${r.severity}">
                        <div class="finding-head">
                            <span class="sev sev-${r.severity}">${soc:severidad(r.severity)}</span>
                            <h3><c:out value="${r.threatType}" /></h3>
                            <c:if test="${not empty r.mitreTechnique}"><span class="mitre"><c:out value="${r.mitreTechnique}" /></span></c:if>
                        </div>
                        <p><c:out value="${r.threatDescription}" /></p>
                        <c:if test="${not empty r.rawEvidence}"><div class="rawline"><c:out value="${r.rawEvidence}" /></div></c:if>
                        <c:if test="${not empty r.recommendation}"><div class="reco"><b>Qué hacer</b><c:out value="${r.recommendation}" /></div></c:if>
                        <div class="finding-meta">
                            <c:forEach var="ioc" items="${r.indicatorList}"><span class="ioc"><c:out value="${ioc}" /></span></c:forEach>
                            <span class="conf" title="Confianza">confianza <i style="--p:${r.confidencePercent}%"></i> ${r.confidencePercent}%</span>
                            <c:if test="${not empty r.parsedLogId}"><a href="#l${r.parsedLogId}">ver línea</a></c:if>
                        </div>
                    </article>
                </c:forEach>
            </c:otherwise>
        </c:choose>
    </section>

    <section class="card flush sticky">
        <div class="card-head" style="padding-bottom:14px">
            <h2>Log</h2>
            <span class="hint">
                <c:choose>
                    <c:when test="${logTotal > maxLines}">Primeras ${maxLines} de ${logTotal} líneas</c:when>
                    <c:otherwise>${logTotal} líneas</c:otherwise>
                </c:choose>
            </span>
        </div>
        <c:choose>
            <c:when test="${empty logs}">
                <div class="empty" style="border-top:1px solid var(--line)"><h3>Sin parsear</h3><p>Las líneas aparecerán aquí después del parseo.</p></div>
            </c:when>
            <c:otherwise>
                <div class="logview">
                    <c:forEach var="l" items="${logs}">
                        <div class="logline ${flagged.contains(l.id) ? 'hit' : ''}" id="l${l.id}"><span class="ln">${l.lineNumber}</span><span class="lc"><c:out value="${l.rawLine}" /></span></div>
                    </c:forEach>
                </div>
            </c:otherwise>
        </c:choose>
    </section>
</div>

<%@ include file="/WEB-INF/views/includes/bottom.jspf" %>
