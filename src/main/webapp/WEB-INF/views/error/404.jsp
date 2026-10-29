<!DOCTYPE html>
<html lang="es">
<head>
<%@ include file="/WEB-INF/views/includes/head.jspf" %>
<title>No existe · SOC Forense</title>
</head>
<body>
<div class="errpage">
    <div class="errbox">
        <div class="errcode">404</div>
        <h1 style="margin:10px 0 8px">No existe</h1>
        <p>La página o el registro que busca no está. Puede que lo hayan eliminado o que el enlace esté incompleto.</p>
        <div style="display:flex;gap:10px;justify-content:center;margin-top:22px">
            <a class="btn btn-rosa" href="${ctx}/tablero">Ir al tablero</a>
            <a class="btn btn-ghost" href="javascript:history.back()">Volver</a>
        </div>
    </div>
</div>
</body>
</html>
