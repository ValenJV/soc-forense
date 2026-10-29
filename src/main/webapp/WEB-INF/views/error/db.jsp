<!DOCTYPE html>
<html lang="es">
<head>
<%@ include file="/WEB-INF/views/includes/head.jspf" %>
<title>Base de datos no disponible · SOC Forense</title>
<meta http-equiv="refresh" content="15">
</head>
<body>
<div class="errpage">
    <div class="errbox">
        <div class="errcode" style="font-size:72px">MySQL</div>
        <h1 style="margin:14px 0 8px">No hay conexión con la base de datos</h1>
        <p>La aplicación arrancó, pero no logra entrar a MySQL. Esta página se recarga sola cada 15 segundos.</p>
        <c:if test="${not empty applicationScope.dbError}">
            <div class="rawline" style="text-align:left"><c:out value="${applicationScope.dbError}" /></div>
        </c:if>
        <div class="card steps">
            <h3 style="margin-bottom:8px">Revise, en este orden</h3>
            <ol style="margin:0;padding-left:20px;color:var(--tenue);line-height:1.9">
                <li>Que el servicio MySQL esté iniciado (MySQL Workbench → Server → Startup/Shutdown).</li>
                <li>Que el usuario y la contraseña coincidan con <code>src/main/resources/application.properties</code> (por defecto <code>root</code> / <code>root</code>).</li>
                <li>Que el puerto sea 3306, o el que indique <code>db.url</code>.</li>
                <li>Si cambió el archivo, vuelva a ejecutar el proyecto desde NetBeans.</li>
            </ol>
            <p style="color:var(--apagado);font-size:12.5px;margin:10px 0 0">No hace falta crear el esquema a mano: <code>soc_forense</code> y sus tablas se crean solos.</p>
        </div>
    </div>
</div>
</body>
</html>
