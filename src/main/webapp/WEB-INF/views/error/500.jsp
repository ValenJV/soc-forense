<%@ page isErrorPage="true" %>
<!DOCTYPE html>
<html lang="es">
<head>
<%@ include file="/WEB-INF/views/includes/head.jspf" %>
<title>Error interno · SOC Forense</title>
</head>
<body>
<div class="errpage">
    <div class="errbox">
        <div class="errcode">500</div>
        <h1 style="margin:10px 0 8px">Algo falló en el servidor</h1>
        <p>La acción no se completó. El detalle quedó en el log de Tomcat (pestaña Output de NetBeans).</p>
        <c:if test="${not empty requestScope['jakarta.servlet.error.message']}">
            <div class="rawline" style="text-align:left"><c:out value="${requestScope['jakarta.servlet.error.message']}" /></div>
        </c:if>
        <div style="display:flex;gap:10px;justify-content:center;margin-top:22px">
            <a class="btn btn-rosa" href="${ctx}/tablero">Ir al tablero</a>
            <a class="btn btn-ghost" href="javascript:history.back()">Volver</a>
        </div>
    </div>
</div>
</body>
</html>
