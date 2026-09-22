package com.android.support;

import android.content.Context;
import android.util.Log;

import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.FirebaseDatabase;

/**
 * Manages the mod's OWN FirebaseApp instance.
 * This prevents conflict with the host game's Firebase project
 * when our mod is injected into another APK.
 */
public final class ModFirebase {

    private static final String TAG = "ModXLab_Firebase";

    // ================================================================
    // 🔥 Firebase credentials — baked in from google-services.json
    // Project: modx-lab-5a6ee
    // ================================================================
    private static final String APP_NAME       = "modxlab_app";
    private static final String APPLICATION_ID = "1:554451964724:android:89f25a0b88f676c67ccb61";
    private static final String API_KEY        = "AIzaSyALZMuCJ0UE8dc0VCNqDbH-VgPvrfo4WJc";
    private static final String DATABASE_URL   = "https://modx-lab-5a6ee-default-rtdb.firebaseio.com";
    private static final String PROJECT_ID     = "modx-lab-5a6ee";
    private static final String GCM_SENDER_ID  = "554451964724";
    private static final String STORAGE_BUCKET = "modx-lab-5a6ee.firebasestorage.app";

    private static FirebaseApp sApp = null;

    private ModFirebase() { }

    public static synchronized FirebaseApp getApp(Context ctx) {
        if (sApp != null) return sApp;

        try {
            sApp = FirebaseApp.getInstance(APP_NAME);
            return sApp;
        } catch (IllegalStateException ignored) {
            // Not yet initialized — proceed
        }

        FirebaseOptions.Builder builder = new FirebaseOptions.Builder()
                .setApplicationId(APPLICATION_ID)
                .setApiKey(API_KEY)
                .setDatabaseUrl(DATABASE_URL)
                .setProjectId(PROJECT_ID)
                .setGcmSenderId(GCM_SENDER_ID);

        if (STORAGE_BUCKET != null && !STORAGE_BUCKET.isEmpty()) {
            builder.setStorageBucket(STORAGE_BUCKET);
        }

        try {
            sApp = FirebaseApp.initializeApp(ctx, builder.build(), APP_NAME);
            Log.i(TAG, "Mod Firebase initialized: " + APP_NAME);
        } catch (Exception e) {
            Log.e(TAG, "Firebase init failed: " + e);
        }
        return sApp;
    }

    public static FirebaseDatabase getDatabase(Context ctx) {
        FirebaseApp app = getApp(ctx);
        if (app == null) return null;
        return FirebaseDatabase.getInstance(app);
    }

    public static FirebaseAuth getAuth(Context ctx) {
        FirebaseApp app = getApp(ctx);
        if (app == null) return null;
        return FirebaseAuth.getInstance(app);
    }
}