package com.android.support;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

import java.util.LinkedHashSet;
import java.util.Set;

public class Preferences {
    private static SharedPreferences sharedPreferences;
    private static Preferences prefsInstance;
    public static Context context;
    public static boolean loadPref, isExpanded;

    private static final String LENGTH = "_length";
    private static final String DEFAULT_STRING_VALUE = "";
    private static final int DEFAULT_INT_VALUE = 0;
    private static final double DEFAULT_DOUBLE_VALUE = 0d;
    private static final float DEFAULT_FLOAT_VALUE = 0f;
    private static final long DEFAULT_LONG_VALUE = 0L;
    private static final boolean DEFAULT_BOOLEAN_VALUE = false;

    public static native void Changes(Context context, int featNum, String featName,
                                      int value, long Lvalue, boolean isOn, String inputText);

    // ================================================================
    // ★ NEW: App start-এ একবার call হবে — সব feature build হওয়ার আগে।
    //   এটা নিশ্চিত করে loadPref static field disk-এর actual value থেকে
    //   set হয়, যাতে Menu constructor-এর feature widget গুলো সঠিকভাবে
    //   load/save করতে পারে।
    // ================================================================
    public static void init(Context ctx) {
        context = ctx;
        try {
            sharedPreferences = ctx.getApplicationContext().getSharedPreferences(
                    ctx.getPackageName() + "_preferences",
                    Context.MODE_PRIVATE
            );
            // ★ SavePref state disk থেকে সাথে সাথে read করে static-এ রাখি
            loadPref = sharedPreferences.getBoolean("-1", false);
            isExpanded = sharedPreferences.getBoolean("-3", false);
        } catch (Exception ignored) {
            loadPref = false;
            isExpanded = false;
        }
    }

    // ================================================================
    // Feature write helpers
    // ★ SavePref OFF থাকলে disk-এ write করি না।
    //   তবে native `Changes()` সবসময় call করি, যাতে runtime-এ hook apply হয়।
    // ================================================================
    public static void changeFeatureInt(String featureName, int featureNum, int value) {
        if (loadPref) {
            Preferences.with(context).writeInt(featureNum, value);
        }
        Changes(context, featureNum, featureName, value, 0, false, null);
    }

    public static void changeFeatureLong(String featureName, int featureNum, long Lvalue) {
        if (loadPref) {
            Preferences.with(context).writeLong(String.valueOf(featureNum), Lvalue);
        }
        Changes(context, featureNum, featureName, 0, Lvalue, false, null);
    }

    public static void changeFeatureString(String featureName, int featureNum, String inputString) {
        if (loadPref) {
            Preferences.with(context).writeString(featureNum, inputString);
        }
        Changes(context, featureNum, featureName, 0, 0, false, inputString);
    }

    public static void changeFeatureBool(String featureName, int featureNum, boolean bool) {
        if (loadPref) {
            Preferences.with(context).writeBoolean(featureNum, bool);
        }
        Changes(context, featureNum, featureName, 0, 0, bool, null);
    }

    // ================================================================
    // Feature load helpers
    // ★ SavePref OFF থাকলে all-time 0 / default return করি,
    //   নাহলে disk-এ save করা value read করি।
    // ================================================================
    public static int loadPrefInt(String featureName, int featureNum) {
        if (!loadPref && featureNum >= 0) return 0;
        int value = Preferences.with(context).readInt(featureNum);
        if (loadPref || featureNum < 0) {
            Changes(context, featureNum, featureName, value, 0, false, null);
            return value;
        }
        return 0;
    }

    public static long loadPrefLong(String featureName, int featureNum) {
        if (!loadPref && featureNum >= 0) return 0L;
        long Lvalue = Preferences.with(context).readLong(String.valueOf(featureNum));
        if (loadPref || featureNum < 0) {
            Changes(context, featureNum, featureName, 0, Lvalue, false, null);
            return Lvalue;
        }
        return 0L;
    }

    public static boolean loadPrefBool(String featureName, int featureNum, boolean bDef) {
        boolean bool = Preferences.with(context).readBoolean(featureNum, bDef);

        // Special features (SavePref toggle and expand toggle)
        if (featureNum == -1) {
            loadPref = bool;
            return bool;
        }
        if (featureNum == -3) {
            isExpanded = bool;
            return bool;
        }

        // ★ loadPref ON থাকলে disk-এ save করা value, নাহলে default
        if (loadPref) {
            bDef = bool;
        }

        Changes(context, featureNum, featureName, 0, 0, bDef, null);
        return bDef;
    }

    public static String loadPrefString(String featureName, int featureNum) {
        if (!loadPref && featureNum > 0) return "";
        String text = Preferences.with(context).readString(featureNum);
        if (loadPref || featureNum <= 0) {
            Changes(context, featureNum, featureName, 0, 0, false, text);
            return text;
        }
        return "";
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