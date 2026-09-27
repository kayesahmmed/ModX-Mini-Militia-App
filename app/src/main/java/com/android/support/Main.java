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
 * Thread-safe single-launch guarantee.
 */
public class Main {

    private static final String TAG = "Mod_Main";

    static {
        try {
            System.loadLibrary("ModXLab");
        } catch (Throwable t) {
            Log.e(TAG, "native load failed: " + t.getMessage());
        }
    }

    private static native void CheckOverlayPermission(Context context);
    private static native void setNativeCrashDir(String dir);

    private static final Object sLock = new Object();
    private static Menu sMenu = null;
    private static final AtomicBoolean sLaunchAttempted = new AtomicBoolean(false);

    private Main() { }

    // ==================================================================
    // Public API
    // ==================================================================

    public static void Start(Context context) {
        if (context == null) return;

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

        CheckOverlayPermission(context);
    }

    public static void StartWithoutPermission(Context context) {
        if (context == null) return;

        // Atomic single-launch guard
        if (!sLaunchAttempted.compareAndSet(false, true)) return;

        CrashHandler.init(context, true);

        if (!(context instanceof Activity)) {
            Toast.makeText(context,
                "Failed to launch the mod menu\n",
                Toast.LENGTH_LONG).show();
            sLaunchAttempted.set(false);
            return;
        }

        try {
            Menu menu = new Menu(context);
            synchronized (sLock) { sMenu = menu; }
            menu.SetWindowManagerActivity();
            menu.ShowMenu();
        } catch (Throwable t) {
            Log.e(TAG, "Menu launch failed: " + t);
            synchronized (sLock) { sMenu = null; }
            sLaunchAttempted.set(false);
            try {
                Toast.makeText(context,
                    "Mod menu failed: " + t.getClass().getSimpleName(),
                    Toast.LENGTH_LONG).show();
            } catch (Throwable ignored) { }
        }
    }

    public static void onDestroy() {
        Menu menu;
        synchronized (sLock) {
            menu = sMenu;
            sMenu = null;
        }
        if (menu != null) {
            try { menu.onDestroy(); } catch (Throwable ignored) { }
        }
    }

    public static Menu getMenu() {
        synchronized (sLock) { return sMenu; }
    }

    public static boolean hasLaunched() {
        return sLaunchAttempted.get();
    }
}