<%@ include file="/WEB-INF/views/includes/top.jspf" %>

<header class="topbar">
    <div>
        <div class="eyebrow">Cadena de custodia</div>
        <h1>Evidencias</h1>
        <p class="sub">Cada archivo se guarda con su SHA-256. Si sube dos veces el mismo contenido, el sistema lo reconoce y no lo duplica.</p>
    </div>
</header>

<div class="grid g-side">
    <section class="card flush">
        <div class="card-head">
            <h2>Archivos cargados</h2>
            <input class="input" style="max-width:240px" type="search" placeholder="Filtrar por nombre o tipo" data-filter="#tabla-evidencias">
        </div>
        <c:choose>
            <c:when test="${empty evidences}">
                <div class="empty"><svg class="ic"><use href="#i-vacio"/></svg><h3>Aún no hay evidencias</h3>
                    <p>${u.canAnalyze() ? 'Use el panel de la derecha para subir el primer log.' : 'Cuando un analista suba logs, los verá aquí.'}</p></div>
            </c:when>
            <c:otherwise>
                <div class="table-wrap" style="margin-top:12px">
                    <table class="t" id="tabla-evidencias">
                        <thead><tr><th>Archivo</th><th>Estado</th><th class="num">Líneas</th><th class="num">Hallazgos</th><th></th></tr></thead>
                        <tbody>
                        <c:forEach var="e" items="${evidences}">
                            <tr>
                                <td>
                                    <a class="cell-title" href="${ctx}/evidencias/detalle?id=${e.id}"><c:out value="${e.filename}" /></a>
                                    <div class="cell-sub"><span class="mono">${e.shortHash}…</span> · <c:out value="${e.logType}" /> · ${e.humanSize} · <c:out value="${e.uploadUsername}" />, ${soc:hace(e.createdAt)}</div>
                                </td>
                                <td><span class="st st-${e.status}">${soc:estadoEvidencia(e.status)}</span></td>
                                <td class="num">${e.lineCount}</td>
                                <td class="num">${e.threatCount}<c:if test="${e.criticalCount > 0}"> <span class="sev sev-critical">${e.criticalCount}</span></c:if></td>
                                <td>
                                    <div class="actions">
                                        <c:if test="${u.canAnalyze()}">
                                            <form method="post" action="${ctx}/evidencias/accion" class="inline" data-busy="Analizando ${fn:escapeXml(e.filename)}">
                                                <input type="hidden" name="_csrf" value="${csrf}">
                                                <input type="hidden" name="id" value="${e.id}">
                                                <input type="hidden" name="action" value="pipeline">
                                                <button class="btn btn-cel btn-sm" type="submit" title="Parsear y analizar"><svg class="ic"><use href="#i-play"/></svg>${e.status == 'analyzed' ? 'Reanalizar' : 'Analizar'}</button>
                                            </form>
                                        </c:if>
                                        <a class="btn btn-ghost btn-sm" href="${ctx}/evidencias/descargar?id=${e.id}" title="Descargar original"><svg class="ic"><use href="#i-bajar"/></svg></a>
                                        <c:if test="${u.admin}">
                                            <form method="post" action="${ctx}/evidencias/accion" class="inline" data-confirm="Se borrará ${fn:escapeXml(e.filename)} con sus líneas y hallazgos. ¿Continuar?">
                                                <input type="hidden" name="_csrf" value="${csrf}">
                                                <input type="hidden" name="id" value="${e.id}">
                                                <input type="hidden" name="action" value="delete">
                                                <button class="btn btn-peligro btn-sm" type="submit" title="Eliminar"><svg class="ic"><use href="#i-basura"/></svg></button>
                                            </form>
                                        </c:if>
                                    </div>
                                </td>
                            </tr>
                        </c:forEach>
                        </tbody>
                    </table>
                </div>
            </c:otherwise>
        </c:choose>
    </section>

    <aside id="subir">
        <c:choose>
            <c:when test="${u.canAnalyze()}">
                <section class="card">
                    <div class="card-head"><h2>Subir log</h2></div>
                    <form method="post" action="${ctx}/evidencias/subir?_csrf=${csrf}" enctype="multipart/form-data" data-busy="Procesando evidencia" data-upload>
                        <label class="drop" id="drop">
                            <input type="file" name="file" id="file" required>
                            <svg class="ic"><use href="#i-subir"/></svg>
                            <strong>Arrastre el archivo aquí</strong>
                            <span>o haga clic para elegirlo · hasta ${maxUploadMb} MB</span>
                            <div class="picked" id="picked"></div>
                        </label>
                        <div style="margin-top:14px">
                            <label class="check"><input type="checkbox" name="auto" checked> Parsear y analizar al subir</label>
                        </div>
                        <div class="form-actions"><button class="btn btn-rosa" type="submit" style="width:100%"><svg class="ic"><use href="#i-subir"/></svg>Subir evidencia</button></div>
                    </form>
                </section>
                <section class="card">
                    <h3>Formatos que reconoce</h3>
                    <p style="color:var(--tenue);font-size:13px;margin:8px 0 0">
                        auth.log y secure (SSH, PAM), sudo, auditd, syslog clásico o con fecha ISO, y accesos de Apache o Nginx.
                        Otros formatos también se aceptan: se guardan línea por línea y el agente los lee igual.
                    </p>
                </section>
            </c:when>
            <c:otherwise>
                <section class="card">
                    <h3>Solo lectura</h3>
                    <p style="color:var(--tenue);font-size:13px;margin:8px 0 0">Su rol es Observador. Para subir o analizar logs necesita el rol Analista.</p>
                </section>
            </c:otherwise>
        </c:choose>
    </aside>
</div>

<%@ include file="/WEB-INF/views/includes/bottom.jspf" %>
