<!DOCTYPE html>
<html lang="es">
<head>
<%@ include file="/WEB-INF/views/includes/head.jspf" %>
<title>Iniciar sesión · SOC Forense</title>
</head>
<body>
<div class="auth">
    <section class="auth-art">
        <div class="brand" style="padding:0">
            <span class="brand-mark"><svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="6"/><path d="M12 2v5M12 17v5M2 12h5M17 12h5"/></svg></span>
            <span class="brand-name">SOC Forense<small>Respuesta a incidentes</small></span>
        </div>

        <div>
            <div class="eyebrow">Análisis forense de logs</div>
            <h1>Del log crudo al <em>plan de respuesta</em>.</h1>
            <p>Suba un auth.log, un audit.log o los accesos de su servidor web. Cada archivo queda sellado con su SHA-256, cada línea se estructura y el agente marca lo que merece atención, con la técnica MITRE ATT&amp;CK que le corresponde.</p>
        </div>

        <div class="tail" aria-hidden="true">
            <div>Mar 14 03:12:07 web01 sshd[2291]: Failed password for root from 203.0.113.45 port 51122</div>
            <div>Mar 14 03:12:09 web01 sshd[2291]: Failed password for root from 203.0.113.45 port 51130</div>
            <div class="hit">Mar 14 03:12:11 web01 sshd[2294]: Accepted password for root from 203.0.113.45 port 51134</div>
            <div>Mar 14 03:13:40 web01 sudo: deploy : TTY=pts/0 ; USER=root ; COMMAND=/usr/sbin/useradd backup2</div>
            <div class="ok">→ T1110 fuerza bruta · crítica · 0.92</div>
        </div>
    </section>

    <section class="auth-form">
        <div class="auth-box">
            <h2>Iniciar sesión</h2>
            <p>Use la cuenta que le asignó el administrador del SOC.</p>

            <c:if test="${not empty error}">
                <div class="flash flash-error"><span><c:out value="${error}" /></span></div>
            </c:if>
            <c:if test="${not empty info}">
                <div class="flash flash-ok"><span><c:out value="${info}" /></span></div>
            </c:if>

            <form method="post" action="${ctx}/login" autocomplete="on">
                <input type="hidden" name="_csrf" value="${csrf}">
                <div class="field">
                    <label for="username">Usuario</label>
                    <input class="input" id="username" name="username" value="${fn:escapeXml(username)}" required autofocus autocomplete="username">
                </div>
                <div class="field">
                    <label for="password">Contraseña</label>
                    <input class="input" id="password" name="password" type="password" required autocomplete="current-password">
                </div>
                <button class="btn btn-rosa" type="submit">Entrar</button>
            </form>

            <div class="seed">¿Primer arranque? Las cuentas iniciales están en el <code>README.md</code> del proyecto. Cambie la contraseña apenas entre.</div>
        </div>
    </section>
</div>
</body>
</html>
