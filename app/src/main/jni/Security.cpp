// ================================================================
// Security.cpp — Native anti-tamper + login verification
// ================================================================

#include <jni.h>
#include <string>
#include <cstring>
#include <cstdint>
#include <cstdio>
#include <cctype>
#include <ctime>
#include <cmath>
#include <android/log.h>

#include "Includes/obfuscate.h"

#define SEC_TAG "ModXLab_Security"
#define SLOGI(...) __android_log_print(ANDROID_LOG_INFO,  SEC_TAG, __VA_ARGS__)

// ================================================================
// ⚠️ Replace with your RELEASE cert SHA-256 (no colons, lowercase)
// ================================================================
static const char* EXPECTED_SHA256() {
    return OBFUSCATE("2214862d49d25c4c72b531bbd14d7e53587508035b6b7084b7d6d3f9763a0502");
}

// ================================================================
// Helpers
// ================================================================
static std::string toLower(std::string s) {
    for (auto& c : s) c = (char)std::tolower((unsigned char)c);
    return s;
}

static std::string getJsonField(const std::string& json, const std::string& key) {
    std::string needle = "\"" + key + "\"";
    size_t k = json.find(needle);
    if (k == std::string::npos) return "";
    size_t colon = json.find(':', k + needle.size());
    if (colon == std::string::npos) return "";
    size_t i = colon + 1;
    while (i < json.size() && std::isspace((unsigned char)json[i])) i++;
    if (i >= json.size()) return "";
    if (json[i] == '"') {
        size_t e = json.find('"', i + 1);
        if (e == std::string::npos) return "";
        return json.substr(i + 1, e - i - 1);
    }
    size_t e = i;
    while (e < json.size() && json[e] != ',' && json[e] != '}') e++;
    std::string v = json.substr(i, e - i);
    while (!v.empty() && std::isspace((unsigned char)v.back())) v.pop_back();
    return v;
}

// ================================================================
// Parse "YYYY-MM-DD HH:MM" [optional "+HH:MM" or "-HH:MM"]
// Returns epoch seconds (UTC), or -1 on parse failure.
// ================================================================
static long long parseExpireDate(const std::string& s) {
    if (s.empty()) return -1;

    int Y = 0, M = 0, D = 0, h = 0, m = 0;
    int tzH = 0, tzM = 0;
    char tzSign = '+';
    bool hasTZ = false;

    // Try full format with timezone first:  "YYYY-MM-DD HH:MM +HH:MM"
    int n = sscanf(s.c_str(), "%d-%d-%d %d:%d %c%d:%d",
                   &Y, &M, &D, &h, &m, &tzSign, &tzH, &tzM);
    if (n == 8) {
        hasTZ = true;
    } else {
        // Try without minutes in offset: "YYYY-MM-DD HH:MM +HH"
        n = sscanf(s.c_str(), "%d-%d-%d %d:%d %c%d",
                   &Y, &M, &D, &h, &m, &tzSign, &tzH);
        if (n == 7) {
            hasTZ = true;
            tzM = 0;
        } else {
            // Try without timezone: "YYYY-MM-DD HH:MM"
            n = sscanf(s.c_str(), "%d-%d-%d %d:%d", &Y, &M, &D, &h, &m);
            if (n != 5) return -1;
            hasTZ = false;
        }
    }

    // Validate ranges
    if (Y < 2020 || Y > 2200) return -1;
    if (M < 1 || M > 12) return -1;
    if (D < 1 || D > 31) return -1;
    if (h < 0 || h > 23) return -1;
    if (m < 0 || m > 59) return -1;
    if (hasTZ) {
        if (tzH < 0 || tzH > 14) return -1;
        if (tzM < 0 || tzM > 59) return -1;
    }

    // Days since 1970-01-01 (Howard Hinnant algorithm — pure integer math)
    int y = Y - (M <= 2 ? 1 : 0);
    int era = (y >= 0 ? y : y - 399) / 400;
    unsigned int yoe = (unsigned int)(y - era * 400);
    unsigned int doy = (153u * (unsigned int)(M > 2 ? M - 3 : M + 9) + 2u) / 5u + (unsigned int)D - 1u;
    unsigned int doe = yoe * 365u + yoe / 4u - yoe / 100u + doy;
    long long days = (long long)era * 146097LL + (long long)doe - 719468LL;

    long long localSec = days * 86400LL + (long long)h * 3600LL + (long long)m * 60LL;

    // Convert local → UTC by subtracting offset
    long long offSec = 0;
    if (hasTZ) {
        offSec = (long long)tzH * 3600LL + (long long)tzM * 60LL;
        if (tzSign == '-') offSec = -offSec;
    }
    return localSec - offSec;
}

