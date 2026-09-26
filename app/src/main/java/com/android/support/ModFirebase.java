package com.android.support;

import org.json.JSONObject;

import java.util.Iterator;

/**
 * Firebase access layer.
 *
 *  - URL comes from native (not in dex)
 *  - HTTPS with certificate pinning
 *  - Cloud Function for login verification (server-side)
 *  - Query-based access — no full node dumps
 */
public final class ModFirebase {

    private ModFirebase() { }

    /**
     * Fetch user by username via native-built URL + pinned HTTPS.
     */
    public static JSONObject fetchUserByUsername(String username) {
        if (username == null || username.isEmpty()) return null;
        try {
            String urlStr = SecurityNative.getQueryUrl(username);
            if (urlStr == null || urlStr.isEmpty()) return null;

            String raw = PinnedHttp.get(urlStr);
            if (raw == null || raw.isEmpty() || "null".equals(raw) || "{}".equals(raw)) return null;

            JSONObject users = new JSONObject(raw);
            Iterator<String> keys = users.keys();
            if (keys.hasNext()) return users.optJSONObject(keys.next());
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Fetch update info via native URL + pinned HTTPS.
     */
    public static JSONObject fetchUpdate() {
        try {
            String urlStr = SecurityNative.getUpdateUrl();
            if (urlStr == null || urlStr.isEmpty()) return null;

            String raw = PinnedHttp.get(urlStr);
            if (raw == null || raw.isEmpty() || "null".equals(raw)) return null;
            return new JSONObject(raw);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Server-side login verification via Cloud Function.
     *   URL is retrieved from native (not in dex).
     *
     * @return JSON like:
     *   {"ok":true,"token":"...","user":"...","status":"true","expiry":"..."}
     *   or {"ok":false,"reason":"invalid_credentials"}
     */
    public static JSONObject verifyLoginRemote(String user, String pass) {
        try {
            String urlStr = SecurityNative.getCloudFnUrl();
            if (urlStr == null || urlStr.isEmpty()) return null;

            String body = "{\"user\":\"" + esc(user) + "\",\"pass\":\"" + esc(pass) + "\"}";
            String raw = PinnedHttp.post(urlStr, body);
            if (raw == null || raw.isEmpty()) return null;

            return new JSONObject(raw);
        } catch (Exception e) {
            return null;
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}