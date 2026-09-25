package com.android.support;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

public class MainActivity extends Activity {

    public String GameActivity = "com.appsomniacs.da2.DA2Activity";
    public boolean hasLaunched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 🔒 Anti-tamper — debug build এ auto-pass, release এ enforce
        if (!SecurityNative.verifyApkSignatureOrDebug(this)) {
            Log.e("Mod_menu", "Signature mismatch — refusing to run.");
            try {
                Toast.makeText(this, "Invalid build signature", Toast.LENGTH_LONG).show();
            } catch (Throwable ignored) { }
            finishAffinity();
            System.exit(0);
            return;
        }

        if (!SecurityNative.isEnvironmentValid()) {
            Log.e("Mod_menu", "Suspicious environment detected.");
            finishAffinity();
            System.exit(0);
            return;
        }

        if (!hasLaunched) {
            hasLaunched = true;
            try {
                startActivity(new Intent(MainActivity.this, Class.forName(GameActivity)));
            } catch (ClassNotFoundException e) {
                Log.e("Mod_menu", "Game activity not found: " + GameActivity);
            }
        }

        Main.Start(this);
    }
}