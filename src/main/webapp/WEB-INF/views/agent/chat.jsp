<%@ include file="/WEB-INF/views/includes/top.jspf" %>

<header class="topbar" style="margin-bottom:18px">
    <div>
        <div class="eyebrow">Agente IA</div>
        <h1>Analista de guardia</h1>
        <p class="sub">Conoce el estado de la plataforma. Si elige una evidencia, también lee sus hallazgos y las primeras líneas del log.</p>
    </div>
    <div class="topbar-actions">
        <span class="pill" id="ai-pill"><span class="dot"></span><span>Comprobando IA…</span></span>
    </div>
</header>

<div class="agent">
    <section class="chat">
        <div class="chat-log" id="chat-log">
            <c:if test="${empty history}">
                <div class="welcome" id="welcome">
                    <div class="orb"><svg viewBox="0 0 24 24"><rect x="4" y="7" width="16" height="12" rx="3"/><path d="M12 3v4M9 13h.01M15 13h.01M9.5 16h5"/></svg></div>
                    <h2>¿Qué revisamos?</h2>
                    <p>Pregunte por una IP, pida que explique una técnica o que priorice lo que hay abierto. Las sugerencias de la derecha sirven para empezar.</p>
                </div>
            </c:if>
            <c:forEach var="m" items="${history}">
                <div class="msg ${m.role == 'user' ? 'user' : 'bot'}">
                    <div class="who">${m.role == 'user' ? soc:iniciales(empty u.fullName ? u.username : u.fullName) : 'IA'}</div>
                    <div>
                        <div class="bubble"><c:out value="${m.content}" /></div>
                        <div class="bubble-meta">${soc:fecha(m.createdAt)}<c:if test="${not empty m.evidenceId}"> · evidencia #${m.evidenceId}</c:if></div>
                    </div>
                </div>
            </c:forEach>
        </div>

        <div class="composer">
            <form id="chat-form" autocomplete="off">
                <textarea class="input" id="chat-input" name="message" rows="1" maxlength="4000"
                          placeholder="Escriba su pregunta. Enter envía, Shift+Enter hace salto de línea." required></textarea>
                <button class="btn btn-rosa" type="submit" id="chat-send"><svg class="ic"><use href="#i-enviar"/></svg>Enviar</button>
            </form>
        </div>
    </section>

    <aside class="agent-side stack">
        <section class="card">
            <div class="field">
                <label for="chat-evidence">Contexto</label>
                <select class="input" id="chat-evidence">
                    <option value="">Toda la plataforma</option>
                    <c:forEach var="e" items="${evidences}">
                        <option value="${e.id}" ${e.id == selectedEvidence ? 'selected' : ''}>#${e.id} · <c:out value="${soc:recortar(e.filename, 30)}" /></option>
                    </c:forEach>
                </select>
                <span class="help">Modelo: <span class="mono"><c:out value="${aiModel}" /></span></span>
            </div>
        </section>

        <section class="card">
            <h3 style="margin-bottom:12px">Para empezar</h3>
            <div class="prompt-list">
                <button class="prompt" type="button">Resume las amenazas críticas y dime por dónde empiezo.</button>
                <button class="prompt" type="button">¿Qué IPs debería bloquear ya y por qué?</button>
                <button class="prompt" type="button">¿Hay señales de que alguna cuenta fue comprometida?</button>
                <button class="prompt" type="button">Arma una línea de tiempo del ataque con la evidencia seleccionada.</button>
                <button class="prompt" type="button">Explica la técnica T1110 y cómo se detecta en auth.log.</button>
            </div>
        </section>

        <section class="card">
            <button class="btn btn-ghost" type="button" id="chat-clear" style="width:100%"><svg class="ic"><use href="#i-basura"/></svg>Borrar conversación</button>
        </section>
    </aside>
</div>

<%@ include file="/WEB-INF/views/includes/bottom.jspf" %>
