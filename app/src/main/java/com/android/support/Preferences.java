package com.android.support;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.LinkedHashSet;
import java.util.Set;

public class Preferences {

    // ================================================================
    // Static state
    // ================================================================
    private static SharedPreferences sharedPreferences;
    private static Preferences prefsInstance;
    public  static Context context;

    /** Current SavePref setting — write gate. Toggle করলে সাথে সাথে update হয়। */
    public static boolean loadPref     = false;
    /** Expand toggle state */
    public static boolean isExpanded   = false;

    /**
     * ★ NEW: app start-এ একবার capture করা SavePref value — read gate.
     *
     * কেন দরকার?
     *  - mid-session এ user SavePref OFF করলে: loadPref=false হবে, কিন্তু
     *    sessionLoadPref আগের true থেকে যাবে → বর্তমান session-এ UI state
     *    অক্ষত থাকবে (menu rebuild হলেও)।
     *  - পরের বার app খুললে: sessionLoadPref = false হবে → সব default।
     */
    public static boolean sessionLoadPref = false;

    private static final String LENGTH              = "_length";
    private static final String DEFAULT_STRING_VALUE  = "";
    private static final int    DEFAULT_INT_VALUE     = 0;
    private static final double DEFAULT_DOUBLE_VALUE  = 0d;
    private static final float  DEFAULT_FLOAT_VALUE   = 0f;
    private static final long   DEFAULT_LONG_VALUE    = 0L;
    private static final boolean DEFAULT_BOOLEAN_VALUE = false;

    public static native void Changes(Context context, int featNum, String featName,
                                      int value, long Lvalue, boolean isOn, String inputText);

    // ================================================================
    // init() — Menu constructor-এর সবচেয়ে আগে একবার call হবে
    // ================================================================
    public static void init(Context ctx) {
        context = ctx;
        try {
            sharedPreferences = ctx.getApplicationContext().getSharedPreferences(
                    ctx.getPackageName() + "_preferences",
                    Context.MODE_PRIVATE
            );

            boolean savedLoadPref = sharedPreferences.getBoolean("-1", false);
            loadPref        = savedLoadPref;   // current write gate
            sessionLoadPref = savedLoadPref;   // ★ read gate — app lifecycle জুড়ে fixed
            isExpanded      = sharedPreferences.getBoolean("-3", false);
        } catch (Exception e) {
            loadPref        = false;
            sessionLoadPref = false;
            isExpanded      = false;
        }
    }

    // ================================================================
    // SAVE  (changeFeature*)
    // ─ loadPref ON  → disk-এ write + native Changes()
    // ─ loadPref OFF → শুধু native Changes(), disk-এ write নেই
    // ─ Except: -1 (SavePref), -3 (Expand) সবসময় disk-এ write হয়
    // ================================================================

    public static void changeFeatureInt(String featureName, int featureNum, int value) {
        try {
            if (loadPref) {
                Preferences.with(context).writeInt(featureNum, value);
            }
        } catch (Exception ignored) { }
        Changes(context, featureNum, featureName, value, 0, false, null);
    }

    public static void changeFeatureLong(String featureName, int featureNum, long Lvalue) {
        try {
            if (loadPref) {
                Preferences.with(context).writeLong(String.valueOf(featureNum), Lvalue);
            }
        } catch (Exception ignored) { }
        Changes(context, featureNum, featureName, 0, Lvalue, false, null);
    }

    public static void changeFeatureString(String featureName, int featureNum, String inputString) {
        try {
            if (loadPref) {
                Preferences.with(context).writeString(featureNum, inputString);
            }
        } catch (Exception ignored) { }
        Changes(context, featureNum, featureName, 0, 0, false, inputString);
    }

    public static void changeFeatureBool(String featureName, int featureNum, boolean bool) {
    // ── Special: SavePref toggle নিজে সবসময় persist করে ──
    if (featureNum == -1) {
        try {
            Preferences.with(context).writeBoolean(-1, bool);
        } catch (Exception ignored) { }
        loadPref = bool;                     // current write gate update


        if (bool) {
            sessionLoadPref = true;
        }

        Changes(context, featureNum, featureName, 0, 0, bool, null);
        return;
    }

    // ── Special: Expand toggle ──
    if (featureNum == -3) {
        try {
            Preferences.with(context).writeBoolean(-3, bool);
        } catch (Exception ignored) { }
        isExpanded = bool;
        Changes(context, featureNum, featureName, 0, 0, bool, null);
        return;
    }

    // ── Normal feature ──
    try {
        if (loadPref) {
            Preferences.with(context).writeBoolean(featureNum, bool);
        }
    } catch (Exception ignored) { }
    Changes(context, featureNum, featureName, 0, 0, bool, null);
}

