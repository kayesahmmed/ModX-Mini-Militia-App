package com.android.support;

import org.json.JSONObject;

import java.util.Iterator;

/**
 * Firebase REST access.
 *
 * NOTE:
 *   - URL comes from native (not in dex)
 *   - Password verification happens in native (Security.cpp :: verifyLogin)
 *   - Query-based access — never downloads full "User" node
 */
public final class ModFirebase {

    private ModFirebase() { }

    /**
     * Fetch user by username via native-built URL.
     * Uses Firebase orderBy=user & equalTo=<username> query.
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
     * Fetch update info.
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
}