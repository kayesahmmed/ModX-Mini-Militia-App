package com.android.support;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.google.firebase.FirebaseApp;

public class MainActivity extends Activity {

    // 🔥 Mini Militia game activity
    public String GameActivity = "com.appsomniacs.da2.DA2Activity";
    public boolean hasLaunched = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Firebase initialize — floating menu এর login এর জন্য জরুরি
        FirebaseApp.initializeApp(this);

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
