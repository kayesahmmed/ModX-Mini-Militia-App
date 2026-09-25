package com.android.support;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * ModX Lab — Main entry point
 *
 * Thread-safe, single-launch guarantee.
 */
public class Main {

    private static final String TAG = "Mod_Main";

    static {
        try {
            System.loadLibrary("ModXLab");
        } catch (Throwable t) {
            Log.e(TAG, "Failed to load native library: " + t.getMessage());
        }
    }

    // ---------------------------------------------------------------
    // Native methods
    // ---------------------------------------------------------------
    private static native void CheckOverlayPermission(Context context);
    private static native void setNativeCrashDir(String dir);

    // ---------------------------------------------------------------
    // Single-launch tracking (thread-safe)
    // ---------------------------------------------------------------
    private static final Object sLock = new Object();
    private static Menu sMenu = null;

    /**
     * Set to true as soon as we decide to create a Menu.
     * Prevents onResume/native-callback race from creating a second Menu.
     * Never reset during a session — one Menu per process lifetime.
     */
    private static final AtomicBoolean sLaunchAttempted = new AtomicBoolean(false);

    // ---------------------------------------------------------------
    // Public API — called from MainActivity.onCreate()
    // ---------------------------------------------------------------
    public static void Start(Context context) {
        if (context == null) {
            Log.e(TAG, "Start() called with null context");
            return;
        }

        Log.i(TAG, "Main.Start() invoked");

        CrashHandler.init(context, false);

        try {
            java.io.File extDir = context.getExternalFilesDir(null);
            if (extDir != null) {
                setNativeCrashDir(extDir.getAbsolutePath());
            } else {
                setNativeCrashDir("/storage/emulated/0/Documents");
            }
        } catch (Exception e) {
            setNativeCrashDir("/storage/emulated/0/Documents");
        }

        // Native permission check (will call back StartWithoutPermission)
        CheckOverlayPermission(context);
    }

    /**
     * Called by native code (JNI) after overlay permission is confirmed,
     * OR directly from Java if you handle permission yourself.
     *
     * Guaranteed to create AT MOST ONE Menu per process.
     */
    public static void StartWithoutPermission(Context context) {
        if (context == null) {
            Log.e(TAG, "StartWithoutPermission() null context");
            return;
        }

        // 🔥 ATOMIC single-launch guard — prevents race condition
        if (!sLaunchAttempted.compareAndSet(false, true)) {
            Log.i(TAG, "Menu launch already attempted — skipping duplicate call");
            return;
        }

        Log.i(TAG, "StartWithoutPermission() — first launch attempt");

        CrashHandler.init(context, true);

        if (!(context instanceof Activity)) {
            Log.e(TAG, "Context is not an Activity — cannot attach menu");
            Toast.makeText(context, "Failed to launch the mod menu\n", Toast.LENGTH_LONG).show();
            // Allow retry since we didn't actually launch
            sLaunchAttempted.set(false);
            return;
        }

        try {
            Menu menu = new Menu(context);

            synchronized (sLock) {
                sMenu = menu;
            }

            menu.SetWindowManagerActivity();
            menu.ShowMenu();

            Log.i(TAG, "Menu launched successfully");

        } catch (Throwable t) {
            Log.e(TAG, "Menu launch FAILED: " + t);
            t.printStackTrace();

            synchronized (sLock) {
                sMenu = null;
            }
            // Allow retry after failure
            sLaunchAttempted.set(false);

            try {
                Toast.makeText(context,
                    "Mod menu failed: " + t.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
            } catch (Throwable ignored) { }
        }
    }

    /**
     * Cleanup — called from MainActivity.onDestroy()
     */
    public static void onDestroy() {
        Log.i(TAG, "Main.onDestroy() invoked");
        Menu menu;
        synchronized (sLock) {
            menu = sMenu;
            sMenu = null;
        }

        if (menu != null) {
            try {
                menu.onDestroy();
                Log.i(TAG, "Menu destroyed cleanly");
            } catch (Throwable t) {
                Log.w(TAG, "onDestroy error: " + t.getMessage());
            }
        }
        // Note: sLaunchAttempted stays TRUE (menu was created this session)
    }

    /** True if a Menu has already been created this session. */
    public static boolean hasLaunched() {
        return sLaunchAttempted.get();
    }

    /** Accessor for the current Menu (may be null). */
    public static Menu getMenu() {
        synchronized (sLock) {
            return sMenu;
        }
    }

    private Main() { }
}