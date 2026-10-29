<%@ include file="/WEB-INF/views/includes/top.jspf" %>

<header class="topbar">
    <div>
        <div class="eyebrow">Administración</div>
        <h1>Usuarios</h1>
        <p class="sub">${total} cuentas. Los cambios de rol y las desactivaciones se aplican en la siguiente acción del usuario, sin esperar a que cierre sesión.</p>
    </div>
    <div class="topbar-actions">
        <form method="get" action="${ctx}/admin/usuarios" class="searchbar">
            <input class="input" type="search" name="q" value="${fn:escapeXml(q)}" placeholder="Usuario, nombre o correo">
            <button class="btn btn-ghost" type="submit"><svg class="ic"><use href="#i-lupa"/></svg>Buscar</button>
        </form>
    </div>
</header>

<div class="grid g-side" style="align-items:start">
    <section class="card flush">
        <c:choose>
            <c:when test="${empty users}">
                <div class="empty"><svg class="ic"><use href="#i-usuarios"/></svg><h3>Sin resultados</h3><p>Nadie coincide con «<c:out value="${q}" />».</p></div>
            </c:when>
            <c:otherwise>
                <div class="table-wrap">
                    <table class="t">
                        <thead><tr><th>Usuario</th><th>Rol</th><th>Estado</th><th>Último acceso</th><th></th></tr></thead>
                        <tbody>
                        <c:forEach var="x" items="${users}">
                            <tr class="${x.active ? '' : 'row-off'}">
                                <td>
                                    <div style="display:flex;gap:10px;align-items:center">
                                        <span class="avatar"><c:out value="${soc:iniciales(empty x.fullName ? x.username : x.fullName)}" /></span>
                                        <div>
                                            <div class="cell-title"><c:out value="${x.username}" /><c:if test="${x.id == u.id}"> <span class="pill" style="font-size:10.5px;padding:0 7px">usted</span></c:if></div>
                                            <div class="cell-sub"><c:out value="${x.fullName}" /> · <c:out value="${x.email}" /></div>
                                        </div>
                                    </div>
                                </td>
                                <td><span class="pill ${x.role == 'ADMIN' ? 'off' : (x.role == 'ANALYST' ? 'live' : '')}">${soc:rol(x.role)}</span></td>
                                <td><span class="st ${x.active ? 'st-contained' : 'st-closed'}">${x.active ? 'Activo' : 'Inactivo'}</span></td>
                                <td class="muted nowrap">${soc:hace(x.lastLoginAt)}</td>
                                <td>
                                    <div class="actions">
                                        <a class="btn btn-ghost btn-sm" href="${ctx}/admin/usuarios?editar=${x.id}" title="Editar"><svg class="ic"><use href="#i-lapiz"/></svg></a>
                                        <c:if test="${x.id != u.id}">
                                            <form method="post" action="${ctx}/admin/usuarios" class="inline">
                                                <input type="hidden" name="_csrf" value="${csrf}">
                                                <input type="hidden" name="action" value="toggle">
                                                <input type="hidden" name="id" value="${x.id}">
                                                <button class="btn btn-ghost btn-sm" type="submit">${x.active ? 'Desactivar' : 'Activar'}</button>
                                            </form>
                                            <form method="post" action="${ctx}/admin/usuarios" class="inline" data-confirm="¿Eliminar la cuenta ${fn:escapeXml(x.username)}? Se borra también su historial con el agente.">
                                                <input type="hidden" name="_csrf" value="${csrf}">
                                                <input type="hidden" name="action" value="delete">
                                                <input type="hidden" name="id" value="${x.id}">
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

    <aside class="stack">
        <c:choose>
            <c:when test="${not empty editing}">
                <section class="card">
                    <div class="card-head">
                        <h2>Editar <c:out value="${editing.username}" /></h2>
                        <a class="hint" href="${ctx}/admin/usuarios">Cancelar</a>
                    </div>
                    <form method="post" action="${ctx}/admin/usuarios" class="stack">
                        <input type="hidden" name="_csrf" value="${csrf}">
                        <input type="hidden" name="action" value="update">
                        <input type="hidden" name="id" value="${editing.id}">
                        <div class="field"><label for="u-name">Nombre completo</label><input class="input" id="u-name" name="fullName" value="${fn:escapeXml(editing.fullName)}"></div>
                        <div class="field"><label for="u-mail">Correo</label><input class="input" id="u-mail" name="email" type="email" value="${fn:escapeXml(editing.email)}" required></div>
                        <div class="field"><label for="u-role">Rol</label>
                            <select class="input" id="u-role" name="role">
                                <c:forEach var="r" items="${roles}"><option value="${r}" ${r == editing.role ? 'selected' : ''}>${soc:rol(r)}</option></c:forEach>
                            </select></div>
                        <label class="check"><input type="checkbox" name="active" ${editing.active ? 'checked' : ''}> Cuenta activa</label>
                        <div class="form-actions"><button class="btn btn-rosa" type="submit">Guardar</button></div>
                    </form>
                </section>
                <section class="card">
                    <div class="card-head"><h2>Restablecer contraseña</h2></div>
                    <form method="post" action="${ctx}/admin/usuarios" class="stack">
                        <input type="hidden" name="_csrf" value="${csrf}">
                        <input type="hidden" name="action" value="password">
                        <input type="hidden" name="id" value="${editing.id}">
                        <div class="field"><label for="p1">Nueva contraseña</label><input class="input" id="p1" name="password" type="password" minlength="8" required autocomplete="new-password"></div>
                        <div class="field"><label for="p2">Repetir</label><input class="input" id="p2" name="password2" type="password" minlength="8" required autocomplete="new-password"></div>
                        <div class="form-actions"><button class="btn btn-cel" type="submit"><svg class="ic"><use href="#i-llave"/></svg>Restablecer</button></div>
                    </form>
                </section>
            </c:when>
            <c:otherwise>
                <section class="card">
                    <div class="card-head"><h2>Nueva cuenta</h2></div>
                    <form method="post" action="${ctx}/admin/usuarios" class="stack">
                        <input type="hidden" name="_csrf" value="${csrf}">
                        <input type="hidden" name="action" value="create">
                        <div class="field"><label for="c-user">Usuario</label><input class="input" id="c-user" name="username" required minlength="3" pattern="[A-Za-z0-9._\-]+" autocomplete="off">
                            <span class="help">Letras, números, punto, guion o guion bajo.</span></div>
                        <div class="field"><label for="c-name">Nombre completo</label><input class="input" id="c-name" name="fullName"></div>
                        <div class="field"><label for="c-mail">Correo</label><input class="input" id="c-mail" name="email" type="email" required></div>
                        <div class="field"><label for="c-role">Rol</label>
                            <select class="input" id="c-role" name="role">
                                <option value="ANALYST">Analista</option>
                                <option value="VIEWER">Observador</option>
                                <option value="ADMIN">Administrador</option>
                            </select></div>
                        <div class="field"><label for="c-p1">Contraseña</label><input class="input" id="c-p1" name="password" type="password" minlength="8" required autocomplete="new-password"></div>
                        <div class="field"><label for="c-p2">Repetir contraseña</label><input class="input" id="c-p2" name="password2" type="password" minlength="8" required autocomplete="new-password"></div>
                        <label class="check"><input type="checkbox" name="active" checked> Activa desde ya</label>
                        <div class="form-actions"><button class="btn btn-rosa" type="submit"><svg class="ic"><use href="#i-mas"/></svg>Crear cuenta</button></div>
                    </form>
                </section>
            </c:otherwise>
        </c:choose>

        <section class="card">
            <h3>Qué puede hacer cada rol</h3>
            <table class="t" style="margin-top:10px;font-size:13px">
                <tbody>
                    <tr><td><b>Administrador</b></td><td class="muted">Todo, más usuarios y borrado de evidencias e incidentes.</td></tr>
                    <tr><td><b>Analista</b></td><td class="muted">Sube y analiza logs, gestiona incidentes, pide planes.</td></tr>
                    <tr><td><b>Observador</b></td><td class="muted">Consulta y conversa con el agente. No modifica nada.</td></tr>
                </tbody>
            </table>
            <p style="color:var(--apagado);font-size:12px;margin:12px 0 0">El sistema no permite quedarse sin un administrador activo.</p>
        </section>
    </aside>
</div>

<%@ include file="/WEB-INF/views/includes/bottom.jspf" %>
