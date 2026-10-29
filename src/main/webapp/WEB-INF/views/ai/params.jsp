<%@ include file="/WEB-INF/views/includes/top.jspf" %>
<c:set var="m" value="${metrics}" />

<header class="topbar">
<div>
<div class="eyebrow">Plataforma IA</div>
<h1>Parámetros generales</h1>
<p class="sub">Cómo se configura el modelo de lenguaje en cada tarea y con qué criterios opera la plataforma: datos, rendimiento, precisión, recursos y costos.</p>
</div>
<div class="topbar-actions">
<span class="pill"><c:out value="${ops.enabled ? 'IA habilitada' : 'IA deshabilitada'}" /></span>
</div>
</header>

<section class="card flush">
<div class="card-head">
<h2>1. Parámetros de inferencia por tarea</h2>
<span class="hint">application.properties · ai.&lt;perfil&gt;.&lt;parámetro&gt;</span>
</div>
<div class="table-wrap" style="margin-top:12px">
<table class="t">
<thead><tr><th>Parámetro</th><th>Qué controla</th>
<th class="num">Análisis de logs</th><th class="num">Agente (chat)</th><th class="num">Plan de respuesta</th></tr></thead>
<tbody>
<tr><td class="cell-title">Temperatura</td><td class="muted">Aleatoriedad. Baja = respuestas deterministas y repetibles; alta = más creativas.</td>
<c:forEach var="p" items="${profiles}"><td class="num mono">${p.temperature}</td></c:forEach></tr>
<tr><td class="cell-title">Top-p</td><td class="muted">Muestreo de núcleo: solo palabras cuya probabilidad acumulada llega a este porcentaje.</td>
<c:forEach var="p" items="${profiles}"><td class="num mono">${p.topP}</td></c:forEach></tr>
<tr><td class="cell-title">Top-k</td><td class="muted">Solo las k palabras más probables en cada paso (0 = no se envía).</td>
<c:forEach var="p" items="${profiles}"><td class="num mono">${p.topK}</td></c:forEach></tr>
<tr><td class="cell-title">Máximo de tokens</td><td class="muted">Longitud máxima de la respuesta (0 = sin límite explícito).</td>
<c:forEach var="p" items="${profiles}"><td class="num mono">${p.maxTokens}</td></c:forEach></tr>
<tr><td class="cell-title">Penalización por frecuencia</td><td class="muted">Castiga repetir las mismas palabras en respuestas largas.</td>
<c:forEach var="p" items="${profiles}"><td class="num mono">${p.frequencyPenalty}</td></c:forEach></tr>
<tr><td class="cell-title">Penalización por presencia</td><td class="muted">Castiga volver a temas ya mencionados; empuja a cubrir ideas nuevas.</td>
<c:forEach var="p" items="${profiles}"><td class="num mono">${p.presencePenalty}</td></c:forEach></tr>
</tbody>
</table>
</div>
<p style="color:var(--apagado);font-size:12.5px;margin:12px 22px 18px">
El análisis usa temperatura mínima y penalizaciones en 0 porque debe devolver JSON estricto y repetible (el JSON repite claves a propósito).
El chat permite algo de variedad y evita repetir frases. El plan de respuesta redacta con más libertad dentro de una estructura fija.
</p>
</section>

<div class="grid g-2 mt">
<section class="card">
<div class="card-head"><h2>Ventana de contexto</h2><span class="hint">ai.context.window</span></div>
<div class="grid g-2">
<div class="card metric"><div class="k">Tokens máximos</div><div class="v">${context.window}</div><div class="foot">Entrada + salida por petición</div></div>
<div class="card metric"><div class="k">Margen de seguridad</div><div class="v">${context.margin}</div><div class="foot">ai.context.margin</div></div>
</div>
<p style="color:var(--apagado);font-size:12.5px;margin:14px 0 0">
Antes de cada llamada se estima el tamaño (~4 caracteres por token). Si no cabe, se descartan los mensajes más antiguos del historial
(máx. ${context.history}), luego se recorta el contexto más largo y por último se reduce el máximo de tokens. Debe coincidir con el
<i>Context Length</i> configurado en LM Studio.
</p>
</section>

