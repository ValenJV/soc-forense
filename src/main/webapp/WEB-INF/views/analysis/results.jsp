<%@ include file="/WEB-INF/views/includes/top.jspf" %>

<header class="topbar">
    <div>
        <div class="eyebrow">Análisis</div>
        <h1>Hallazgos</h1>
        <p class="sub">Todo lo que marcaron el agente y el motor de reglas, en todas las evidencias. Ordenado por gravedad.</p>
    </div>
</header>

<section class="card">
    <form method="get" action="${ctx}/hallazgos" class="searchbar">
        <div class="chips">
            <a class="chip ${empty sev ? 'on' : ''}" href="${ctx}/hallazgos">Todas<b>${total}</b></a>
            <c:forEach var="entry" items="${severityCounts}">
                <a class="chip ${sev == entry.key ? 'on' : ''}" href="${ctx}/hallazgos?sev=${entry.key}">${soc:severidad(entry.key)}<b>${entry.value}</b></a>
            </c:forEach>
        </div>
        <c:if test="${not empty sev}"><input type="hidden" name="sev" value="${fn:escapeXml(sev)}"></c:if>
        <input class="input" type="search" name="q" value="${fn:escapeXml(q)}" placeholder="IP, usuario, T1110, sudo…" style="margin-left:auto">
        <button class="btn btn-ghost" type="submit"><svg class="ic"><use href="#i-lupa"/></svg>Buscar</button>
    </form>
</section>

<section class="card flush mt">
    <c:choose>
        <c:when test="${empty findings}">
            <div class="empty"><svg class="ic"><use href="#i-hallazgo"/></svg>
                <h3>${total == 0 ? 'Todavía no hay hallazgos' : 'Ningún hallazgo coincide'}</h3>
                <p>${total == 0 ? 'Analice una evidencia para llenar esta lista.' : 'Pruebe con otro filtro o término.'}</p></div>
        </c:when>
        <c:otherwise>
            <div class="table-wrap">
                <table class="t">
                    <thead><tr><th>Severidad</th><th>Amenaza</th><th>MITRE</th><th>Indicadores</th><th>Evidencia</th><th class="num">Confianza</th></tr></thead>
                    <tbody>
                    <c:forEach var="r" items="${findings}">
                        <tr>
                            <td><span class="sev sev-${r.severity}">${soc:severidad(r.severity)}</span></td>
                            <td style="min-width:320px">
                                <div class="cell-title"><c:out value="${r.threatType}" /></div>
                                <details class="fold">
                                    <summary><c:out value="${soc:recortar(r.threatDescription, 110)}" /></summary>
                                    <p style="margin:8px 0 0"><c:out value="${r.threatDescription}" /></p>
                                    <c:if test="${not empty r.rawEvidence}"><div class="rawline"><c:out value="${r.rawEvidence}" /></div></c:if>
                                    <c:if test="${not empty r.recommendation}"><div class="finding"><div class="reco" style="margin:0"><b>Qué hacer</b><c:out value="${r.recommendation}" /></div></div></c:if>
                                </details>
                            </td>
                            <td><c:if test="${not empty r.mitreTechnique}"><span class="mitre"><c:out value="${r.mitreTechnique}" /></span></c:if></td>
                            <td><c:forEach var="ioc" items="${r.indicatorList}" end="2"><span class="ioc" style="display:inline-block;margin:2px 2px 2px 0"><c:out value="${ioc}" /></span></c:forEach></td>
                            <td><a href="${ctx}/evidencias/detalle?id=${r.evidenceId}${empty r.parsedLogId ? '' : '#l'}${r.parsedLogId}">#${r.evidenceId}</a>
                                <div class="cell-sub">${soc:hace(r.createdAt)}</div></td>
                            <td class="num"><span class="conf"><i style="--p:${r.confidencePercent}%"></i>${r.confidencePercent}%</span></td>
                        </tr>
                    </c:forEach>
                    </tbody>
                </table>
            </div>
        </c:otherwise>
    </c:choose>
</section>

<%@ include file="/WEB-INF/views/includes/bottom.jspf" %>
