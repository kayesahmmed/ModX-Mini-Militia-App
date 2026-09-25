LOCAL_PATH := $(call my-dir)
include $(CLEAR_VARS)

# Kittymemory
KITTYMEMORY_PATH = KittyMemory
include $(CLEAR_VARS)
LOCAL_MODULE    := Keystone
LOCAL_SRC_FILES := $(KITTYMEMORY_PATH)/Deps/Keystone/libs-android/$(TARGET_ARCH_ABI)/libkeystone.a
include $(PREBUILT_STATIC_LIBRARY)

# ================================================================
# Mod Menu Library
# ================================================================
include $(CLEAR_VARS)
LOCAL_MODULE    := ModXLab

LOCAL_CFLAGS := -w -s -Wno-error=format-security -fvisibility=hidden -fpermissive -fexceptions
LOCAL_CPPFLAGS := -w -s -Wno-error=format-security -fvisibility=hidden -Werror -std=c++17
LOCAL_CPPFLAGS += -Wno-error=c++11-narrowing -fpermissive -Wall -fexceptions
LOCAL_LDFLAGS += -Wl,--gc-sections,--strip-all,-llog

LOCAL_LDLIBS := -llog -landroid -lEGL -lGLESv2 -ldl

LOCAL_ARM_MODE := arm

LOCAL_C_INCLUDES += $(LOCAL_PATH)
LOCAL_C_INCLUDES += $(LOCAL_PATH)/Includes/

LOCAL_SRC_FILES := Main.cpp \
    Security.cpp \
    Menu/Jni.cpp \
    Menu/Menu.cpp \
    Menu/Setup.cpp \
    Includes/Utils.cpp \
	Substrate/hde64.c \
	Substrate/SubstrateDebug.cpp \
	Substrate/SubstrateHook.cpp \
	Substrate/SubstratePosixMemory.cpp \
	Substrate/SymbolFinder.cpp \
	KittyMemory/KittyArm64.cpp \
    KittyMemory/KittyScanner.cpp \
    KittyMemory/KittyMemory.cpp \
    KittyMemory/KittyUtils.cpp \
    KittyMemory/MemoryPatch.cpp \
    KittyMemory/MemoryBackup.cpp \
	And64InlineHook/And64InlineHook.cpp \

LOCAL_STATIC_LIBRARIES := Keystone

include $(BUILD_SHARED_LIBRARY)