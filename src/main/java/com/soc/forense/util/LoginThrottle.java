package com.soc.forense.util;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Freno simple contra fuerza bruta en el login, en memoria. Tras 5 fallos en
 * 10 minutos para la misma combinacion usuario+IP, bloquea 2 minutos. Seria
 * incomodo que una plataforma forense cayera en el mismo ataque que detecta.
 */
public final class LoginThrottle {

    private static final int MAX_FAILS = 5;
    private static final long WINDOW_MS = 10 * 60 * 1000L;
    private static final long LOCK_MS = 2 * 60 * 1000L;

    private static final ConcurrentHashMap<String, long[]> STATE = new ConcurrentHashMap<>();

    private LoginThrottle() {
    }

    private static String key(String username, String ip) {
        return (username == null ? "" : username.trim().toLowerCase()) + "|" + ip;
    }

    /** Segundos restantes de bloqueo, o 0 si puede intentar. */
    public static long secondsLocked(String username, String ip) {
        long[] s = STATE.get(key(username, ip));
        if (s == null) {
            return 0;
        }
        long now = System.currentTimeMillis();
        if (s[2] > now) {
            return Math.max(1, (s[2] - now) / 1000);
        }
        return 0;
    }

    public static void fail(String username, String ip) {
        long now = System.currentTimeMillis();
        STATE.compute(key(username, ip), (k, s) -> {
            // s[0] = fallos, s[1] = inicio de ventana, s[2] = bloqueado hasta
            if (s == null || now - s[1] > WINDOW_MS) {
                s = new long[]{0, now, 0};
            }
            s[0]++;
            if (s[0] >= MAX_FAILS) {
                s[2] = now + LOCK_MS;
                s[0] = 0;
                s[1] = now;
            }
            return s;
        });
        if (STATE.size() > 10_000) {
            STATE.entrySet().removeIf(e -> now - e.getValue()[1] > WINDOW_MS && e.getValue()[2] < now);
        }
    }

    public static void success(String username, String ip) {
        STATE.remove(key(username, ip));
    }
}
