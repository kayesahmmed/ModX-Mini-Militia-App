package com.android.support;

import android.util.Log;

import org.json.JSONObject;

import java.util.Iterator;

/**
 * Appwrite Cloud access layer.
 *
 * NOTE: All logs use Log.e() so they survive ProGuard in Release.
 */
public final class ModFirebase {

    private static final String TAG = "ModXLab_Cloud";

    // 🔑 Appwrite project ID
    private static final String APPWRITE_PROJECT_ID = "modxlab";
    private static final String APPWRITE_RESPONSE_FORMAT = "1.6.0";

    private ModFirebase() { }

    // =================================================================
    // Legacy Firebase user lookup (retained for compatibility)
    // =================================================================
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
            Log.e(TAG, "fetchUserByUsername failed: " + e.getMessage());
            return null;
        }
    }

    // =================================================================
    // Update info via Firebase RTDB
    // =================================================================
    public static JSONObject fetchUpdate() {
        try {
            String urlStr = SecurityNative.getUpdateUrl();
            if (urlStr == null || urlStr.isEmpty()) return null;

            String raw = PinnedHttp.get(urlStr);
            if (raw == null || raw.isEmpty() || "null".equals(raw)) return null;
            return new JSONObject(raw);
        } catch (Exception e) {
            Log.e(TAG, "fetchUpdate failed: " + e.getMessage());
            return null;
        }
    }

    // =================================================================
    // ☁️ Appwrite Cloud Function — Login verification
    //
    // Request shape (Appwrite /executions):
    //   {
    //     "body":    "<escaped inner JSON string>",
    //     "method":  "POST",
    //     "path":    "/",
    //     "async":   false,
    //     "headers": {}
    //   }
    //
    // Response shape:
    //   {
    //     "status": "completed",
    //     "responseStatusCode": 200,
    //     "responseBody": "<nested function JSON>",
    //     ...
    //   }
    // =================================================================
    public static JSONObject verifyLoginRemote(String user, String pass) {
        try {
            String urlStr = SecurityNative.getCloudFnUrl();
            Log.e(TAG, "Cloud URL: " + urlStr);

            if (urlStr == null || urlStr.isEmpty()) {
                Log.e(TAG, "❌ Cloud function URL is empty");
                return null;
            }

            // ── 1) Inner payload
            String inner = "{\"user\":\"" + esc(user) + "\",\"pass\":\"" + esc(pass) + "\"}";

            // ── 2) Outer wrapper
            String outer = "{"
                    + "\"body\":\"" + esc(inner) + "\","
                    + "\"method\":\"POST\","
                    + "\"path\":\"/\","
                    + "\"async\":false,"
                    + "\"headers\":{}"
                    + "}";

            Log.e(TAG, "POST body: " + outer);

            String raw = PinnedHttp.postJson(
                    urlStr, outer, APPWRITE_PROJECT_ID, APPWRITE_RESPONSE_FORMAT);

            if (raw == null || raw.isEmpty()) {
                Log.e(TAG, "❌ No response from Appwrite function");
                return null;
            }

            Log.e(TAG, "📥 Raw: " + raw);

            JSONObject wrapper = new JSONObject(raw);

            // ── 3) Verify execution completed
            String status = wrapper.optString("status", "");
            if (!status.isEmpty() && !"completed".equals(status)) {
                String errors = wrapper.optString("errors", "");
                Log.e(TAG, "❌ Function status=" + status + " errors=" + errors);
                return null;
            }

            // ── 4) Unwrap responseBody
            String responseBody = wrapper.optString("responseBody", "");
            if (!responseBody.isEmpty() && !"null".equals(responseBody)) {
                Log.e(TAG, "📦 responseBody: " + responseBody);
                try {
                    return new JSONObject(responseBody);
                } catch (Exception e) {
                    Log.e(TAG, "❌ responseBody not valid JSON: " + e.getMessage());
                    return null;
                }
            }

            // ── 5) Fallback: inline response
            if (wrapper.has("ok")) {
                Log.e(TAG, "📦 Inline function response");
                return wrapper;
            }

            Log.e(TAG, "❌ Unexpected response shape: " + raw);
            return null;

        } catch (Exception e) {
            Log.e(TAG, "❌ verifyLoginRemote exception: " + e.getMessage(), e);
            return null;
        }
    }

    /** JSON-string escape. */
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}