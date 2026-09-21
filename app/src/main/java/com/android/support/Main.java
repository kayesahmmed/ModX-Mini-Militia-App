package com.android.support;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.widget.Toast;

public class Main {

    static {
        System.loadLibrary("ModXLab");
    }

    private static native void CheckOverlayPermission(Context context);

    // 🔥 NEW: Native crash log directory
    private static native void setNativeCrashDir(String dir);

    public static void StartWithoutPermission(Context context) {
        CrashHandler.init(context, true);
        if (context instanceof Activity) {
            Menu menu = new Menu(context);
            menu.SetWindowManagerActivity();
            menu.ShowMenu();
        } else {
            Toast.makeText(context, "Failed to launch the mod menu\n", Toast.LENGTH_LONG).show();
        }
    }

    public static void Start(Context context) {
        CrashHandler.init(context, false);

        // 🔥 Set native crash dir BEFORE starting game
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
}