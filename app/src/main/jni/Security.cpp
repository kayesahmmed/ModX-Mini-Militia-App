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
#include <android/log.h>

#include "Includes/obfuscate.h"

#define SEC_TAG "ModXLab_Security"
#define SLOGI(...) __android_log_print(ANDROID_LOG_INFO,  SEC_TAG, __VA_ARGS__)

// ================================================================
// ⚠️ PHASE 4 এ এই hash UPDATE করবেন
//  (এখন placeholder রাখছি, পরে actual release hash বসাবেন)
// ================================================================
static const char* EXPECTED_SHA256() {
    return OBFUSCATE("0000000000000000000000000000000000000000000000000000000000000000");
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

static std::string sessionToken(const std::string& user,
                                const std::string& pass,
                                const std::string& timeStr,
                                const std::string& status) {
    unsigned long long h = 1469598103934665603ULL;
    auto mix = [&](const std::string& s) {
        for (char c : s) { h ^= (unsigned char)c; h *= 1099511628211ULL; }
    };
    mix(user); mix(pass); mix(timeStr); mix(status);
    char buf[24];
    snprintf(buf, sizeof(buf), "%016llx", h);
    return std::string(buf);
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

    std::string dbUser   = getJsonField(sJson, "user");
    std::string dbPass   = getJsonField(sJson, "pass");
    std::string dbStatus = getJsonField(sJson, "status");
    std::string dbTime   = getJsonField(sJson, "time");

    if (dbUser.empty() || dbPass.empty()) return fail("no_match");
    if (sUser.size() != dbUser.size() || sPass.size() != dbPass.size()) return fail("invalid_credentials");
    unsigned char du = 0, dp = 0;
    for (size_t i = 0; i < sUser.size(); i++) du |= (unsigned char)(sUser[i] ^ dbUser[i]);
    for (size_t i = 0; i < sPass.size(); i++) dp |= (unsigned char)(sPass[i] ^ dbPass[i]);
    if (du != 0 || dp != 0) return fail("invalid_credentials");

    if (dbStatus != "true") return fail("blocked");

    if (!dbTime.empty()) {
        long long t = 0;
        try { t = std::stoll(dbTime); } catch (...) { t = 0; }
        if (t > 0) {
            long long nowMs = (long long)time(nullptr) * 1000LL;
            if (nowMs > t) return fail("expired");
        }
    }

    std::string token = sessionToken(dbUser, dbPass, dbTime, dbStatus);
    std::string out = "{\"ok\":true,\"token\":\"";
    out += token;
    out += "\",\"user\":\""; out += dbUser;
    out += "\",\"status\":\"true\",\"time\":\""; out += dbTime;
    out += "\"}";
    SLOGI("verifyLogin ok user=%s", dbUser.c_str());
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