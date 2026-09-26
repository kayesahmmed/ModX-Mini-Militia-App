package com.android.support;

import android.content.Context;
import android.content.SharedPreferences;

import java.net.HttpURLConnection;
import java.net.URL;

/**
 * NTP-based trusted time.
 * Prevents device clock manipulation for expiry bypass.
 */
public final class NtpTime {

    private static final String PREFS = "ntp_cache";
    private static final String KEY_OFFSET = "offset_ms";
    private static final String KEY_LAST_SYNC = "last_sync_ms";
    private static final long SYNC_INTERVAL_MS = 6 * 60 * 60 * 1000L;

    private static volatile long sCachedOffset = 0L;
    private static volatile long sLastSyncMs = 0L;
    private static volatile boolean sLoaded = false;

    private static final String[] TIME_URLS = {
        "https://modx-lab-5a6ee-default-rtdb.firebaseio.com/.json?shallow=true",
        "https://www.google.com",
        "https://www.cloudflare.com"
    };

    private NtpTime() { }

    public static long now(Context ctx) {
        if (!sLoaded) loadCache(ctx);
        long deviceNow = System.currentTimeMillis();
        long sinceSync = deviceNow - sLastSyncMs;
        if (sinceSync > SYNC_INTERVAL_MS) refreshAsync(ctx);
        return deviceNow + sCachedOffset;
    }

    public static void refreshSync(Context ctx) {
        try {
            long deviceBefore = System.currentTimeMillis();
            long serverMs = fetchServerTimeMs();
            long deviceAfter = System.currentTimeMillis();

            if (serverMs > 0) {
                long deviceMid = (deviceBefore + deviceAfter) / 2;
                sCachedOffset = serverMs - deviceMid;
                sLastSyncMs = deviceMid;
                sLoaded = true;
                saveCache(ctx);
            }
        } catch (Throwable ignored) { }
    }

    private static void refreshAsync(final Context ctx) {
        new Thread(new Runnable() {
            @Override public void run() { refreshSync(ctx); }
        }, "NtpRefresh").start();
    }

    private static long fetchServerTimeMs() {
        for (String urlStr : TIME_URLS) {
            try {
                HttpURLConnection conn = (HttpURLConnection) new URL(urlStr).openConnection();
                conn.setRequestMethod("HEAD");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                long dateHeader = conn.getHeaderFieldDate("Date", 0L);
                conn.disconnect();
                if (dateHeader > 0) return dateHeader;
            } catch (Throwable ignored) { }
        }
        return 0L;
    }

    private static void loadCache(Context ctx) {
        try {
            SharedPreferences sp = ctx.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            sCachedOffset = sp.getLong(KEY_OFFSET, 0L);
            sLastSyncMs = sp.getLong(KEY_LAST_SYNC, 0L);
            sLoaded = true;
        } catch (Throwable ignored) { }
    }

    private static void saveCache(Context ctx) {
        try {
            SharedPreferences sp = ctx.getApplicationContext()
                    .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            sp.edit()
                .putLong(KEY_OFFSET, sCachedOffset)
                .putLong(KEY_LAST_SYNC, sLastSyncMs)
                .apply();
        } catch (Throwable ignored) { }
    }
}