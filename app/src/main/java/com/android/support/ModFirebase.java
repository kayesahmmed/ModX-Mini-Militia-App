package com.android.support;

import org.json.JSONObject;

import java.util.Iterator;

/**
 * Appwrite Cloud access layer.
 *
 *  - URL from native (not in dex)
 *  - HTTPS with certificate pinning
 *  - Cloud Function for login verification (server-side)
 *  - Query-based access via native URLs
 */
public final class ModFirebase {

    // 🔑 Appwrite Project ID
    private static final String APPWRITE_PROJECT_ID = "modxlab";

    private ModFirebase() { }

    /**
     * Fetch user by username via native-built URL + pinned HTTPS.
     * Uses Appwrite REST API for direct document queries.
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
     * Server-side login via Appwrite Cloud Function.
     * URL from native, uses pinned HTTPS with X-Appwrite-Project header.
     */
    public static JSONObject verifyLoginRemote(String user, String pass) {
        try {
            String urlStr = SecurityNative.getCloudFnUrl();
            if (urlStr == null || urlStr.isEmpty()) return null;

            String body = "{\"user\":\"" + esc(user) + "\",\"pass\":\"" + esc(pass) + "\"}";
            String raw = PinnedHttp.post(urlStr, body, APPWRITE_PROJECT_ID);
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