    public static int loadPrefInt(String featureName, int featureNum) {
        int value = 0;
        try {
            if (sessionLoadPref && featureNum >= 0) {
                value = Preferences.with(context).readInt(featureNum);
            }
        } catch (Exception ignored) { }
        Changes(context, featureNum, featureName, value, 0, false, null);
        return value;
    }

    public static long loadPrefLong(String featureName, int featureNum) {
        long value = 0L;
        try {
            if (sessionLoadPref && featureNum >= 0) {
                value = Preferences.with(context).readLong(String.valueOf(featureNum));
            }
        } catch (Exception ignored) { }
        Changes(context, featureNum, featureName, 0, value, false, null);
        return value;
    }

    public static boolean loadPrefBool(String featureName, int featureNum, boolean bDef) {
        // ── SavePref toggle নিজে সবসময় disk থেকে read ──
        if (featureNum == -1) {
            boolean saved = false;
            try {
                saved = Preferences.with(context).readBoolean(-1, false);
            } catch (Exception ignored) { }
            loadPref = saved;                    // current write gate sync
            // ★ sessionLoadPref এখানে update করি না — এটা app lifecycle-এর জন্য fixed
            Changes(context, featureNum, featureName, 0, 0, saved, null);
            return saved;
        }
        // ── Expand toggle ──
        if (featureNum == -3) {
            boolean saved = false;
            try {
                saved = Preferences.with(context).readBoolean(-3, false);
            } catch (Exception ignored) { }
            isExpanded = saved;
            Changes(context, featureNum, featureName, 0, 0, saved, null);
            return saved;
        }
        // ── Normal feature ──
        boolean result = bDef;
        try {
            if (sessionLoadPref) {
                result = Preferences.with(context).readBoolean(featureNum, bDef);
            }
        } catch (Exception ignored) { }
        Changes(context, featureNum, featureName, 0, 0, result, null);
        return result;
    }

    public static String loadPrefString(String featureName, int featureNum) {
        String result = "";
        try {
            if (sessionLoadPref && featureNum > 0) {
                result = Preferences.with(context).readString(featureNum);
            }
        } catch (Exception ignored) { }
        Changes(context, featureNum, featureName, 0, 0, false, result);
        return result;
    }

    // ================================================================
    // Constructors
    // ================================================================
    private Preferences(Context context) {
        sharedPreferences = context.getApplicationContext().getSharedPreferences(
                context.getPackageName() + "_preferences",
                Context.MODE_PRIVATE
        );
    }

    private Preferences(Context context, String preferencesName) {
        sharedPreferences = context.getApplicationContext().getSharedPreferences(
                preferencesName,
                Context.MODE_PRIVATE
        );
    }

    // ================================================================
    // Singleton accessors
    // ================================================================
    public static Preferences with(Context context) {
        if (prefsInstance == null) {
            prefsInstance = new Preferences(context);
        }
        return prefsInstance;
    }

    public static Preferences with(Context context, boolean forceInstantiation) {
        if (forceInstantiation) {
            prefsInstance = new Preferences(context);
        }
        return prefsInstance;
    }

    public static Preferences with(Context context, String preferencesName) {
        if (prefsInstance == null) {
            prefsInstance = new Preferences(context, preferencesName);
        }
        return prefsInstance;
    }

    public static Preferences with(Context context, String preferencesName,
                                   boolean forceInstantiation) {
        if (forceInstantiation) {
            prefsInstance = new Preferences(context, preferencesName);
        }
        return prefsInstance;
    }

    // ================================================================
    // String related methods
    // ================================================================
    public String readString(String what) {
        return sharedPreferences.getString(what, DEFAULT_STRING_VALUE);
    }

    public String readString(int what) {
        try {
            return sharedPreferences.getString(String.valueOf(what), DEFAULT_STRING_VALUE);
        } catch (java.lang.ClassCastException ex) {
            return "";
        }
    }

    public String readString(String what, String defaultString) {
        return sharedPreferences.getString(what, defaultString);
    }

    public void writeString(String where, String what) {
        sharedPreferences.edit().putString(where, what).apply();
    }

    public void writeString(int where, String what) {
        sharedPreferences.edit().putString(String.valueOf(where), what).apply();
    }

    // ================================================================
    // Int related methods
    // ================================================================
    public int readInt(String what) {
        return sharedPreferences.getInt(what, DEFAULT_INT_VALUE);
    }

    public int readInt(int what) {
        try {
            return sharedPreferences.getInt(String.valueOf(what), DEFAULT_INT_VALUE);
        } catch (java.lang.ClassCastException ex) {
            return 0;
        }
    }

    public int readInt(String what, int defaultInt) {
        return sharedPreferences.getInt(what, defaultInt);
    }

