package com.android.support;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Client-side rate limiter for login attempts.
 */
public final class RateLimiter {

    private static final String PREFS = "rate_limit";
    private static final int MAX_ATTEMPTS = 5;
    private static final long LOCKOUT_MS = 60 * 1000L;
    private static final long DECAY_WINDOW_MS = 5 * 60 * 1000L;

    private RateLimiter() { }

    public static boolean isLocked(Context ctx) {
        SharedPreferences sp = prefs(ctx);
        long lockUntil = sp.getLong("lock_until", 0L);
        return System.currentTimeMillis() < lockUntil;
    }

    public static long lockRemainingMs(Context ctx) {
        SharedPreferences sp = prefs(ctx);
        long lockUntil = sp.getLong("lock_until", 0L);
        long rem = lockUntil - System.currentTimeMillis();
        return rem > 0 ? rem : 0L;
    }

    public static void recordFailure(Context ctx) {
        SharedPreferences sp = prefs(ctx);
        long now = System.currentTimeMillis();
        long windowStart = sp.getLong("window_start", 0L);
        int count = sp.getInt("count", 0);

        if (now - windowStart > DECAY_WINDOW_MS) {
            windowStart = now;
            count = 0;
        }
        count++;

        if (count >= MAX_ATTEMPTS) {
            sp.edit()
                .putLong("lock_until", now + LOCKOUT_MS)
                .putInt("count", 0)
                .putLong("window_start", now)
                .apply();
        } else {
            sp.edit()
                .putInt("count", count)
                .putLong("window_start", windowStart)
                .apply();
        }
    }

    public static void reset(Context ctx) {
        prefs(ctx).edit().clear().apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}