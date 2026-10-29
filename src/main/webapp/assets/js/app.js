/* SOC Forense: comportamiento de la interfaz. Sin librerias externas. */
(function () {
    "use strict";

    var SOC = window.SOC || { ctx: "", csrf: "" };
    var $ = function (sel, root) { return (root || document).querySelector(sel); };
    var $$ = function (sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); };

    /* ---------- avisos flash: los positivos se van solos ---------- */
    $$(".flash-ok, .flash-info").forEach(function (el) {
        if (!el.closest(".auth-box")) {
            setTimeout(function () { el.style.transition = "opacity .4s"; el.style.opacity = "0"; }, 7000);
            setTimeout(function () { el.remove(); }, 7500);
        }
    });

    /* ---------- confirmaciones y pantalla de espera ---------- */
    $$("form[data-confirm]").forEach(function (f) {
        f.addEventListener("submit", function (ev) {
            if (!window.confirm(f.getAttribute("data-confirm"))) { ev.preventDefault(); }
        });
    });

    $$("form[data-busy]").forEach(function (f) {
        f.addEventListener("submit", function (ev) {
            if (ev.defaultPrevented) { return; }
            var busy = $("#busy");
            if (!busy) { return; }
            $("#busy-title").textContent = f.getAttribute("data-busy");
            busy.classList.add("on");
            $$("button[type=submit]", f).forEach(function (b) { b.disabled = true; });
        });
    });
    window.addEventListener("pageshow", function () {
        var busy = $("#busy");
        if (busy) { busy.classList.remove("on"); }
    });

    /* ---------- dialogos ---------- */
    $$("[data-open]").forEach(function (btn) {
        btn.addEventListener("click", function () {
            var d = $(btn.getAttribute("data-open"));
            if (d && d.showModal) { d.showModal(); }
        });
    });
    $$("dialog [data-close]").forEach(function (btn) {
        btn.addEventListener("click", function () { btn.closest("dialog").close(); });
    });

    /* ---------- filtro rapido de tablas ---------- */
    $$("[data-filter]").forEach(function (input) {
        var table = $(input.getAttribute("data-filter"));
        if (!table) { return; }
        input.addEventListener("input", function () {
            var q = input.value.trim().toLowerCase();
            $$("tbody tr", table).forEach(function (tr) {
                tr.style.display = !q || tr.textContent.toLowerCase().indexOf(q) >= 0 ? "" : "none";
            });
        });
    });

    /* ---------- zona de carga ---------- */
    var drop = $("#drop");
    var fileInput = $("#file");
    if (drop && fileInput) {
        var picked = $("#picked");
        var show = function () {
            var f = fileInput.files && fileInput.files[0];
            picked.textContent = f ? f.name + " · " + humanSize(f.size) : "";
        };
        fileInput.addEventListener("change", show);
        ["dragenter", "dragover"].forEach(function (t) {
            drop.addEventListener(t, function () { drop.classList.add("over"); });
        });
        ["dragleave", "drop"].forEach(function (t) {
            drop.addEventListener(t, function () { drop.classList.remove("over"); });
        });
        drop.addEventListener("drop", function () { setTimeout(show, 0); });
    }

    function humanSize(b) {
        if (b < 1024) { return b + " B"; }
        if (b < 1048576) { return (b / 1024).toFixed(1) + " KB"; }
        return (b / 1048576).toFixed(1) + " MB";
    }

    /* ---------- estado de la IA y medidor de riesgo ---------- */
    var pill = $("#ai-pill");
    var gauge = $("#gauge");

    function setPill(ai) {
        if (!pill || !ai) { return; }
        var label = pill.querySelector("span:last-child");
        pill.classList.remove("live", "off");
        if (!ai.enabled) {
            pill.classList.add("off");
            label.textContent = "IA desactivada · motor de reglas";
        } else if (ai.reachable) {
            pill.classList.add("live");
            label.textContent = "IA en línea · " + ai.model;
        } else {
            pill.classList.add("off");
            label.textContent = "IA sin respuesta · motor de reglas";
        }
        pill.title = ai.baseUrl || "";
    }

    function drawTicks() {
        var g = $("#gauge-ticks");
        if (!g || g.childNodes.length) { return; }
        var ns = "http://www.w3.org/2000/svg";
        for (var i = 0; i <= 10; i++) {
            var a = Math.PI - (Math.PI * i / 10);
            var r1 = 100, r2 = i % 5 === 0 ? 88 : 93;
            var line = document.createElementNS(ns, "line");
            line.setAttribute("x1", 150 + r1 * Math.cos(a));
            line.setAttribute("y1", 150 - r1 * Math.sin(a));
            line.setAttribute("x2", 150 + r2 * Math.cos(a));
            line.setAttribute("y2", 150 - r2 * Math.sin(a));
            line.setAttribute("stroke", i % 5 === 0 ? "#9d9db4" : "#363648");
            line.setAttribute("stroke-width", i % 5 === 0 ? "2" : "1.5");
            g.appendChild(line);
        }
    }

    function setGauge(risk, label) {
        if (!gauge) { return; }
        risk = Math.max(0, Math.min(100, Number(risk) || 0));
        $("#gauge-needle").style.transform = "rotate(" + (-90 + risk * 1.8) + "deg)";
        $("#gauge-arc").setAttribute("stroke-dasharray", risk + " 100");
        $("#gauge-arc").style.transition = "stroke-dasharray 1.1s cubic-bezier(.2,.9,.25,1.05)";
        var num = $("#gauge-num");
        var from = Number(num.textContent) || 0;
        var start = null;
        var step = function (t) {
            if (!start) { start = t; }
            var p = Math.min(1, (t - start) / 900);
            num.textContent = Math.round(from + (risk - from) * p);
            if (p < 1) { requestAnimationFrame(step); }
        };
        requestAnimationFrame(step);
        if (label) { $("#gauge-label").textContent = label; }
    }

    function refresh() {
        fetch(SOC.ctx + "/api/tablero", { headers: { "Accept": "application/json" }, credentials: "same-origin" })
            .then(function (r) { return r.ok ? r.json() : null; })
            .then(function (d) {
                if (!d) { return; }
                setPill(d.ai);
                if (!gauge) { return; }
                setGauge(d.riskIndex, d.riskLabel);
                $$("[data-stat]").forEach(function (el) {
                    var k = el.getAttribute("data-stat");
                    if (d[k] !== undefined) { el.textContent = d[k]; }
                });
                var total = d.findingsTotal || 0;
                ["critical", "high", "medium", "low"].forEach(function (s, idx) {
                    var n = (d.severity && d.severity[s]) || 0;
                    var el = document.querySelector("[data-sev=" + s + "]");
                    if (el) { el.textContent = n; }
                    var bar = $$("#sevbar span")[idx];
                    if (bar) { bar.style.width = (total ? n * 100 / total : 0) + "%"; }
                });
            })
            .catch(function () { /* sin red: se reintenta en el siguiente ciclo */ });
    }

    if (gauge) {
        drawTicks();
        var initial = Number(gauge.getAttribute("data-risk")) || 0;
        $("#gauge-num").textContent = "0";
        setTimeout(function () { setGauge(initial); }, 150);
        setInterval(refresh, 30000);
    }
    if (pill) { refresh(); }

    /* ---------- chat con el agente ---------- */
    var form = $("#chat-form");
    if (form) {
        var log = $("#chat-log");
        var input = $("#chat-input");
        var send = $("#chat-send");
        var evSel = $("#chat-evidence");
        var sending = false;

        var scrollDown = function () { log.scrollTop = log.scrollHeight; };
        var ahora = function () {
            var d = new Date(), p = function (n) { return (n < 10 ? "0" : "") + n; };
            return p(d.getDate()) + "/" + p(d.getMonth() + 1) + "/" + d.getFullYear() + " " + p(d.getHours()) + ":" + p(d.getMinutes());
        };
        scrollDown();

        var autosize = function () {
            input.style.height = "auto";
            input.style.height = Math.min(180, input.scrollHeight) + "px";
        };
        input.addEventListener("input", autosize);

        var initials = (function () {
            var el = $(".rail .avatar");
            return el ? el.textContent.trim() : "TÚ";
        })();

        var addMsg = function (role, text, meta) {
            var w = $("#welcome");
            if (w) { w.remove(); }
            var msg = document.createElement("div");
            msg.className = "msg " + (role === "user" ? "user" : "bot");
            var who = document.createElement("div");
            who.className = "who";
            who.textContent = role === "user" ? initials : "IA";
            var body = document.createElement("div");
            var bubble = document.createElement("div");
            bubble.className = "bubble";
            if (text === null) {
                bubble.innerHTML = '<span class="typing"><span></span><span></span><span></span></span>';
            } else {
                bubble.textContent = text;
            }
            body.appendChild(bubble);
            if (meta) {
                var m = document.createElement("div");
                m.className = "bubble-meta";
                m.textContent = meta;
                body.appendChild(m);
            }
            msg.appendChild(who);
            msg.appendChild(body);
            log.appendChild(msg);
            scrollDown();
            return { msg: msg, bubble: bubble, body: body };
        };

        var ask = function (text) {
            if (sending || !text.trim()) { return; }
            sending = true;
            send.disabled = true;
            var evId = evSel ? evSel.value : "";
            var ctxLabel = evId ? " · evidencia #" + evId : "";
            addMsg("user", text, ahora() + ctxLabel);
            input.value = "";
            autosize();
            var pending = addMsg("bot", null);

            var params = new URLSearchParams();
            params.set("action", "ask");
            params.set("message", text);
            if (evId) { params.set("evidenceId", evId); }

            fetch(SOC.ctx + "/api/agente", {
                method: "POST",
                credentials: "same-origin",
                headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8", "X-CSRF-Token": SOC.csrf },
                body: params.toString()
            })
                .then(function (r) { return r.json().catch(function () { return { ok: false, error: "Respuesta inválida del servidor (" + r.status + ")." }; }); })
                .then(function (d) {
                    if (d.ok) {
                        pending.bubble.textContent = d.content;
                        var meta = document.createElement("div");
                        meta.className = "bubble-meta";
                        meta.textContent = (d.time && d.time !== "—" ? d.time : ahora()) + ctxLabel + (d.usedAi ? "" : " · sin IA, datos de la base");
                        pending.body.appendChild(meta);
                    } else {
                        pending.bubble.textContent = d.error || "No se obtuvo respuesta.";
                        pending.msg.querySelector(".bubble").style.borderColor = "var(--rosa-borde)";
                    }
                })
                .catch(function () {
                    pending.bubble.textContent = "Se perdió la conexión con el servidor. Intente de nuevo.";
                })
                .then(function () {
                    sending = false;
                    send.disabled = false;
                    scrollDown();
                    input.focus();
                });
        };

        form.addEventListener("submit", function (ev) {
            ev.preventDefault();
            ask(input.value);
        });
        input.addEventListener("keydown", function (ev) {
            if (ev.key === "Enter" && !ev.shiftKey && !ev.isComposing) {
                ev.preventDefault();
                ask(input.value);
            }
        });
        $$(".prompt").forEach(function (p) {
            p.addEventListener("click", function () { ask(p.textContent.trim()); });
        });

        var clear = $("#chat-clear");
        if (clear) {
            clear.addEventListener("click", function () {
                if (!window.confirm("Se borrará todo su historial con el agente. ¿Continuar?")) { return; }
                fetch(SOC.ctx + "/api/agente", {
                    method: "POST",
                    credentials: "same-origin",
                    headers: { "Content-Type": "application/x-www-form-urlencoded", "X-CSRF-Token": SOC.csrf },
                    body: "action=clear"
                }).then(function () { window.location.reload(); });
            });
        }
    }
})();
