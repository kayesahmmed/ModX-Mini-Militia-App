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

        // 🔒 Layer 1: signature
        if (!SecurityNative.verifyApkSignatureOrDebug(this)) {
            Log.e("Mod_menu", "Signature mismatch");
            try { Toast.makeText(this, "Invalid signature", Toast.LENGTH_LONG).show(); } catch (Throwable t) {}
            finishAffinity(); System.exit(0); return;
        }

        // 🔒 Layer 2: environment (root/frida/debugger/emulator)
        //if (!SecurityNative.isEnvironmentValid()) {
            //Log.e("Mod_menu", "Unsafe environment");
            //finishAffinity(); System.exit(0); return;
       // }

        // 🔒 Layer 3: verify stored session token (if "remember me")
        String storedToken = getSharedPreferences("KEY", MODE_PRIVATE).getString("token", "");
        if (storedToken != null && !storedToken.isEmpty()) {
            String u = getSharedPreferences("KEY", MODE_PRIVATE).getString("User", "");
            String p = getSharedPreferences("save", MODE_PRIVATE).getString("edittext2", "");
            String e = getSharedPreferences("KEY", MODE_PRIVATE).getString("expiry", "");
            if (!SecurityNative.verifySessionToken(storedToken, u, p, e)) {
                Log.w("Mod_menu", "Stored token invalid — clearing");
                getSharedPreferences("KEY", MODE_PRIVATE).edit().clear().apply();
            }
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
