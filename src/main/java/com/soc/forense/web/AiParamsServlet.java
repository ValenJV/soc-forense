package com.soc.forense.web;

import com.soc.forense.ai.AiClient;
import com.soc.forense.ai.AiMetrics;
import com.soc.forense.ai.ContextBudget;
import com.soc.forense.ai.InferenceParams;
import com.soc.forense.config.AppConfig;
import com.soc.forense.util.Web;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Panel de solo lectura con los parametros generales de la plataforma de IA:
 * parametros de inferencia por perfil, ventana de contexto, fundamentacion
 * (RAG), metricas de rendimiento y precision, y recursos/costos. Los valores
 * salen de application.properties; las metricas, del arranque actual.
 */
@WebServlet("/ia/parametros")
public class AiParamsServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        req.setAttribute("profiles", List.of(InferenceParams.analisis(), InferenceParams.chat(), InferenceParams.plan()));

        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("window", ContextBudget.contextWindow());
        ctx.put("margin", ContextBudget.safetyMargin());
        ctx.put("history", 12);
        ctx.put("ragLines", AppConfig.getInt("ai.rag.lines", 40));
        ctx.put("maxLines", AppConfig.getInt("ai.max.lines", 480));
        ctx.put("chunk", 60);
        req.setAttribute("context", ctx);

        Map<String, Object> ops = new LinkedHashMap<>();
        ops.put("enabled", AppConfig.aiEnabled());
        ops.put("baseUrl", AppConfig.aiBaseUrl());
        ops.put("model", AppConfig.aiModel());
        ops.put("timeout", AppConfig.aiTimeoutSeconds());
        ops.put("maxConcurrent", AiClient.maxConcurrent());
        ops.put("inUse", AiClient.inUse());
        ops.put("queueWait", AppConfig.getInt("ai.queue.wait.seconds", 30));
        ops.put("priceIn", AiMetrics.inputPricePerMillion());
        ops.put("priceOut", AiMetrics.outputPricePerMillion());
        String url = AppConfig.aiBaseUrl();
        ops.put("local", url.contains("localhost") || url.contains("127.0.0.1"));
        req.setAttribute("ops", ops);

        req.setAttribute("metrics", AiMetrics.get().snapshot());
        Web.render(req, resp, "ai/params", "Parámetros IA", "parametros");
    }
}
