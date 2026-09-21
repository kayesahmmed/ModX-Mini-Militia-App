package com.android.support;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class MainActivity extends Activity {

    // 🔥 FIX: Mini Militia game activity
    public String GameActivity = "com.appsomniacs.da2.DA2Activity";
    public boolean hasLaunched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!hasLaunched) {
            try {
                hasLaunched = true;
                MainActivity.this.startActivity(
                    new Intent(MainActivity.this, Class.forName(MainActivity.this.GameActivity)));
                Main.Start(this);
                return;
            } catch (ClassNotFoundException e) {
                Log.e("Mod_menu", "Error. Game's main activity does not exist: " + GameActivity);
            }
        }

        Main.Start(this);
    }
}