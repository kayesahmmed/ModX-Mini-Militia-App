# ================================================================
#  ModX Lab — ProGuard Rules (Light)
# ================================================================

# ----------------------------------------------------------------
#  Original rules (kept)
# ----------------------------------------------------------------
-keepclassmembers class ** {
    public static void Start (***);
}
-keep public class com.android.support.MainActivity

-keepclassmembers class com.android.support.TitanicTextView {
    public void setMaskX(float);
    public void setMaskY(float);
    public float getMaskX();
    public float getMaskY();
}
-keep class com.android.support.TitanicTextView { *; }


# ================================================================
#  Keep our own classes
# ================================================================
-keep class com.android.support.Main { *; }
-keep class com.android.support.MainActivity { *; }
-keep class com.android.support.Menu { *; }
-keep class com.android.support.LoginHelper { *; }
-keep class com.android.support.LoginHelper$Callback { *; }
-keep class com.android.support.ESPView { *; }
-keep class com.android.support.Preferences { *; }
-keep class com.android.support.CrashHandler { *; }
-keep class com.android.support.Launcher { *; }

-keep class com.android.support.** { *; }
-keepclassmembers class com.android.support.** { *; }


# ================================================================
#  Native methods
# ================================================================
-keepclasseswithmembernames class * {
    native <methods>;
}


# ================================================================
#  Firebase (Light keep)
# ================================================================
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod


# ================================================================
#  AndroidX
# ================================================================
-keep class androidx.** { *; }
-dontwarn androidx.**


# ================================================================
#  Misc
# ================================================================
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-dontwarn kotlin.**
-dontwarn kotlinx.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn javax.inject.**