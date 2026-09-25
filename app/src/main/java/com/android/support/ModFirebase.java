package com.android.support;

import android.util.Log;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;

public final class ModFirebase {
    private static final String TAG = "ModXLab_Firebase";
    private ModFirebase() { }

    public static JSONObject fetchUserByUsername(String username) {
        if (username == null || username.isEmpty()) return null;
        HttpURLConnection conn = null;
        try {
            String urlStr = SecurityNative.getQueryUrl(username);
            if (urlStr == null || urlStr.isEmpty()) return null;

            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Accept", "application/json");

            int code = conn.getResponseCode();
            if (code != 200) { Log.w(TAG, "HTTP " + code); return null; }

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            String raw = sb.toString();
            if (raw.isEmpty() || "null".equals(raw) || "{}".equals(raw)) return null;

            JSONObject users = new JSONObject(raw);
            Iterator<String> keys = users.keys();
            if (keys.hasNext()) return users.optJSONObject(keys.next());
            return null;
        } catch (Exception e) {
            Log.e(TAG, "fetch failed: " + e);
            return null;
        } finally {
            if (conn != null) try { conn.disconnect(); } catch (Exception ignored) { }
        }
    }

    public static JSONObject fetchUpdate() {
        HttpURLConnection conn = null;
        try {
            String urlStr = SecurityNative.getUpdateUrl();
            if (urlStr == null || urlStr.isEmpty()) return null;
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Accept", "application/json");
            int code = conn.getResponseCode();
            if (code != 200) return null;

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            String raw = sb.toString();
            if (raw.isEmpty() || "null".equals(raw)) return null;
            return new JSONObject(raw);
        } catch (Exception e) {
            Log.e(TAG, "update failed: " + e);
            return null;
        } finally {
            if (conn != null) try { conn.disconnect(); } catch (Exception ignored) { }
        }
    }
}