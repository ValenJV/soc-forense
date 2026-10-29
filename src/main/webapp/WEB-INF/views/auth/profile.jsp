<%@ include file="/WEB-INF/views/includes/top.jspf" %>

<header class="topbar">
    <div>
        <div class="eyebrow">Cuenta</div>
        <h1><c:out value="${empty u.fullName ? u.username : u.fullName}" /></h1>
        <p class="sub">Sus datos los administra un administrador. La contraseña sí puede cambiarla usted.</p>
    </div>
</header>

<div class="grid g-2">
    <section class="card">
        <div class="card-head"><h2>Datos</h2></div>
        <dl class="facts">
            <div><dt>Usuario</dt><dd><c:out value="${u.username}" /></dd></div>
            <div><dt>Rol</dt><dd><c:out value="${soc:rol(u.role)}" /></dd></div>
            <div><dt>Correo</dt><dd><c:out value="${u.email}" /></dd></div>
            <div><dt>Último acceso</dt><dd>${soc:fecha(u.lastLoginAt)}</dd></div>
        </dl>
        <p class="muted" style="color:var(--tenue);margin:16px 0 0;font-size:13px">
            <c:choose>
                <c:when test="${u.role == 'ADMIN'}">Puede gestionar usuarios, borrar evidencias e incidentes, y todo lo que hace un analista.</c:when>
                <c:when test="${u.role == 'ANALYST'}">Puede cargar evidencias, lanzar análisis, abrir incidentes y pedir planes de respuesta.</c:when>
                <c:otherwise>Puede consultar tablero, evidencias, hallazgos e incidentes, y conversar con el agente.</c:otherwise>
            </c:choose>
        </p>
    </section>

    <section class="card">
        <div class="card-head"><h2>Cambiar contraseña</h2></div>
        <form method="post" action="${ctx}/perfil" class="stack">
            <input type="hidden" name="_csrf" value="${csrf}">
            <div class="field">
                <label for="current">Contraseña actual</label>
                <input class="input" type="password" id="current" name="current" required autocomplete="current-password">
            </div>
            <div class="field">
                <label for="password">Nueva contraseña</label>
                <input class="input" type="password" id="password" name="password" minlength="8" required autocomplete="new-password">
                <span class="help">Mínimo 8 caracteres.</span>
            </div>
            <div class="field">
                <label for="password2">Repita la nueva contraseña</label>
                <input class="input" type="password" id="password2" name="password2" minlength="8" required autocomplete="new-password">
            </div>
            <div class="form-actions"><button class="btn btn-rosa" type="submit"><svg class="ic"><use href="#i-llave"/></svg>Guardar contraseña</button></div>
        </form>
    </section>
</div>

<%@ include file="/WEB-INF/views/includes/bottom.jspf" %>
