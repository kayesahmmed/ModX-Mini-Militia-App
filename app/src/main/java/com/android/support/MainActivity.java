package com.android.support;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

public class MainActivity extends Activity {

    public String GameActivity = "com.appsomniacs.da2.DA2Activity";
    public boolean hasLaunched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Silent runtime gate — no user-visible strings, no logs.
        // If any check fails, the process is killed immediately with
        // no clue to the attacker about what triggered the failure.
        if (!SecurityNative.preload(this, BuildConfig.DEBUG)) {
            android.os.Process.killProcess(android.os.Process.myPid());
            return;
        }

        if (!hasLaunched) {
            hasLaunched = true;
            try {
                startActivity(new Intent(MainActivity.this, Class.forName(GameActivity)));
            } catch (ClassNotFoundException e) {
                // Game not found — silently continue
            }
        }

        Main.Start(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            Main.onDestroy();
        } catch (Throwable ignored) {
        }
    }
}