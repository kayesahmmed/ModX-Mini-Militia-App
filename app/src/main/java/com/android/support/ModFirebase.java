package com.android.support;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Firebase access via REST API.
 * No SDK, no manifest entries — works inside injected games too.
 */
public final class ModFirebase {

    private static final String TAG = "ModXLab_Firebase";

    // ================================================================
    // 🔥 Firebase — modx-lab-5a6ee (from google-services.json)
    // ================================================================
    public static final String DATABASE_URL = "https://modx-lab-5a6ee-default-rtdb.firebaseio.com";
    public static final String API_KEY      = "AIzaSyALZMuCJ0UE8dc0VCNqDbH-VgPvrfo4WJc";

    private ModFirebase() { }

    /**
     * GET JSON from a Firebase path.
     * Example: fetch("User") → returns JSON string of "User" node.
     * Returns null on network error.
     */
    public static String fetch(String path) {
        HttpURLConnection conn = null;
        try {
            String cleanPath = path.startsWith("/") ? path : "/" + path;
            URL url = new URL(DATABASE_URL + cleanPath + ".json");
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Accept", "application/json");

            int code = conn.getResponseCode();
            if (code == 200) {
                BufferedReader br = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), "UTF-8"));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
                br.close();
                return sb.toString();
            } else {
                Log.w(TAG, "HTTP " + code + " for " + path);
                return null;
            }
        } catch (Exception e) {
            Log.e(TAG, "fetch failed: " + e);
            return null;
        } finally {
            if (conn != null) try { conn.disconnect(); } catch (Exception ignored) { }
        }
    }

    /**
     * Same as fetch() but returns a JSONObject directly (or null).
     */
    public static JSONObject fetchJson(String path) {
        String raw = fetch(path);
        if (raw == null || raw.isEmpty() || "null".equals(raw)) return null;
        try {
            return new JSONObject(raw);
        } catch (Exception e) {
            Log.e(TAG, "JSON parse failed: " + e);
            return null;
        }
    }
}