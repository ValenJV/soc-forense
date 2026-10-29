<%@ include file="/WEB-INF/views/includes/top.jspf" %>
<c:set var="inc" value="${incident}" />

<header class="topbar">
    <div>
        <div class="eyebrow"><a href="${ctx}/incidentes">Incidentes</a> / #${inc.id}</div>
        <h1><c:out value="${inc.title}" /></h1>
        <div style="display:flex;gap:10px;align-items:center;margin-top:10px;flex-wrap:wrap">
            <span class="sev sev-${inc.severity}">${soc:severidad(inc.severity)}</span>
            <span class="st st-${inc.status}">${soc:estadoIncidente(inc.status)}</span>
            <span style="color:var(--apagado);font-size:12.5px">abierto por <c:out value="${inc.createdByUsername}" /> el ${soc:fecha(inc.createdAt)}</span>
        </div>
    </div>
    <c:if test="${u.canAnalyze()}">
        <div class="topbar-actions">
            <c:forEach var="s" items="${statuses}">
                <c:if test="${s != inc.status}">
                    <form method="post" action="${ctx}/incidentes" class="inline">
                        <input type="hidden" name="_csrf" value="${csrf}">
                        <input type="hidden" name="action" value="status">
                        <input type="hidden" name="id" value="${inc.id}">
                        <input type="hidden" name="status" value="${s}">
                        <button class="btn btn-ghost btn-sm" type="submit"><c:choose><c:when test="${s == 'open'}">Reabrir</c:when><c:when test="${s == 'investigating'}">Investigar</c:when><c:when test="${s == 'contained'}">Dar por contenido</c:when><c:otherwise>Cerrar incidente</c:otherwise></c:choose></button>
                    </form>
                </c:if>
            </c:forEach>
        </div>
    </c:if>
</header>

