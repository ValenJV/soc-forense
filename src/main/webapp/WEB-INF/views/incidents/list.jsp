<%@ include file="/WEB-INF/views/includes/top.jspf" %>

<header class="topbar">
    <div>
        <div class="eyebrow">Respuesta</div>
        <h1>Incidentes</h1>
        <p class="sub">Un incidente agrupa hallazgos que requieren seguimiento. Desde el detalle, el agente redacta el plan de contención, erradicación y recuperación.</p>
    </div>
    <c:if test="${u.canAnalyze()}">
        <div class="topbar-actions">
            <button class="btn btn-rosa" type="button" data-open="#nuevo-incidente"><svg class="ic"><use href="#i-mas"/></svg>Nuevo incidente</button>
        </div>
    </c:if>
</header>

<section class="card">
    <div class="chips">
        <a class="chip ${empty estado ? 'on' : ''}" href="${ctx}/incidentes">Todos</a>
        <c:forEach var="entry" items="${statusCounts}">
            <a class="chip ${estado == entry.key ? 'on' : ''}" href="${ctx}/incidentes?estado=${entry.key}">${soc:estadoIncidente(entry.key)}<b>${entry.value}</b></a>
        </c:forEach>
    </div>
</section>

<section class="card flush mt">
    <c:choose>
        <c:when test="${empty incidents}">
            <div class="empty"><svg class="ic"><use href="#i-incidente"/></svg><h3>No hay incidentes en esta vista</h3>
                <p>Abra uno desde una evidencia analizada o con el botón «Nuevo incidente».</p></div>
        </c:when>
        <c:otherwise>
            <div class="table-wrap">
                <table class="t">
                    <thead><tr><th>Incidente</th><th>Severidad</th><th>Estado</th><th>Responsable</th><th>Plan</th><th>Actualizado</th></tr></thead>
                    <tbody>
                    <c:forEach var="i" items="${incidents}">
                        <tr class="${i.status == 'closed' ? 'row-off' : ''}">
                            <td><a class="cell-title" href="${ctx}/incidentes/detalle?id=${i.id}"><c:out value="${i.title}" /></a>
                                <div class="cell-sub">#${i.id} · abierto por <c:out value="${i.createdByUsername}" /><c:if test="${not empty i.evidenceId}"> · evidencia #${i.evidenceId}</c:if></div></td>
                            <td><span class="sev sev-${i.severity}">${soc:severidad(i.severity)}</span></td>
                            <td><span class="st st-${i.status}">${soc:estadoIncidente(i.status)}</span></td>
                            <td class="muted"><c:out value="${empty i.assignedTo ? 'Sin asignar' : i.assignedTo}" /></td>
                            <td>${empty i.responsePlan ? '<span class="st">Pendiente</span>' : '<span class="st st-contained">Listo</span>'}</td>
                            <td class="muted">${soc:hace(i.updatedAt)}</td>
                        </tr>
                    </c:forEach>
                    </tbody>
                </table>
            </div>
        </c:otherwise>
    </c:choose>
</section>

<c:if test="${u.canAnalyze()}">
<dialog class="modal" id="nuevo-incidente">
    <h2 style="margin-bottom:16px">Nuevo incidente</h2>
    <form method="post" action="${ctx}/incidentes">
        <input type="hidden" name="_csrf" value="${csrf}">
        <input type="hidden" name="action" value="create">
        <div class="form-grid">
            <div class="field full"><label for="n-title">Título</label><input class="input" id="n-title" name="title" required maxlength="200" placeholder="Acceso SSH sospechoso en web01"></div>
            <div class="field"><label for="n-sev">Severidad</label>
                <select class="input" id="n-sev" name="severity">
                    <option value="critical">Crítica</option><option value="high" selected>Alta</option>
                    <option value="medium">Media</option><option value="low">Baja</option>
                </select></div>
            <div class="field"><label for="n-assigned">Responsable</label><input class="input" id="n-assigned" name="assignedTo" value="${fn:escapeXml(u.username)}"></div>
            <div class="field full"><label for="n-ev">Evidencia relacionada</label>
                <select class="input" id="n-ev" name="evidenceId">
                    <option value="">Ninguna</option>
                    <c:forEach var="e" items="${evidences}"><option value="${e.id}">#${e.id} · <c:out value="${e.filename}" /> (${e.threatCount} hallazgos)</option></c:forEach>
                </select>
                <span class="help">Si la vincula, el plan de respuesta usará sus hallazgos.</span></div>
            <div class="field full"><label for="n-sum">Resumen</label><textarea class="input" id="n-sum" name="summary" placeholder="Qué se observó, desde cuándo, qué sistemas toca"></textarea></div>
        </div>
        <div class="form-actions">
            <button class="btn btn-ghost" type="button" data-close>Cancelar</button>
            <button class="btn btn-rosa" type="submit">Registrar</button>
        </div>
    </form>
</dialog>
</c:if>

<%@ include file="/WEB-INF/views/includes/bottom.jspf" %>
