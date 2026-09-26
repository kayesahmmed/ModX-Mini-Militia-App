package com.android.support;

import android.content.Context;
import android.util.Log;

import org.json.JSONObject;

/**
 * Appwrite Cloud access layer (v3).
 *
 * CHANGES:
 *   • Firebase fallback REMOVED
 *   • Project ID from native (OBFUSCATED)
 *   • APK signature included in login payload for server-side attestation
 *   • Server's HMAC response signature (nativeSig) verified via native
 */
public final class ModFirebase {

    private static final String TAG = "ModXLab_Cloud";
    private static final boolean VERBOSE = BuildConfig.DEBUG;

    private static final String APPWRITE_RESPONSE_FORMAT = "1.6.0";

    private ModFirebase() { }

    // =================================================================
    // Update info
    // =================================================================
    public static JSONObject fetchUpdate() {
        try {
            String urlStr = SecurityNative.getUpdateUrl();
            if (urlStr == null || urlStr.isEmpty()) return null;

            String raw = PinnedHttp.get(urlStr);
            if (raw == null || raw.isEmpty() || "null".equals(raw)) return null;
            return new JSONObject(raw);
        } catch (Exception e) {
            if (VERBOSE) Log.e(TAG, "fetchUpdate failed: " + e.getMessage());
            return null;
        }
    }

    // =================================================================
    // ☁️ Login verification via Appwrite Cloud Function
    //
    // Payload includes apkSig so the server can attest the APK.
    // Response includes nativeSig (HMAC) which native verifies.
    // =================================================================
    public static JSONObject verifyLoginRemote(Context ctx, String user, String pass) {
        try {
            String urlStr = SecurityNative.getCloudFnUrl();
            if (urlStr == null || urlStr.isEmpty()) {
                if (VERBOSE) Log.e(TAG, "Cloud URL empty");
                return null;
            }

            // Get APK signature to prove authenticity
            String apkSig = SecurityNative.getApkSigHash(ctx);
            if (apkSig == null) apkSig = "";

            // ── 1) Inner payload ──
            String inner = "{\"user\":\"" + esc(user)
                    + "\",\"pass\":\"" + esc(pass)
                    + "\",\"apkSig\":\"" + esc(apkSig) + "\"}";

            // ── 2) Outer wrapper ──
            String outer = "{"
                    + "\"body\":\"" + esc(inner) + "\","
                    + "\"method\":\"POST\","
                    + "\"path\":\"/\","
                    + "\"async\":false,"
                    + "\"headers\":{}"
                    + "}";

            if (VERBOSE) Log.e(TAG, "POST body: " + outer);

            // Project ID comes from native — not in dex
            String projectId = SecurityNative.getProjectId();
            if (projectId == null || projectId.isEmpty()) {
                if (VERBOSE) Log.e(TAG, "Project ID empty");
                return null;
            }

            String raw = PinnedHttp.postJson(
                    urlStr, outer, projectId, APPWRITE_RESPONSE_FORMAT);

            if (raw == null || raw.isEmpty()) {
                if (VERBOSE) Log.e(TAG, "No response from function");
                return null;
            }

            if (VERBOSE) Log.e(TAG, "Raw: " + raw);

            JSONObject wrapper = new JSONObject(raw);

            String status = wrapper.optString("status", "");
            if (!status.isEmpty() && !"completed".equals(status)) {
                if (VERBOSE) Log.e(TAG, "Function status=" + status
                        + " errors=" + wrapper.optString("errors", ""));
                return null;
            }

            String responseBody = wrapper.optString("responseBody", "");
            if (!responseBody.isEmpty() && !"null".equals(responseBody)) {
                if (VERBOSE) Log.e(TAG, "responseBody: " + responseBody);
                try {
                    return new JSONObject(responseBody);
                } catch (Exception e) {
                    if (VERBOSE) Log.e(TAG, "responseBody not JSON: " + e.getMessage());
                    return null;
                }
            }

            if (wrapper.has("ok")) {
                return wrapper;
            }

            if (VERBOSE) Log.e(TAG, "Unexpected response shape");
            return null;

        } catch (Exception e) {
            if (VERBOSE) Log.e(TAG, "verifyLoginRemote exception: " + e.getMessage());
            return null;
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}