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
 *
 *  FIX (v2):
 *    - Appwrite /executions endpoint requires WRAPPER JSON:
 *        { "body": "<inner-json-string>", "method":"POST", "path":"/", "async":false }
 *    - Response is { ... "responseBody":"<string>", ... } → parse twice.
 */
public final class ModFirebase {

    // 🔑 Appwrite Project ID
    private static final String APPWRITE_PROJECT_ID = "modxlab";

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
     * Server-side login via Appwrite Cloud Function.
     *
     * Appwrite /v1/functions/{id}/executions EXPECTS:
     *   {
     *     "body":    "<string payload>",
     *     "method":  "POST",
     *     "path":    "/",
     *     "async":   false
     *   }
     *
     * Appwrite RETURNS:
     *   {
     *     "$id": "...",
     *     "status": "completed",
     *     "responseStatusCode": 200,
     *     "responseBody": "{ ...actual function JSON... }",
     *     ...
     *   }
     */
    public static JSONObject verifyLoginRemote(String user, String pass) {
        try {
            String urlStr = SecurityNative.getCloudFnUrl();
            if (urlStr == null || urlStr.isEmpty()) return null;

            // ── 1) Inner JSON — the actual payload the function reads
            String inner = "{\"user\":\"" + esc(user) + "\",\"pass\":\"" + esc(pass) + "\"}";

            // ── 2) Outer JSON — Appwrite executions wrapper
            //     IMPORTANT: "body" is a STRING containing the inner JSON,
            //     so inner quotes must be escaped via esc().
            String body = "{\"body\":\"" + esc(inner) + "\","
                        + "\"method\":\"POST\","
                        + "\"path\":\"/\","
                        + "\"async\":false}";

            String raw = PinnedHttp.post(urlStr, body, APPWRITE_PROJECT_ID);
            if (raw == null || raw.isEmpty()) return null;

            // ── 3) Parse the Appwrite wrapper object
            JSONObject wrapper = new JSONObject(raw);

            // ── 4) Extract the function's actual response (nested JSON string)
            String responseBody = wrapper.optString("responseBody", "");
            if (responseBody == null || responseBody.isEmpty() || "null".equals(responseBody)) {
                // Some Appwrite setups / versions return the function JSON directly.
                return wrapper;
            }
            return new JSONObject(responseBody);

        } catch (Exception e) {
            return null;
        }
    }

    /** JSON-string escape (backslash + double-quote). */
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}