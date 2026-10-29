<!DOCTYPE html>
<html lang="es">
<head>
<%@ include file="/WEB-INF/views/includes/head.jspf" %>
<title>Sin permiso · SOC Forense</title>
</head>
<body>
<div class="errpage">
    <div class="errbox">
        <div class="errcode">403</div>
        <h1 style="margin:10px 0 8px">Sin permiso</h1>
        <p>Su rol no permite esta acción. Si cree que debería tener acceso, pídaselo a un administrador.</p>
        <div style="display:flex;gap:10px;justify-content:center;margin-top:22px">
            <a class="btn btn-rosa" href="${ctx}/tablero">Ir al tablero</a>
            <a class="btn btn-ghost" href="javascript:history.back()">Volver</a>
        </div>
    </div>
</div>
</body>
</html>
