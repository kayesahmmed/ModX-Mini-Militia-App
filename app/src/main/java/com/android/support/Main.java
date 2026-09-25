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

/**
 * ModX Lab — Main entry point
 *
 * Responsibilities:
 *   - Initialize crash handler
 *   - Set native crash-log directory
 *   - Check overlay permission (Android 6+)
 *   - Launch Menu overlay
 *   - Provide cleanup on Activity destroy
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
    // Menu instance tracking (for onDestroy)
    // ---------------------------------------------------------------
    private static Menu sMenu = null;

    // ---------------------------------------------------------------
    // Public API — called from MainActivity.onCreate()
    // ---------------------------------------------------------------
    public static void Start(Context context) {
        if (context == null) {
            Log.e(TAG, "Start() called with null context");
            return;
        }

        Log.i(TAG, "Main.Start() invoked");

        // Init crash handler
        CrashHandler.init(context, false);

        // Set native crash directory
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

        // Native permission check → will call back StartWithoutPermission()
        // when overlay permission is granted
        CheckOverlayPermission(context);
    }

    /**
     * Called from native (via JNI) after overlay permission is granted.
     * OR can be called directly from Java if you handle permission yourself.
     */
    public static void StartWithoutPermission(Context context) {
        if (context == null) {
            Log.e(TAG, "StartWithoutPermission() null context");
            return;
        }

        Log.i(TAG, "StartWithoutPermission() invoked");

        CrashHandler.init(context, true);

        if (!(context instanceof Activity)) {
            Toast.makeText(context, "Failed to launch the mod menu\n", Toast.LENGTH_LONG).show();
            return;
        }

        // Prevent double-launch
        if (sMenu != null) {
            Log.i(TAG, "Menu already running — skipping");
            return;
        }

        try {
            Menu menu = new Menu(context);
            sMenu = menu;

            menu.SetWindowManagerActivity();
            menu.ShowMenu();

            Log.i(TAG, "Menu launched successfully");
        } catch (Throwable t) {
            Log.e(TAG, "Menu launch FAILED: " + t);
            t.printStackTrace();
            sMenu = null;

            try {
                Toast.makeText(context,
                    "Mod menu failed: " + t.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
            } catch (Throwable ignored) { }
        }
    }

    /**
     * Cleanup — called from MainActivity.onDestroy()
     * Removes overlay views, stops animations, releases native refs.
     */
    public static void onDestroy() {
        Log.i(TAG, "Main.onDestroy() invoked");
        try {
            if (sMenu != null) {
                sMenu.onDestroy();
                sMenu = null;
                Log.i(TAG, "Menu destroyed cleanly");
            }
        } catch (Throwable t) {
            Log.w(TAG, "onDestroy error: " + t.getMessage());
        }
    }

    /** Accessor for internal checks (e.g. onResume fallback). */
    public static Menu getMenu() {
        return sMenu;
    }

    /** Prevent instantiation. */
    private Main() { }
}