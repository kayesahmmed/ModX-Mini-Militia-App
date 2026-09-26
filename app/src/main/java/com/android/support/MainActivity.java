package com.android.support;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

public class MainActivity extends Activity {

    public String GameActivity = "com.appsomniacs.da2.DA2Activity";
    public boolean hasLaunched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ============================================================
        // ⚠️ DEBUG BUILD WARNING
        //    Security checks are DISABLED in debug builds.
        //    This warning ensures devs never accidentally ship a debug APK.
        // ============================================================
        if (BuildConfig.DEBUG) {
            try {
                new AlertDialog.Builder(this)
                    .setTitle("⚠️ DEBUG BUILD")
                    .setMessage(
                        "This is a DEBUG build.\n\n" +
                        "Security checks are DISABLED:\n" +
                        "• APK signature verify — OFF\n" +
                        "• DEX integrity — OFF\n" +
                        "• Cert pinning — OFF\n\n" +
                        "DO NOT distribute this APK.")
                    .setPositiveButton("I Understand", new DialogInterface.OnClickListener() {
                        @Override public void onClick(DialogInterface d, int w) { d.dismiss(); }
                    })
                    .setCancelable(false)
                    .show();
            } catch (Throwable ignored) { }
        }

        // ============================================================
        // 🔒 SILENT SECURITY GATE
        //    All checks live in native. Silent kill on failure.
        // ============================================================
        if (!SecurityNative.preload(this, BuildConfig.DEBUG)) {
            android.os.Process.killProcess(android.os.Process.myPid());
            return;
        }

        // ============================================================
        // 🕐 NTP TIME SYNC (background)
        //    Prevents device-clock-based expiry bypass.
        // ============================================================
        try {
            NtpTime.refreshSync(this);
        } catch (Throwable t) {
            Log.w("Mod_main", "NTP sync failed: " + t.getMessage());
        }

        // ============================================================
        // 🔒 NATIVE LIB INTEGRITY (release only)
        //    Verifies libModXLab.so hasn't been replaced.
        // ============================================================
        if (!BuildConfig.DEBUG) {
            try {
                String libHash = SecurityNative.computeNativeLibHash(this);
                if (libHash == null || libHash.isEmpty()) {
                    android.os.Process.killProcess(android.os.Process.myPid());
                    return;
                }
                if (!SecurityNative.verifyLibHash(libHash)) {
                    android.os.Process.killProcess(android.os.Process.myPid());
                    return;
                }
            } catch (Throwable t) {
                android.os.Process.killProcess(android.os.Process.myPid());
                return;
            }
        }

        // ============================================================
        // 🎮 Launch Mini Militia
        // ============================================================
        if (!hasLaunched) {
            hasLaunched = true;
            try {
                startActivity(new Intent(MainActivity.this, Class.forName(GameActivity)));
            } catch (ClassNotFoundException e) { }
        }

        // ============================================================
        // 🚀 Start mod menu
        // ============================================================
        Main.Start(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try { Main.onDestroy(); } catch (Throwable ignored) { }
    }
}