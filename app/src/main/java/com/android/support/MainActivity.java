package com.android.support;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

public class MainActivity extends Activity {

    /** Mini Militia game activity */
    public String GameActivity = "com.appsomniacs.da2.DA2Activity";
    public boolean hasLaunched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ============================================================
        // 🔒 LAYER 1: APK Signature verification
        // ============================================================
        if (!SecurityNative.verifyApkSignatureOrDebug(this)) {
            Log.e("Mod_security", "APK signature mismatch — refusing to run");
            try {
                Toast.makeText(this, "Invalid build signature", Toast.LENGTH_LONG).show();
            } catch (Throwable ignored) { }
            finishAffinity();
            System.exit(0);
            return;
        }

        // ============================================================
        // 🔒 LAYER 2: Environment check (Frida/Xposed only)
        // ============================================================
        if (!SecurityNative.isEnvironmentValid()) {
            Log.e("Mod_security", "Unsafe environment detected — aborting");
            try {
                Toast.makeText(this, "Security check failed", Toast.LENGTH_LONG).show();
            } catch (Throwable ignored) { }
            finishAffinity();
            System.exit(0);
            return;
        }

        // ============================================================
        // 🔒 LAYER 2.5: DEX Integrity Verification
        //    Detects any modification of classes.dex — even if the
        //    attacker re-signs with the same key.
        //    Skipped in DEBUG builds for dev convenience.
        // ============================================================
        if (!com.android.support.BuildConfig.DEBUG) {
            try {
                String dexHash = SecurityNative.computeDexHash(this);
                if (dexHash == null || dexHash.isEmpty()) {
                    Log.e("Mod_security", "DEX hash computation failed — aborting");
                    finishAffinity();
                    System.exit(0);
                    return;
                }
                if (!SecurityNative.verifyDexHash(dexHash)) {
                    Log.e("Mod_security", "DEX integrity check FAILED — APK tampered");
                    try {
                        Toast.makeText(this, "Integrity check failed", Toast.LENGTH_LONG).show();
                    } catch (Throwable ignored) { }
                    finishAffinity();
                    System.exit(0);
                    return;
                }
                Log.i("Mod_security", "DEX integrity OK");
            } catch (Throwable t) {
                Log.e("Mod_security", "DEX check error: " + t.getMessage());
                finishAffinity();
                System.exit(0);
                return;
            }
        }

        // ============================================================
        // 🔒 LAYER 3: Session token validation
        // ============================================================
        try {
            String token  = getSharedPreferences("KEY", MODE_PRIVATE).getString("token", "");
            String user   = getSharedPreferences("KEY", MODE_PRIVATE).getString("User", "");
            String pass   = getSharedPreferences("save", MODE_PRIVATE).getString("edittext2", "");
            String expiry = getSharedPreferences("KEY", MODE_PRIVATE).getString("expiry", "");

            if (token != null && !token.isEmpty()) {
                boolean valid = SecurityNative.verifySessionToken(token, user, pass, expiry);
                if (!valid) {
                    Log.w("Mod_security", "Stored session token invalid — clearing");
                    getSharedPreferences("KEY", MODE_PRIVATE).edit().clear().apply();
                }
            }
        } catch (Throwable t) {
            Log.w("Mod_security", "Session check error: " + t.getMessage());
        }

        // ============================================================
        // 🎮 Launch Mini Militia game
        // ============================================================
        if (!hasLaunched) {
            hasLaunched = true;
            try {
                Intent launch = new Intent(MainActivity.this, Class.forName(GameActivity));
                startActivity(launch);
            } catch (ClassNotFoundException e) {
                Log.e("Mod_menu", "Game activity not found: " + GameActivity);
            }
        }

        // ============================================================
        // 🚀 Start the mod menu (single call — native callback handles rest)
        // ============================================================
        Main.Start(this);
    }

    // NOTE: onResume fallback removed — caused duplicate Menu instances.

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            Main.onDestroy();
        } catch (Throwable t) {
            Log.w("Mod_menu", "Main.onDestroy error: " + t.getMessage());
        }
    }
}