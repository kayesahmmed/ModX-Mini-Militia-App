package com.android.support;

import android.util.Log;

import org.json.JSONObject;

import java.util.Iterator;

/**
 * Appwrite Cloud access layer.
 *
 * FIX (v3):
 *   • Correct Appwrite /executions request shape (nested body string)
 *   • Add X-Appwrite-Response-Format header (Appwrite requires it)
 *   • Parse responseBody (nested JSON string) properly
 *   • Check execution status/errors before parsing
 *   • Full logcat diagnostics
 *   • Return null ONLY on network/server failure → callers can
 *     distinguish "server down" from "invalid credentials".
 */
public final class ModFirebase {

    private static final String TAG = "ModXLab_Cloud";

    // 🔑 Appwrite project ID — MUST match your console → Settings → Project ID
    private static final String APPWRITE_PROJECT_ID = "modxlab";

    // Appwrite response format — safe for 1.6.x; adjust if needed.
    private static final String APPWRITE_RESPONSE_FORMAT = "1.6.0";

    private ModFirebase() { }

    // =================================================================
    // Legacy Firebase user lookup — retained for compatibility only.
    // Your data is in Appwrite → this will normally return null.
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
            Log.w(TAG, "fetchUserByUsername failed: " + e.getMessage());
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
            Log.w(TAG, "fetchUpdate failed: " + e.getMessage());
            return null;
        }
    }

    // =================================================================
    // ☁️ Appwrite Cloud Function — Login verification
    //
    // Request to /v1/functions/{id}/executions MUST be:
    //   {
    //     "body":    "<string payload>",   ← escaped JSON string
    //     "method":  "POST",
    //     "path":    "/",
    //     "async":   false,
    //     "headers": {}
    //   }
    //
    // Response comes back as:
    //   {
    //     "$id": "...",
    //     "status": "completed",
    //     "responseStatusCode": 200,
    //     "responseBody": "{ ...actual function JSON... }",  ← nested string
    //     "errors": "",
    //     ...
    //   }
    // =================================================================
    public static JSONObject verifyLoginRemote(String user, String pass) {
        try {
            String urlStr = SecurityNative.getCloudFnUrl();
            if (urlStr == null || urlStr.isEmpty()) {
                Log.e(TAG, "❌ Cloud function URL is empty (native returned nothing)");
                return null;
            }

            // ── 1) INNER payload — function's actual input
            String inner = "{\"user\":\"" + esc(user) + "\",\"pass\":\"" + esc(pass) + "\"}";

            // ── 2) OUTER wrapper — Appwrite executions envelope.
            //      Note: `inner` must be JSON-string-escaped so its quotes
            //      don't break the outer JSON.
            String outer = "{"
                    + "\"body\":\"" + esc(inner) + "\","
                    + "\"method\":\"POST\","
                    + "\"path\":\"/\","
                    + "\"async\":false,"
                    + "\"headers\":{}"
                    + "}";

            Log.d(TAG, "☁️ Calling Appwrite function…");
            Log.d(TAG, "URL : " + urlStr);
            Log.d(TAG, "Body: " + outer);

            String raw = PinnedHttp.postJson(
                    urlStr, outer, APPWRITE_PROJECT_ID, APPWRITE_RESPONSE_FORMAT);

            if (raw == null || raw.isEmpty()) {
                Log.e(TAG, "❌ No response from Appwrite function");
                return null;
            }

            Log.d(TAG, "📥 Raw: " + raw);

            JSONObject wrapper = new JSONObject(raw);

            // ── 3) Verify execution completed
            String status = wrapper.optString("status", "");
            if (!status.isEmpty() && !"completed".equals(status)) {
                String errors = wrapper.optString("errors", "");
                Log.e(TAG, "❌ Function status=" + status + " errors=" + errors);
                return null;
            }

            // ── 4) Unwrap responseBody (nested JSON string)
            String responseBody = wrapper.optString("responseBody", "");
            if (!responseBody.isEmpty() && !"null".equals(responseBody)) {
                Log.d(TAG, "📦 responseBody: " + responseBody);
                try {
                    return new JSONObject(responseBody);
                } catch (Exception e) {
                    Log.e(TAG, "❌ responseBody is not valid JSON: " + e.getMessage());
                    return null;
                }
            }

            // ── 5) Fallback: some Appwrite versions return the function
            //          result inline (no wrapper).
            if (wrapper.has("ok")) {
                Log.d(TAG, "📦 Inline function response (no wrapper)");
                return wrapper;
            }

            Log.e(TAG, "❌ Unexpected response shape: " + raw);
            return null;

        } catch (Exception e) {
            Log.e(TAG, "❌ verifyLoginRemote exception: " + e.getMessage(), e);
            return null;
        }
    }

    /** JSON-string escape (backslash + double-quote). */
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}