<section class="card">
<div class="card-head"><h2>2.1 Gestión y calidad de datos (RAG)</h2><span class="hint">ai.rag.lines · ai.max.lines</span></div>
<ul style="margin:0;padding-left:18px;line-height:1.7">
<li><b>Ingesta y cadena de custodia:</b> cada log se sella con SHA-256 y se guarda fuera de la carpeta temporal.</li>
<li><b>Transformación:</b> el parser estructura cada línea (fecha, host, servicio, usuario, IP, acción).</li>
<li><b>Fundamentación del chat:</b> estado real de la plataforma, hallazgos de la evidencia y las <b>${context.ragLines}</b> líneas más relevantes para la pregunta (recuperación por IPs, usuarios y términos).</li>
<li><b>Análisis:</b> bloques de ${context.chunk} líneas; en logs grandes solo se envían hasta ${context.maxLines} líneas con señales de ataque.</li>
</ul>
</section>
</div>

<section class="card flush mt">
<div class="card-head">
<h2>2.2 Rendimiento y precisión</h2>
<span class="hint">Desde el último arranque (${m.uptimeMinutes} min)</span>
</div>
<div class="table-wrap" style="margin-top:12px">
<table class="t">
<thead><tr><th>Tarea</th><th class="num">Llamadas</th><th class="num">Éxito</th><th class="num">Latencia media</th><th class="num">p95</th><th class="num">Máx.</th>
<th class="num">Tokens entrada</th><th class="num">Tokens salida</th><th class="num">Cortadas por máx. tokens</th><th class="num">Ajustes de contexto</th></tr></thead>
<tbody>
<c:forEach var="r" items="${m.profiles}">
<tr>
<td class="cell-title"><c:choose><c:when test="${r.profile == 'analisis'}">Análisis de logs</c:when><c:when test="${r.profile == 'plan'}">Plan de respuesta</c:when><c:otherwise>Agente (chat)</c:otherwise></c:choose></td>
<td class="num">${r.calls}</td><td class="num">${r.successRate}</td><td class="num">${r.avgLatency}</td><td class="num">${r.p95Latency}</td><td class="num">${r.maxLatency}</td>
<td class="num">${r.promptTokens}</td><td class="num">${r.completionTokens}</td><td class="num">${r.truncatedOutput}</td><td class="num">${r.contextTrims}</td>
</tr>
</c:forEach>
</tbody>
</table>
</div>
<div class="grid g-2" style="padding:0 22px 22px;margin-top:14px">
<div class="card metric"><div class="k">Concordancia IA–reglas</div><div class="v">${m.agreementRate}</div>
<div class="foot">${m.rulesConfirmed} hallazgos de reglas confirmados por la IA · ${m.aiAdded} nuevos aportados por la IA · ${m.analysesWithAi} análisis</div></div>
<div class="card metric"><div class="k">Tasa de éxito global</div><div class="v">${m.successRate}</div>
<div class="foot">${m.totalCalls} llamadas. Si la IA falla, el sistema sigue con el motor de reglas.</div></div>
</div>
</section>

<section class="card mt">
<div class="card-head"><h2>2.3 Escalabilidad, recursos y costos</h2><span class="hint">ai.max.concurrent · ai.cost.*</span></div>
<div class="grid g-2">
<div class="card metric"><div class="k">Consultas simultáneas</div><div class="v">${ops.inUse} / ${ops.maxConcurrent}</div>
<div class="foot">Espera máx. en cola ${ops.queueWait} s · rechazadas por saturación: ${m.rejectedBusy}</div></div>
<div class="card metric"><div class="k">Costo estimado (USD)</div><div class="v">${m.estimatedCost}</div>
<div class="foot">Tarifa por millón de tokens: entrada ${ops.priceIn} · salida ${ops.priceOut}${ops.local ? ' (modelo local: sin costo por token)' : ''}</div></div>
</div>
<ul style="margin:14px 0 0;padding-left:18px;line-height:1.7">
<li><b>Servidor:</b> <span class="mono"><c:out value="${ops.baseUrl}" /></span> · modelo <span class="mono"><c:out value="${ops.model}" /></span> · tiempo máximo ${ops.timeout} s.</li>
<li><b>TCO:</b> un modelo local no cobra por token; su costo es el equipo (RAM/GPU) y la energía. En la nube, el costo crece con los tokens: configure la tarifa para estimarlo.</li>
<li><b>Sin bloqueo de proveedor:</b> se usa la API estándar compatible con OpenAI. Pasar de LM Studio a Ollama, vLLM o un proveedor en la nube es cambiar <span class="mono">ai.base.url</span> y <span class="mono">ai.model</span>, sin tocar código.</li>
<li><b>Resiliencia:</b> reintentos con espera creciente, sin repetir peticiones que ya agotaron el tiempo, y respaldo con el motor de reglas.</li>
</ul>
</section>

<%@ include file="/WEB-INF/views/includes/bottom.jspf" %>
