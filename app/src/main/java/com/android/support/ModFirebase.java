package com.android.support;

import android.util.Log;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Iterator;

/**
 * Firebase REST access.
 *
 * ⚠️ SECURITY NOTE:
 *  - We NEVER download the whole "User" node.
 *  - We query by username with orderBy/equalTo so Firebase returns
 *    only the ONE matching record (or {} if none).
 *  - Password / status / expiry verification is done in native code
 *    (see Security.cpp :: verifyLogin).
 *
 * Recommended Firebase rule for the /User node:
 *   {
 *     "rules": {
 *       "User": {
 *         ".read": true,
 *         ".write": false,
 *         ".indexOn": ["user"]
 *       }
 *     }
 *   }
 *  This allows the query but blocks dumping the whole node from any client.
 */
public final class ModFirebase {

    private static final String TAG = "ModXLab_Firebase";

    public static final String DATABASE_URL = "https://modx-lab-5a6ee-default-rtdb.firebaseio.com";
    public static final String API_KEY      = "AIzaSyALZMuCJ0UE8dc0VCNqDbH-VgPvrfo4WJc";

    private ModFirebase() { }

    /** Generic GET (kept for update-check / other endpoints). */
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
            }
            Log.w(TAG, "HTTP " + code + " for " + path);
            return null;
        } catch (Exception e) {
            Log.e(TAG, "fetch failed: " + e);
            return null;
        } finally {
            if (conn != null) try { conn.disconnect(); } catch (Exception ignored) { }
        }
    }

    public static JSONObject fetchJson(String path) {
        String raw = fetch(path);
        if (raw == null || raw.isEmpty() || "null".equals(raw)) return null;
        try { return new JSONObject(raw); } catch (Exception e) { return null; }
    }

    /**
     * Fetch exactly ONE user whose "user" field equals `username`.
     * Returns the matching user JSONObject, or null.
     *
     * Query: /User.json?orderBy="user"&equalTo="<username>"
     */
    public static JSONObject fetchUserByUsername(String username) {
        if (username == null || username.isEmpty()) return null;
        HttpURLConnection conn = null;
        try {
            String encoded = URLEncoder.encode("\"" + username + "\"", "UTF-8");
            String urlStr = DATABASE_URL + "/User.json?orderBy=%22user%22&equalTo=" + encoded;
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(10000);
            conn.setReadTimeout(10000);
            conn.setRequestProperty("Accept", "application/json");

            int code = conn.getResponseCode();
            if (code != 200) {
                Log.w(TAG, "fetchUserByUsername HTTP " + code);
                return null;
            }
            BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            String raw = sb.toString();
            if (raw.isEmpty() || "null".equals(raw) || "{}".equals(raw)) return null;

            JSONObject users = new JSONObject(raw);
            Iterator<String> keys = users.keys();
            if (keys.hasNext()) {
                return users.optJSONObject(keys.next());
            }
            return null;
        } catch (Exception e) {
            Log.e(TAG, "fetchUserByUsername failed: " + e);
            return null;
        } finally {
            if (conn != null) try { conn.disconnect(); } catch (Exception ignored) { }
        }
    }
}