// ================================================================
// JNI: checkSignatureHash
// ================================================================
extern "C" JNIEXPORT jboolean JNICALL
Java_com_android_support_SecurityNative_checkSignatureHash(JNIEnv* env, jclass, jstring jhash) {
    if (!jhash) return JNI_FALSE;
    const char* raw = env->GetStringUTFChars(jhash, nullptr);
    if (!raw) return JNI_FALSE;
    std::string given = toLower(std::string(raw));
    env->ReleaseStringUTFChars(jhash, raw);
    std::string expected = toLower(std::string(EXPECTED_SHA256()));
    if (given.size() != expected.size()) return JNI_FALSE;
    unsigned char diff = 0;
    for (size_t i = 0; i < given.size(); i++) diff |= (unsigned char)(given[i] ^ expected[i]);
    return diff == 0 ? JNI_TRUE : JNI_FALSE;
}

// ================================================================
// JNI: verifyLogin
// ================================================================
extern "C" JNIEXPORT jstring JNICALL
Java_com_android_support_SecurityNative_verifyLogin(JNIEnv* env, jclass,
                                                    jstring jUser,
                                                    jstring jPass,
                                                    jstring jUserJson) {
    auto fail = [&](const char* reason) -> jstring {
        std::string s = "{\"ok\":false,\"reason\":\"";
        s += reason;
        s += "\"}";
        return env->NewStringUTF(s.c_str());
    };

    if (!jUser || !jPass || !jUserJson) return fail("bad_input");
    const char* cUser = env->GetStringUTFChars(jUser, nullptr);
    const char* cPass = env->GetStringUTFChars(jPass, nullptr);
    const char* cJson = env->GetStringUTFChars(jUserJson, nullptr);
    if (!cUser || !cPass || !cJson) {
        if (cUser) env->ReleaseStringUTFChars(jUser, cUser);
        if (cPass) env->ReleaseStringUTFChars(jPass, cPass);
        if (cJson) env->ReleaseStringUTFChars(jUserJson, cJson);
        return fail("bad_input");
    }
    std::string sUser(cUser), sPass(cPass), sJson(cJson);
    env->ReleaseStringUTFChars(jUser, cUser);
    env->ReleaseStringUTFChars(jPass, cPass);
    env->ReleaseStringUTFChars(jUserJson, cJson);

    if (sUser.empty() || sPass.empty()) return fail("bad_input");
    if (sJson.empty() || sJson == "null" || sJson == "{}") return fail("no_match");

    // Case-insensitive username
    std::string sUserLower = toLower(sUser);

    std::string dbUser   = getJsonField(sJson, "user");
    std::string dbPass   = getJsonField(sJson, "pass");
    std::string dbStatus = getJsonField(sJson, "status");

    if (dbUser.empty() || dbPass.empty()) return fail("no_match");

    std::string dbUserLower = toLower(dbUser);

    // Constant-time-ish compare
    if (sUserLower.size() != dbUserLower.size() || sPass.size() != dbPass.size())
        return fail("invalid_credentials");
    unsigned char du = 0, dp = 0;
    for (size_t i = 0; i < sUserLower.size(); i++) du |= (unsigned char)(sUserLower[i] ^ dbUserLower[i]);
    for (size_t i = 0; i < sPass.size(); i++) dp |= (unsigned char)(sPass[i] ^ dbPass[i]);
    if (du != 0 || dp != 0) return fail("invalid_credentials");

    // Blocked check
    if (dbStatus != "true") return fail("blocked");

    // ============================================================
    // EXPIRY CHECK — Multi-format support
    // ============================================================
    long long expiryMs = 0;
    std::string how = "none";

    // Priority 1: explicit "expire_date" string (YYYY-MM-DD HH:MM [+HH:MM])
    std::string expireDate = getJsonField(sJson, "expire_date");
    if (!expireDate.empty()) {
        long long sec = parseExpireDate(expireDate);
        if (sec > 0) {
            expiryMs = sec * 1000LL;
            how = "expire_date";
        } else {
            SLOGI("verifyLogin: expire_date parse FAILED for '%s'", expireDate.c_str());
        }
    }

    // Priority 2: explicit "time" field (epoch ms)
    if (expiryMs == 0) {
        std::string dbTime = getJsonField(sJson, "time");
        if (!dbTime.empty()) {
            try {
                double d = std::stod(dbTime);
                if (d > 100000000000.0 && d < 9.2e18) {
                    expiryMs = (long long)d;
                    how = "time_epoch";
                }
            } catch (...) { }
        }
    }

    // Priority 3: rgtime + duration_hours
    if (expiryMs == 0) {
        std::string rgStr = getJsonField(sJson, "rgtime");
        long long rgMs = 0;
        try {
            double d = std::stod(rgStr);
            if (d > 100000000000.0 && d < 9.2e18) rgMs = (long long)d;
        } catch (...) { }
        if (rgMs > 0) {
            std::string durH = getJsonField(sJson, "duration_hours");
            if (!durH.empty()) {
                try {
                    double hours = std::stod(durH);
                    if (hours > 0 && hours < 1000000) {
                        expiryMs = rgMs + (long long)(hours * 3600.0 * 1000.0);
                        how = "rgtime+hours";
                    }
                } catch (...) { }
            }
            if (expiryMs == 0) {
                std::string durD = getJsonField(sJson, "duration_days");
                if (!durD.empty()) {
                    try {
                        double days = std::stod(durD);
                        if (days > 0 && days < 36500) {
                            expiryMs = rgMs + (long long)(days * 86400.0 * 1000.0);
                            how = "rgtime+days";
                        }
                    } catch (...) { }
                }
            }
        }
    }

    // Final expiry validation
    if (expiryMs > 0) {
        long long nowMs = (long long)time(nullptr) * 1000LL;
        double diffHours = (double)(expiryMs - nowMs) / 3600000.0;
        SLOGI("verifyLogin: user='%s' method=%s expiry=%lld now=%lld diff_hours=%.2f",
              dbUser.c_str(), how.c_str(), expiryMs, nowMs, diffHours);

        if (nowMs > expiryMs) {
            SLOGI("verifyLogin: EXPIRED");
            return fail("expired");
        }
    } else {
        SLOGI("verifyLogin: user='%s' no expiry set (infinite)", dbUser.c_str());
    }

    // Build session token
    unsigned long long h = 1469598103934665603ULL;
    auto mix = [&](const std::string& s) {
        for (char c : s) { h ^= (unsigned char)c; h *= 1099511628211ULL; }
    };
    mix(dbUser); mix(dbPass); mix(std::to_string(expiryMs)); mix(dbStatus);
    char tokenBuf[24];
    snprintf(tokenBuf, sizeof(tokenBuf), "%016llx", h);

    std::string out = "{\"ok\":true,\"token\":\"";
    out += tokenBuf;
    out += "\",\"user\":\""; out += dbUser;
    out += "\",\"status\":\"true\",\"expiry\":\"";
    out += std::to_string(expiryMs);
    out += "\"}";

    SLOGI("verifyLogin: OK user=%s", dbUser.c_str());
    return env->NewStringUTF(out.c_str());
}

