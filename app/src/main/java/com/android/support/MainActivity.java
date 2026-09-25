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
        //    In release builds → enforces your cert hash
        //    In debug builds   → auto-passes for testing
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
        // 🔒 LAYER 2: Environment check
        //    Fails ONLY on real attacks (Frida/Xposed)
        //    Root/VPN/Emulator are soft-checked (allowed)
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
        // 🔒 LAYER 3: Clear any stored session if invalid
        //    Prevents stale/tampered tokens from being trusted
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
        // 🚀 Start the mod menu
        // ============================================================
        Main.Start(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Graceful shutdown — remove overlay views
        try {
            Main.onDestroy();
        } catch (Throwable ignored) { }
    }
}