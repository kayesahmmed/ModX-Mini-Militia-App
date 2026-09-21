#include "Includes/obfuscate.h"
#include "Menu/Jni.hpp"
#include "Menu/Menu.hpp"
#include "Includes/Utils.hpp"
#include <jni.h>

extern "C" JNIEXPORT void JNICALL
Java_com_android_support_Menu_Draw(JNIEnv*, jclass, jobject, jobject);

// ================================================================
// Native Crash Dir Setter — Java থেকে path সেট করার জন্য
// ================================================================
extern "C" JNIEXPORT void JNICALL
Java_com_android_support_Main_setNativeCrashDir(JNIEnv*, jclass, jstring);

void Init(JNIEnv *env, jobject thiz, jobject ctx, jobject title, jobject subtitle) {
    //Set title
    setText(env, title, OBFUSCATE("<b>ModX Lab</b>"));

    //Set sub title
    setText(env, subtitle, OBFUSCATE("<b><marquee><p style=\"font-size:30\">"
                                     "<p style=\"color:green;\">Created by ModX Lab</p>"
                                     "</marquee></b>"));

    //Toast Example
    Toast(env, ctx, OBFUSCATE("Created by ModX Lab"), ToastLength::LENGTH_LONG);
}

int RegisterMenu(JNIEnv *env) {
    JNINativeMethod methods[] = {
            {OBFUSCATE("Icon"),            OBFUSCATE("()Ljava/lang/String;"),
                reinterpret_cast<void *>(Icon)},
            {OBFUSCATE("IconWebViewData"), OBFUSCATE("()Ljava/lang/String;"),
                reinterpret_cast<void *>(IconWebViewData)},
            {OBFUSCATE("IsGameLibLoaded"), OBFUSCATE("()Z"),
                reinterpret_cast<void *>(isGameLibLoaded)},
            {OBFUSCATE("Init"),
                OBFUSCATE("(Landroid/content/Context;Landroid/widget/TextView;Landroid/widget/TextView;)V"),
                reinterpret_cast<void *>(Init)},
            {OBFUSCATE("SettingsList"),    OBFUSCATE("()[Ljava/lang/String;"),
                reinterpret_cast<void *>(SettingsList)},
            {OBFUSCATE("GetFeatureList"),  OBFUSCATE("()[Ljava/lang/String;"),
                reinterpret_cast<void *>(GetFeatureList)},

            // ESP draw callback
            {OBFUSCATE("Draw"),
                OBFUSCATE("(Lcom/android/support/ESPView;Landroid/graphics/Canvas;)V"),
                reinterpret_cast<void *>(Java_com_android_support_Menu_Draw)},
    };

    jclass clazz = env->FindClass(OBFUSCATE("com/android/support/Menu"));
    if (!clazz)
        return JNI_ERR;
    if (env->RegisterNatives(clazz, methods, sizeof(methods) / sizeof(methods[0])) != 0)
        return JNI_ERR;
    return JNI_OK;
}

int RegisterPreferences(JNIEnv *env) {
    JNINativeMethod methods[] = {
            {OBFUSCATE("Changes"),
                OBFUSCATE("(Landroid/content/Context;ILjava/lang/String;IJZLjava/lang/String;)V"),
                reinterpret_cast<void *>(Changes)},
    };
    jclass clazz = env->FindClass(OBFUSCATE("com/android/support/Preferences"));
    if (!clazz)
        return JNI_ERR;
    if (env->RegisterNatives(clazz, methods, sizeof(methods) / sizeof(methods[0])) != 0)
        return JNI_ERR;
    return JNI_OK;
}

int RegisterMain(JNIEnv *env) {
    JNINativeMethod methods[] = {
            {OBFUSCATE("CheckOverlayPermission"),
                OBFUSCATE("(Landroid/content/Context;)V"),
                reinterpret_cast<void *>(CheckOverlayPermission)},

            // ============================================================
            // Native crash dir setter
            // ============================================================
            {OBFUSCATE("setNativeCrashDir"),
                OBFUSCATE("(Ljava/lang/String;)V"),
                reinterpret_cast<void *>(Java_com_android_support_Main_setNativeCrashDir)},
    };
    jclass clazz = env->FindClass(OBFUSCATE("com/android/support/Main"));
    if (!clazz)
        return JNI_ERR;
    if (env->RegisterNatives(clazz, methods, sizeof(methods) / sizeof(methods[0])) != 0)
        return JNI_ERR;

    return JNI_OK;
}

extern "C"
JNIEXPORT jint JNICALL
JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    vm->GetEnv((void **) &env, JNI_VERSION_1_6);
    if (RegisterMenu(env) != 0)
        return JNI_ERR;
    if (RegisterPreferences(env) != 0)
        return JNI_ERR;
    if (RegisterMain(env) != 0)
        return JNI_ERR;
    return JNI_VERSION_1_6;
}