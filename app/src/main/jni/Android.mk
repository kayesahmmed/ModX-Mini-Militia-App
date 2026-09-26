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

# ================================================================
# Compiler flags — symbol hiding + security hardening
# ================================================================
LOCAL_CFLAGS := \
    -w \
    -s \
    -Wno-error=format-security \
    -fvisibility=hidden \
    -fvisibility-inlines-hidden \
    -fpermissive \
    -fexceptions \
    -fstack-protector-strong \
    -D_FORTIFY_SOURCE=2

LOCAL_CPPFLAGS := \
    -w \
    -s \
    -Wno-error=format-security \
    -fvisibility=hidden \
    -fvisibility-inlines-hidden \
    -Werror \
    -std=c++17 \
    -Wno-error=c++11-narrowing \
    -fpermissive \
    -Wall \
    -fexceptions \
    -fno-rtti \
    -fstack-protector-strong \
    -D_FORTIFY_SOURCE=2

# ================================================================
# Linker flags — strip symbols, RELRO, NX
# ================================================================
LOCAL_LDFLAGS += \
    -Wl,--gc-sections \
    -Wl,--strip-all \
    -Wl,--exclude-libs,ALL \
    -Wl,-z,relro \
    -Wl,-z,now \
    -Wl,-z,noexecstack \
    -llog

# ================================================================
# Link libraries
# ================================================================
LOCAL_LDLIBS := -llog -landroid -lEGL -lGLESv2 -ldl

LOCAL_ARM_MODE := arm

LOCAL_C_INCLUDES += $(LOCAL_PATH)
LOCAL_C_INCLUDES += $(LOCAL_PATH)/Includes/

# ================================================================
# Source files
# ================================================================
LOCAL_SRC_FILES := \
    Main.cpp \
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