    public void writeInt(String where, int what) {
        sharedPreferences.edit().putInt(where, what).apply();
    }

    public void writeInt(int where, int what) {
        sharedPreferences.edit().putInt(String.valueOf(where), what).apply();
    }

    // ================================================================
    // Double related methods
    // ================================================================
    public double readDouble(String what) {
        if (!contains(what))
            return DEFAULT_DOUBLE_VALUE;
        return Double.longBitsToDouble(readLong(what));
    }

    public double readDouble(String what, double defaultDouble) {
        if (!contains(what))
            return defaultDouble;
        return Double.longBitsToDouble(readLong(what));
    }

    public void writeDouble(String where, double what) {
        writeLong(where, Double.doubleToRawLongBits(what));
    }

    // ================================================================
    // Float related methods
    // ================================================================
    public float readFloat(String what) {
        return sharedPreferences.getFloat(what, DEFAULT_FLOAT_VALUE);
    }

    public float readFloat(String what, float defaultFloat) {
        return sharedPreferences.getFloat(what, defaultFloat);
    }

    public void writeFloat(String where, float what) {
        sharedPreferences.edit().putFloat(where, what).apply();
    }

    // ================================================================
    // Long related methods
    // ================================================================
    public long readLong(String what) {
        return sharedPreferences.getLong(what, DEFAULT_LONG_VALUE);
    }

    public long readLong(String what, long defaultLong) {
        return sharedPreferences.getLong(what, defaultLong);
    }

    public void writeLong(String where, long what) {
        sharedPreferences.edit().putLong(where, what).apply();
    }

    // ================================================================
    // Boolean related methods
    // ================================================================
    public boolean readBoolean(String what) {
        return sharedPreferences.getBoolean(what, DEFAULT_BOOLEAN_VALUE);
    }

    public boolean readBoolean(int what) {
        return sharedPreferences.getBoolean(String.valueOf(what), DEFAULT_BOOLEAN_VALUE);
    }

    public boolean readBoolean(String what, boolean defaultBoolean) {
        return sharedPreferences.getBoolean(what, defaultBoolean);
    }

    public boolean readBoolean(int what, boolean defaultBoolean) {
        try {
            return sharedPreferences.getBoolean(String.valueOf(what), defaultBoolean);
        } catch (java.lang.ClassCastException ex) {
            return defaultBoolean;
        }
    }

    public void writeBoolean(String where, boolean what) {
        sharedPreferences.edit().putBoolean(where, what).apply();
    }

    public void writeBoolean(int where, boolean what) {
        sharedPreferences.edit().putBoolean(String.valueOf(where), what).apply();
    }

    // ================================================================
    // String set methods
    // ================================================================
    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public void putStringSet(final String key, final Set<String> value) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            sharedPreferences.edit().putStringSet(key, value).apply();
        } else {
            putOrderedStringSet(key, value);
        }
    }

    public void putOrderedStringSet(String key, Set<String> value) {
        int stringSetLength = 0;
        if (sharedPreferences.contains(key + LENGTH)) {
            stringSetLength = readInt(key + LENGTH);
        }
        writeInt(key + LENGTH, value.size());
        int i = 0;
        for (String aValue : value) {
            writeString(key + "[" + i + "]", aValue);
            i++;
        }
        for (; i < stringSetLength; i++) {
            remove(key + "[" + i + "]");
        }
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    public Set<String> getStringSet(final String key, final Set<String> defValue) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            return sharedPreferences.getStringSet(key, defValue);
        } else {
            return getOrderedStringSet(key, defValue);
        }
    }

    public Set<String> getOrderedStringSet(String key, final Set<String> defValue) {
        if (contains(key + LENGTH)) {
            LinkedHashSet<String> set = new LinkedHashSet<>();
            int stringSetLength = readInt(key + LENGTH);
            if (stringSetLength >= 0) {
                for (int i = 0; i < stringSetLength; i++) {
                    set.add(readString(key + "[" + i + "]"));
                }
            }
            return set;
        }
        return defValue;
    }

    // ================================================================
    // Utility methods
    // ================================================================
    public void remove(final String key) {
        if (contains(key + LENGTH)) {
            int stringSetLength = readInt(key + LENGTH);
            if (stringSetLength >= 0) {
                sharedPreferences.edit().remove(key + LENGTH).apply();
                for (int i = 0; i < stringSetLength; i++) {
                    sharedPreferences.edit().remove(key + "[" + i + "]").apply();
                }
            }
        }
        sharedPreferences.edit().remove(key).apply();
    }

    public boolean contains(final String key) {
        return sharedPreferences.contains(key);
    }

    public void clear() {
        sharedPreferences.edit().clear().apply();
    }
}