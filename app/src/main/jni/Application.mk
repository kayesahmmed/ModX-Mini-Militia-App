# To AIDE Users: If you are using 32-bit/ARMv7 phone, please remove arm64-v8a
APP_ABI := armeabi-v7a arm64-v8a

# 🔥 FIXED: APP_PLATFORM must be uncommented and set to android-21+
# android-21 = Android 5.0 (Lollipop)
# std::atomic<float> and other C++17 features need API 21 minimum
APP_PLATFORM := android-21

APP_STL := c++_static
APP_OPTIM := release
APP_THIN_ARCHIVE := true
APP_PIE := true