<div class="grid g-side" style="align-items:start">
    <div>
        <section class="card">
            <div class="card-head">
                <h2>Plan de respuesta</h2>
                <c:if test="${u.canAnalyze()}">
                    <form method="post" action="${ctx}/incidentes" class="inline" data-busy="El agente está redactando el plan">
                        <input type="hidden" name="_csrf" value="${csrf}">
                        <input type="hidden" name="action" value="plan">
                        <input type="hidden" name="id" value="${inc.id}">
                        <button class="btn ${empty inc.responsePlan ? 'btn-rosa' : 'btn-cel'} btn-sm" type="submit"><svg class="ic"><use href="#i-rayo"/></svg>${empty inc.responsePlan ? 'Generar plan' : 'Regenerar'}</button>
                    </form>
                </c:if>
            </div>
            <c:choose>
                <c:when test="${empty inc.responsePlan}">
                    <div class="empty" style="padding:30px 10px"><svg class="ic"><use href="#i-agente"/></svg>
                        <h3>Sin plan todavía</h3>
                        <p>El agente toma los hallazgos de la evidencia vinculada y propone contención, erradicación, recuperación y lecciones aprendidas.</p></div>
                </c:when>
                <c:otherwise>
                    <div class="plan"><c:out value="${inc.responsePlan}" /></div>
                    <p style="color:var(--apagado);font-size:12px;margin:10px 0 0">Última actualización ${soc:hace(inc.updatedAt)}. Revise el plan antes de ejecutarlo.</p>
                </c:otherwise>
            </c:choose>
        </section>

        <section class="mt">
            <div class="card-head" style="margin-bottom:12px">
                <h2>Hallazgos vinculados</h2>
                <c:if test="${not empty evidence}"><a class="hint" href="${ctx}/evidencias/detalle?id=${evidence.id}"><c:out value="${evidence.filename}" /></a></c:if>
            </div>
            <c:choose>
                <c:when test="${empty inc.evidenceId}">
                    <div class="card empty" style="padding:26px"><p>Este incidente no tiene evidencia vinculada.</p></div>
                </c:when>
                <c:when test="${empty evidence}">
                    <div class="card empty" style="padding:26px"><p>La evidencia #${inc.evidenceId} fue eliminada.</p></div>
                </c:when>
                <c:when test="${empty findings}">
                    <div class="card empty" style="padding:26px"><p>La evidencia vinculada no tiene hallazgos.</p></div>
                </c:when>
                <c:otherwise>
                    <c:forEach var="r" items="${findings}" end="19">
                        <article class="finding f-${r.severity}">
                            <div class="finding-head">
                                <span class="sev sev-${r.severity}">${soc:severidad(r.severity)}</span>
                                <h3><c:out value="${r.threatType}" /></h3>
                                <c:if test="${not empty r.mitreTechnique}"><span class="mitre"><c:out value="${r.mitreTechnique}" /></span></c:if>
                            </div>
                            <p><c:out value="${r.threatDescription}" /></p>
                            <c:if test="${not empty r.rawEvidence}"><div class="rawline"><c:out value="${r.rawEvidence}" /></div></c:if>
                        </article>
                    </c:forEach>
                    <c:if test="${fn:length(findings) > 20}"><p style="color:var(--tenue);font-size:13px">Y ${fn:length(findings) - 20} más en la evidencia.</p></c:if>
                </c:otherwise>
            </c:choose>
        </section>
    </div>

    <aside>
        <section class="card">
            <div class="card-head"><h2>Ficha</h2></div>
            <c:choose>
                <c:when test="${u.canAnalyze()}">
                    <form method="post" action="${ctx}/incidentes" class="stack">
                        <input type="hidden" name="_csrf" value="${csrf}">
                        <input type="hidden" name="action" value="update">
                        <input type="hidden" name="id" value="${inc.id}">
                        <div class="field"><label for="e-title">Título</label><input class="input" id="e-title" name="title" value="${fn:escapeXml(inc.title)}" required maxlength="200"></div>
                        <div class="field"><label for="e-sev">Severidad</label>
                            <select class="input" id="e-sev" name="severity">
                                <c:forEach var="s" items="${['critical','high','medium','low']}"><option value="${s}" ${s == inc.severity ? 'selected' : ''}>${soc:severidad(s)}</option></c:forEach>
                            </select></div>
                        <div class="field"><label for="e-st">Estado</label>
                            <select class="input" id="e-st" name="status">
                                <c:forEach var="s" items="${statuses}"><option value="${s}" ${s == inc.status ? 'selected' : ''}>${soc:estadoIncidente(s)}</option></c:forEach>
                            </select></div>
                        <div class="field"><label for="e-as">Responsable</label><input class="input" id="e-as" name="assignedTo" value="${fn:escapeXml(inc.assignedTo)}"></div>
                        <div class="field"><label for="e-sum">Resumen</label><textarea class="input" id="e-sum" name="summary" rows="6"><c:out value="${inc.summary}" /></textarea></div>
                        <div class="form-actions"><button class="btn btn-rosa" type="submit">Guardar cambios</button></div>
                    </form>
                </c:when>
                <c:otherwise>
                    <dl class="facts" style="grid-template-columns:1fr">
                        <div><dt>Responsable</dt><dd><c:out value="${empty inc.assignedTo ? 'Sin asignar' : inc.assignedTo}" /></dd></div>
                        <div><dt>Resumen</dt><dd style="font-weight:400"><c:out value="${inc.summary}" /></dd></div>
                    </dl>
                </c:otherwise>
            </c:choose>
        </section>

        <section class="card">
            <a class="btn btn-cel" style="width:100%" href="${ctx}/agente${empty inc.evidenceId ? '' : '?evidencia='}${inc.evidenceId}"><svg class="ic"><use href="#i-agente"/></svg>Consultar al agente</a>
            <c:if test="${u.admin}">
                <form method="post" action="${ctx}/incidentes" data-confirm="¿Eliminar el incidente #${inc.id}? No se puede deshacer." style="margin-top:10px">
                    <input type="hidden" name="_csrf" value="${csrf}">
                    <input type="hidden" name="action" value="delete">
                    <input type="hidden" name="id" value="${inc.id}">
                    <button class="btn btn-peligro" style="width:100%" type="submit"><svg class="ic"><use href="#i-basura"/></svg>Eliminar incidente</button>
                </form>
            </c:if>
        </section>
    </aside>
</div>

<%@ include file="/WEB-INF/views/includes/bottom.jspf" %>
