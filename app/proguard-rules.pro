# ================================================================
# ModX Lab — Professional ProGuard Rules
# ================================================================

# Original rules
-keepclassmembers class ** { public static void Start (***); }
-keep public class com.android.support.MainActivity
-keepclassmembers class com.android.support.TitanicTextView {
    public void setMaskX(float);
    public void setMaskY(float);
    public float getMaskX();
    public float getMaskY();
}
-keep class com.android.support.TitanicTextView { *; }

# 🔒 JNI-bound classes — MUST keep
-keep class com.android.support.SecurityNative { *; }
-keep class com.android.support.Main             { *; }
-keep class com.android.support.MainActivity     { *; }
-keep class com.android.support.Menu             { *; }
-keep class com.android.support.LoginHelper      { *; }
-keep class com.android.support.LoginHelper$Callback { *; }
-keep class com.android.support.ESPView          { *; }
-keep class com.android.support.Preferences      { *; }
-keep class com.android.support.ModFirebase      { *; }
-keep class com.android.support.CrashHandler     { *; }
-keep class com.android.support.Launcher         { *; }
-keep class com.android.support.Titanic          { *; }

-keep class com.android.support.** { *; }
-keepclassmembers class com.android.support.** { *; }

-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Firebase / GMS
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# AndroidX
-keep class androidx.** { *; }
-dontwarn androidx.**

# Attributes
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes SourceFile,LineNumberTable

# Aggressive obfuscation
-repackageclasses 'o'
-allowaccessmodification
-overloadaggressively
-adaptclassstrings

# Strip logs (release only)
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}

-dontwarn kotlin.**
-dontwarn kotlinx.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-dontwarn javax.inject.**
-dontwarn org.jetbrains.annotations.**