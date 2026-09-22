# ================================================================
#  ModX Lab — ProGuard / R8 Rules
#  Location: app/proguard-rules.pro
# ================================================================

# ----------------------------------------------------------------
#  Your existing rules (kept as-is)
# ----------------------------------------------------------------
-keepclassmembers class ** {
    public static void Start (***);
}
-keep public class com.android.support.MainActivity

# TitanicTextView animation methods (prevent ProGuard from removing)
-keepclassmembers class com.android.support.TitanicTextView {
    public void setMaskX(float);
    public void setMaskY(float);
    public float getMaskX();
    public float getMaskY();
}
-keep class com.android.support.TitanicTextView { *; }


# ================================================================
#  🔥 NEW: Keep our own classes (Java + Login + Menu + Firebase bridge)
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

# All classes in our package — safest for a mod menu
-keep class com.android.support.** { *; }
-keepclassmembers class com.android.support.** { *; }


# ================================================================
#  🔥 Native methods (JNI calls from Main.cpp)
# ================================================================
-keepclasseswithmembernames class * {
    native <methods>;
}

# Keep all JNI-callable methods
-keepclasseswithmembers class * {
    native <methods>;
}


# ================================================================
#  🔥 Custom Views with XML constructors
# ================================================================
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}
-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}


# ================================================================
#  🔥 Firebase (Core, Auth, Database)
# ================================================================
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class com.google.android.gms.internal.** { *; }
-keep class com.google.firebase.database.** { *; }
-keep class com.google.firebase.auth.** { *; }

# Don't warn about missing Firebase / GMS classes
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**
-dontwarn com.google.android.gms.internal.**
-dontwarn com.google.firebase.database.**
-dontwarn com.google.firebase.auth.**


# ================================================================
#  🔥 Firebase Database — Generic type deserialization
#  (GenericTypeIndicator<HashMap<String,Object>> এর জন্য জরুরি)
# ================================================================
-keepattributes Signature
-keepattributes Exceptions
-keepattributes *Annotation*
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Keep generic info for Firebase data classes
-keep,allowobfuscation,allowshrinking class com.google.firebase.database.GenericTypeIndicator
-keep,allowobfuscation,allowshrinking class com.google.firebase.database.DataSnapshot

# Keep @PropertyName annotated fields/methods
-keepclassmembers class * {
    @com.google.firebase.database.PropertyName <fields>;
}
-keepclassmembers class * {
    @com.google.firebase.database.PropertyName <methods>;
}

# Keep classes with @IgnoreExtraProperties
-keepclassmembers class * {
    @com.google.firebase.database.IgnoreExtraProperties <fields>;
}

# Keep HashMap (used in our login code for Firebase data)
-keep class java.util.HashMap { *; }
-keep class java.util.ArrayList { *; }


# ================================================================
#  🔥 AndroidX
# ================================================================
-keep class androidx.** { *; }
-keep interface androidx.** { *; }
-dontwarn androidx.**


# ================================================================
#  🔥 Remove verbose logs in release (helps shrink dex)
# ================================================================
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}

# Keep error logs (useful for debugging release builds)
# -assumenosideeffects class android.util.Log {
#     public static *** e(...);
#     public static *** w(...);
# }


# ================================================================
#  🔥 Source file attributes (better crash reports)
# ================================================================
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile


# ================================================================
#  🔥 Misc warnings suppression (harmless dependencies)
# ================================================================
-dontwarn kotlin.**
-dontwarn kotlinx.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn javax.annotation.**
-dontwarn javax.inject.**


# ================================================================
#  🔥 Enum classes (Firebase Auth sometimes uses them)
# ================================================================
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}


# ================================================================
#  🔥 Parcelable (if any AndroidX lib needs it)
# ================================================================
-keepclassmembers class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}


# ================================================================
#  🔥 Reflection-safe — prevent R8 from stripping anonymous/inner classes
#  (firebase listeners use them heavily)
# ================================================================
-keepattributes Signature,RuntimeVisibleAnnotations,AnnotationDefault
-keep class * extends com.google.firebase.database.ValueEventListener { *; }
-keep class * implements com.google.firebase.database.ValueEventListener { *; }
-keep class * implements com.google.firebase.database.ChildEventListener { *; }
-keep class * implements com.google.firebase.database.DatabaseReference$CompletionListener { *; }


# ================================================================
#  🔥 WebView JavaScript interface (if used)
# ================================================================
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