// ================================================================
// JNI: decryptString
// ================================================================
extern "C" JNIEXPORT jstring JNICALL
Java_com_android_support_SecurityNative_decryptString(JNIEnv* env, jclass, jstring jEnc, jint key) {
    if (!jEnc) return env->NewStringUTF("");
    const char* enc = env->GetStringUTFChars(jEnc, nullptr);
    if (!enc) return env->NewStringUTF("");
    size_t n = strlen(enc);
    std::string out(n, 0);
    for (size_t i = 0; i < n; i++)
        out[i] = (char)((unsigned char)enc[i] ^ (unsigned char)((key + (int)(i * 31)) & 0xFF));
    env->ReleaseStringUTFChars(jEnc, enc);
    return env->NewStringUTF(out.c_str());
}

// ================================================================
// JNI: isEnvironmentValid
// ================================================================
extern "C" JNIEXPORT jboolean JNICALL
Java_com_android_support_SecurityNative_isEnvironmentValid(JNIEnv*, jclass) {
    FILE* fp = fopen("/proc/self/maps", "r");
    if (!fp) return JNI_TRUE;
    char line[512];
    bool suspicious = false;
    while (fgets(line, sizeof(line), fp)) {
        if (strstr(line, "frida") || strstr(line, "xposed") ||
            strstr(line, "substrate") || strstr(line, "magisk")) {
            suspicious = true; break;
        }
    }
    fclose(fp);
    return suspicious ? JNI_FALSE : JNI_TRUE;
}