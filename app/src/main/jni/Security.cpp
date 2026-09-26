// ================================================================
// Security.cpp — Silent Security Layer (v5)
//
// Design principles:
//   - NO user-visible strings
//   - NO log messages that leak what check failed
//   - All sensitive data obfuscated at compile time
//   - Single entry point: verifyHashes()
//   - Constant-time comparisons
// ================================================================

#include <jni.h>
#include <string>
#include <cstring>
#include <cstdint>
#include <cstdio>
#include <cctype>
#include <cstdlib>
#include <ctime>
#include <cmath>
#include <cstdarg>
#include <unistd.h>
#include <fcntl.h>
#include <dirent.h>
#include <dlfcn.h>
#include <sys/ptrace.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/mman.h>
#include <sys/system_properties.h>
#include <android/log.h>
#include <pthread.h>

#include "Includes/obfuscate.h"

// ================================================================
// Silent logging — only used for catastrophic errors, not security
// ================================================================
#define SEC_TAG "ModXLab"
#define SLOG_CRIT(...) __android_log_print(ANDROID_LOG_ERROR, SEC_TAG, __VA_ARGS__)

#define OBF_STR(s) (static_cast<const char*>(OBFUSCATE(s)))

// ================================================================
// 🔑 CONFIGURATION — REPLACE WITH YOUR OWN VALUES
// ================================================================

// Your release cert SHA-256 (64 lowercase hex chars, no colons)
static const char* SHA256_PRIMARY() {
    return OBF_STR("2214862d49d25c4c72b531bbd14d7e53587508035b6b7084b7d6d3f9763a0502");
}
static const char* SHA256_ALT1() { return OBF_STR(""); }
static const char* SHA256_ALT2() { return OBF_STR(""); }

// HMAC secret — CHANGE THIS TO YOUR OWN UNIQUE VALUE
static const char* HMAC_SECRET() {
    return OBF_STR("xK9mP2QvLt7Rn5Bs4Wz8YhJ6CgD3FeA1NqU4TrXc");
}

// Expected DEX hash — update after first build
static const char* EXPECTED_DEX_HASH() {
    return OBF_STR("0000000000000000000000000000000000000000000000000000000000000000");
}

// ================================================================
// SHA-256 (pure C, no external dependencies)
// ================================================================
namespace SecSHA {

    struct Ctx {
        uint32_t h[8];
        uint64_t len;
        uint8_t  buf[64];
        size_t   used;
    };

    static const uint32_t K[64] = {
        0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,
        0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,
        0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
        0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,
        0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,
        0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,
        0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
        0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2
    };

    #define ROR32(x,n) (((x) >> (n)) | ((x) << (32 - (n))))

    static void transform(Ctx* c, const uint8_t* p) {
        uint32_t w[64];
        for (int i = 0; i < 16; i++)
            w[i] = ((uint32_t)p[i*4] << 24) | ((uint32_t)p[i*4+1] << 16)
                 | ((uint32_t)p[i*4+2] << 8) | ((uint32_t)p[i*4+3]);
        for (int i = 16; i < 64; i++) {
            uint32_t s0 = ROR32(w[i-15], 7) ^ ROR32(w[i-15], 18) ^ (w[i-15] >> 3);
            uint32_t s1 = ROR32(w[i-2], 17) ^ ROR32(w[i-2], 19) ^ (w[i-2] >> 10);
            w[i] = w[i-16] + s0 + w[i-7] + s1;
        }
        uint32_t a=c->h[0], b=c->h[1], cc=c->h[2], d=c->h[3];
        uint32_t e=c->h[4], f=c->h[5], g=c->h[6], h=c->h[7];
        for (int i = 0; i < 64; i++) {
            uint32_t S1 = ROR32(e, 6) ^ ROR32(e, 11) ^ ROR32(e, 25);
            uint32_t ch = (e & f) ^ ((~e) & g);
            uint32_t t1 = h + S1 + ch + K[i] + w[i];
            uint32_t S0 = ROR32(a, 2) ^ ROR32(a, 13) ^ ROR32(a, 22);
            uint32_t maj = (a & b) ^ (a & cc) ^ (b & cc);
            uint32_t t2 = S0 + maj;
            h=g; g=f; f=e; e=d+t1; d=cc; cc=b; b=a; a=t1+t2;
        }
        c->h[0]+=a; c->h[1]+=b; c->h[2]+=cc; c->h[3]+=d;
        c->h[4]+=e; c->h[5]+=f; c->h[6]+=g; c->h[7]+=h;
    }

    static void init(Ctx* c) {
        c->h[0]=0x6a09e667; c->h[1]=0xbb67ae85; c->h[2]=0x3c6ef372; c->h[3]=0xa54ff53a;
        c->h[4]=0x510e527f; c->h[5]=0x9b05688c; c->h[6]=0x1f83d9ab; c->h[7]=0x5be0cd19;
        c->len=0; c->used=0;
    }

    static void update(Ctx* c, const uint8_t* data, size_t n) {
        c->len += n;
        while (n > 0) {
            size_t take = 64 - c->used;
            if (take > n) take = n;
            memcpy(c->buf + c->used, data, take);
            c->used += take; data += take; n -= take;
            if (c->used == 64) { transform(c, c->buf); c->used = 0; }
        }
    }

    static void final(Ctx* c, uint8_t out[32]) {
        uint64_t bits = c->len * 8;
        uint8_t pad = 0x80;
        update(c, &pad, 1);
        uint8_t zero = 0;
        while (c->used != 56) update(c, &zero, 1);
        uint8_t lenb[8];
        for (int i = 0; i < 8; i++) lenb[i] = (uint8_t)(bits >> (56 - i*8));
        update(c, lenb, 8);
        for (int i = 0; i < 8; i++) {
            out[i*4]   = (uint8_t)(c->h[i] >> 24);
            out[i*4+1] = (uint8_t)(c->h[i] >> 16);
            out[i*4+2] = (uint8_t)(c->h[i] >> 8);
            out[i*4+3] = (uint8_t)(c->h[i]);
        }
    }

    static std::string hashHex(const std::string& in) {
        Ctx c; init(&c);
        update(&c, (const uint8_t*)in.data(), in.size());
        uint8_t out[32]; final(&c, out);
        char buf[65];
        for (int i = 0; i < 32; i++) snprintf(buf + i*2, 3, "%02x", out[i]);
        return std::string(buf, 64);
    }

    static std::string hmacHex(const std::string& key, const std::string& msg) {
        std::string k = key;
        if (k.size() > 64) k = hashHex(k);
        while (k.size() < 64) k += '\0';
        std::string o(64, 0x5c), inn(64, 0x36);
        for (int i = 0; i < 64; i++) {
            o[i]   ^= k[i];
            inn[i] ^= k[i];
        }
        return hashHex(o + hashHex(inn + msg));
    }
}

// ================================================================
// String helpers
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

static bool constTimeEquals(const std::string& a, const std::string& b) {
    if (a.size() != b.size()) return false;
    unsigned char diff = 0;
    for (size_t i = 0; i < a.size(); i++) diff |= (unsigned char)(a[i] ^ b[i]);
    return diff == 0;
}

static std::string urlEncode(const std::string& s) {
    std::string out;
    out.reserve(s.size() * 3);
    for (size_t i = 0; i < s.size(); i++) {
        unsigned char c = (unsigned char)s[i];
        if ((c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') ||
            (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '~') {
            out += (char)c;
        } else {
            char b[8];
            snprintf(b, sizeof(b), "%%%02X", c);
            out += b;
        }
    }
    return out;
}

// ================================================================
// Detection helpers (all SILENT — no logs)
// ================================================================

// --- Frida detection (multi-vector) ---
static bool detectFrida() {
    // 1) Memory map scan
    FILE* fp = fopen("/proc/self/maps", "r");
    if (fp) {
        char line[512];
        bool found = false;
        while (fgets(line, sizeof(line), fp)) {
            if (strstr(line, "frida-agent") ||
                strstr(line, "frida-gadget") ||
                strstr(line, "libfrida-gadget") ||
                strstr(line, "libfrida-agent") ||
                strstr(line, "gum-js-loop") ||
                strstr(line, "frida-server") ||
                strstr(line, "re.frida.server")) {
                found = true; break;
            }
        }
        fclose(fp);
        if (found) return true;
    }

    // 2) Known Frida tmp files
    DIR* dir = opendir("/data/local/tmp");
    if (dir) {
        struct dirent* de;
        bool found = false;
        while ((de = readdir(dir)) != nullptr) {
            if (strstr(de->d_name, "re.frida.server") ||
                strstr(de->d_name, "frida-server")) {
                found = true; break;
            }
        }
        closedir(dir);
        if (found) return true;
    }

    // 3) Frida libraries loaded in-process
    void* h1 = dlopen("libfrida-gadget.so", RTLD_NOW);
    if (h1) { dlclose(h1); return true; }
    void* h2 = dlopen("libfrida-agent.so", RTLD_NOW);
    if (h2) { dlclose(h2); return true; }

    return false;
}

// --- Xposed / LSPosed detection ---
static bool detectXposed() {
    const char* paths[] = {
        "/system/framework/XposedBridge.jar",
        "/system/lib/libxposed_art.so",
        "/system/lib64/libxposed_art.so",
        "/data/data/de.robv.android.xposed.installer",
        "/data/data/org.lsposed.manager",
        "/data/adb/lspd",
        "/data/adb/modules/riru_lsposed",
        "/data/adb/modules/zygisk_lsposed",
        nullptr
    };
    for (int i = 0; paths[i]; i++) {
        if (access(paths[i], F_OK) == 0) return true;
    }
    return false;
}

// --- Debugger via TracerPid ---
static bool detectTracer() {
    int fd = open("/proc/self/status", O_RDONLY);
    if (fd < 0) return false;
    char buf[4096];
    ssize_t n = read(fd, buf, sizeof(buf) - 1);
    close(fd);
    if (n <= 0) return false;
    buf[n] = '\0';
    const char* p = strstr(buf, "TracerPid:");
    if (!p) return false;
    p += 10;
    while (*p == ' ' || *p == '\t') p++;
    return atoi(p) > 0;
}

// --- Substrate (soft) ---
static bool detectSubstrate() {
    const char* paths[] = {
        "/system/lib/libsubstrate.so",
        "/system/lib64/libsubstrate.so",
        "/data/data/com.saurik.substrate",
        nullptr
    };
    for (int i = 0; paths[i]; i++) {
        if (access(paths[i], F_OK) == 0) return true;
    }
    return false;
}

// --- Root (soft) ---
static bool detectRoot() {
    const char* paths[] = {
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su",
        "/data/local/su", "/data/local/xbin/su",
        "/system/app/Superuser.apk",
        "/magisk/.core/bin/su",
        "/sbin/.magisk",
        nullptr
    };
    for (int i = 0; paths[i]; i++) {
        if (access(paths[i], F_OK) == 0) return true;
    }
    return false;
}

// --- Emulator (soft) ---
static bool detectEmulator() {
    if (access("/dev/socket/qemud", F_OK) == 0) return true;
    if (access("/dev/qemu_pipe", F_OK) == 0) return true;

    char value[PROP_VALUE_MAX] = {0};
    if (__system_property_get("ro.kernel.qemu", value) > 0) {
        if (strcmp(value, "1") == 0) return true;
    }

    FILE* fp = fopen("/proc/cpuinfo", "r");
    if (fp) {
        char line[512];
        bool found = false;
        while (fgets(line, sizeof(line), fp)) {
            if (strstr(line, "goldfish") || strstr(line, "ranchu")) {
                found = true; break;
            }
        }
        fclose(fp);
        if (found) return true;
    }
    return false;
}

// ================================================================
// Aggregated environment check — silent, no logs
// ================================================================
static bool isEnvironmentSafe() {
    // HARD FAILS
    if (detectFrida())      return false;
    if (detectXposed())     return false;
    if (detectTracer())     return false;

    // SOFT CHECKS — run them (no logs) but don't fail
    (void)detectSubstrate();
    (void)detectRoot();
    (void)detectEmulator();

    return true;
}

// ================================================================
// Native URL builder
// ================================================================
static std::string buildBaseUrl() {
    std::string url;
    url += OBF_STR("https://modx-lab-5a6ee");
    url += OBF_STR("-default-rtdb.firebaseio.com");
    return url;
}

static std::string buildQueryUrl(const std::string& username) {
    std::string url = buildBaseUrl();
    url += OBF_STR("/User.json?orderBy=%22user%22&equalTo=%22");
    url += urlEncode(username);
    url += OBF_STR("%22");
    return url;
}

static std::string buildUpdateUrl() {
    std::string url = buildBaseUrl();
    url += OBF_STR("/update.json");
    return url;
}

// ================================================================
// Expiry date parser — "YYYY-MM-DD HH:MM [+HH:MM]"
// ================================================================
static long long parseExpireDate(const std::string& s) {
    if (s.empty()) return -1;

    int Y=0, M=0, D=0, h=0, m=0, tzH=0, tzM=0;
    char tzSign = '+';
    bool hasTZ = false;

    int n = sscanf(s.c_str(), "%d-%d-%d %d:%d %c%d:%d",
                   &Y, &M, &D, &h, &m, &tzSign, &tzH, &tzM);
    if (n == 8) hasTZ = true;
    else {
        n = sscanf(s.c_str(), "%d-%d-%d %d:%d %c%d",
                   &Y, &M, &D, &h, &m, &tzSign, &tzH);
        if (n == 7) { hasTZ = true; tzM = 0; }
        else {
            n = sscanf(s.c_str(), "%d-%d-%d %d:%d", &Y, &M, &D, &h, &m);
            if (n != 5) return -1;
        }
    }

    if (Y < 2020 || Y > 2200) return -1;
    if (M < 1 || M > 12) return -1;
    if (D < 1 || D > 31) return -1;
    if (h < 0 || h > 23) return -1;
    if (m < 0 || m > 59) return -1;
    if (hasTZ && (tzH < 0 || tzH > 14 || tzM < 0 || tzM > 59)) return -1;

    int y = Y - (M <= 2 ? 1 : 0);
    int era = (y >= 0 ? y : y - 399) / 400;
    unsigned yoe = (unsigned)(y - era * 400);
    unsigned doy = (153u * (M > 2 ? M - 3 : M + 9) + 2u) / 5u + (unsigned)D - 1u;
    unsigned doe = yoe * 365u + yoe / 4u - yoe / 100u + doy;
    long long days = (long long)era * 146097LL + (long long)doe - 719468LL;

    long long localSec = days * 86400LL + (long long)h * 3600LL + (long long)m * 60LL;

    long long offSec = 0;
    if (hasTZ) {
        offSec = (long long)tzH * 3600LL + (long long)tzM * 60LL;
        if (tzSign == '-') offSec = -offSec;
    }
    return localSec - offSec;
}

// ================================================================
// JNI: verifyHashes — SILENT SECURITY GATE
// ---------------------------------------------------------------
// Combines:
//   - APK signature verify (multi-cert, constant-time)
//   - DEX integrity verify
//   - Environment check (Frida, Xposed, Tracer)
//
// Returns:
//   JNI_TRUE  → all checks pass
//   JNI_FALSE → any check fails (caller should kill process silently)
//
// NO logging. NO descriptive strings.
// ================================================================
extern "C" JNIEXPORT jboolean JNICALL
Java_com_android_support_SecurityNative_verifyHashes(
        JNIEnv* env, jclass, jstring jsig, jstring jdex, jboolean jdebug) {

    if (!jsig || !jdex) return JNI_FALSE;

    const char* csig = env->GetStringUTFChars(jsig, nullptr);
    const char* cdex = env->GetStringUTFChars(jdex, nullptr);
    if (!csig || !cdex) {
        if (csig) env->ReleaseStringUTFChars(jsig, csig);
        if (cdex) env->ReleaseStringUTFChars(jdex, cdex);
        return JNI_FALSE;
    }

    std::string sigHash = toLower(std::string(csig));
    std::string dexHash = toLower(std::string(cdex));
    env->ReleaseStringUTFChars(jsig, csig);
    env->ReleaseStringUTFChars(jdex, cdex);

    bool isDebug = (jdebug == JNI_TRUE);

    // ---------- Signature verify (skip in debug) ----------
    if (!isDebug) {
        std::string expected = toLower(std::string(SHA256_PRIMARY()));

        bool ok = false;
        if (!expected.empty() && sigHash.size() == expected.size()) {
            if (constTimeEquals(sigHash, expected)) ok = true;
        }

        if (!ok) {
            // Try alternative certs (key rotation)
            std::string a1 = toLower(std::string(SHA256_ALT1()));
            if (!a1.empty() && sigHash.size() == a1.size()) {
                if (constTimeEquals(sigHash, a1)) ok = true;
            }
            std::string a2 = toLower(std::string(SHA256_ALT2()));
            if (!ok && !a2.empty() && sigHash.size() == a2.size()) {
                if (constTimeEquals(sigHash, a2)) ok = true;
            }
        }

        if (!ok) return JNI_FALSE;
    }

    // ---------- DEX integrity verify (skip in debug) ----------
    if (!isDebug) {
        std::string expected = toLower(std::string(EXPECTED_DEX_HASH()));

        // Check if placeholder (all zeros) — skip if so
        bool placeholder = true;
        for (char c : expected) {
            if (c != '0') { placeholder = false; break; }
        }

        if (!placeholder) {
            if (dexHash.size() != expected.size()) return JNI_FALSE;
            if (!constTimeEquals(dexHash, expected)) return JNI_FALSE;
        }
    }

    // ---------- Environment check (always) ----------
    if (!isEnvironmentSafe()) return JNI_FALSE;

    return JNI_TRUE;
}

// ================================================================
// JNI: verifyLogin — native login verification + HMAC token
// ================================================================
extern "C" JNIEXPORT jstring JNICALL
Java_com_android_support_SecurityNative_verifyLogin(
        JNIEnv* env, jclass,
        jstring jUser, jstring jPass, jstring jUserJson) {

    auto fail = [&](const char* reason) -> jstring {
        std::string s = "{\"ok\":false,\"reason\":\"";
        s += reason;
        s += "\"}";
        return env->NewStringUTF(s.c_str());
    };

    if (!jUser || !jPass || !jUserJson) return fail("bad_input");

    const char* cu = env->GetStringUTFChars(jUser, nullptr);
    const char* cp = env->GetStringUTFChars(jPass, nullptr);
    const char* cj = env->GetStringUTFChars(jUserJson, nullptr);
    if (!cu || !cp || !cj) {
        if (cu) env->ReleaseStringUTFChars(jUser, cu);
        if (cp) env->ReleaseStringUTFChars(jPass, cp);
        if (cj) env->ReleaseStringUTFChars(jUserJson, cj);
        return fail("bad_input");
    }

    std::string sUser(cu), sPass(cp), sJson(cj);
    env->ReleaseStringUTFChars(jUser, cu);
    env->ReleaseStringUTFChars(jPass, cp);
    env->ReleaseStringUTFChars(jUserJson, cj);

    if (sUser.empty() || sPass.empty()) return fail("bad_input");
    if (sJson.empty() || sJson == "null" || sJson == "{}") return fail("no_match");

    std::string dbUser   = getJsonField(sJson, "user");
    std::string dbPass   = getJsonField(sJson, "pass");
    std::string dbStatus = getJsonField(sJson, "status");

    if (dbUser.empty() || dbPass.empty()) return fail("no_match");

    std::string uL  = toLower(sUser);
    std::string dUL = toLower(dbUser);
    if (!constTimeEquals(uL, dUL)) return fail("invalid_credentials");
    if (!constTimeEquals(sPass, dbPass)) return fail("invalid_credentials");

    if (dbStatus != "true") return fail("blocked");

    // ---------- Expiry resolution ----------
    long long expiryMs = 0;

    // Priority 1: expire_date string
    std::string expireDate = getJsonField(sJson, "expire_date");
    if (!expireDate.empty()) {
        long long sec = parseExpireDate(expireDate);
        if (sec > 0) expiryMs = sec * 1000LL;
    }

    // Priority 2: time epoch ms
    if (expiryMs == 0) {
        std::string dbTime = getJsonField(sJson, "time");
        if (!dbTime.empty()) {
            try {
                double d = std::stod(dbTime);
                if (d > 1e11 && d < 9.2e18) expiryMs = (long long)d;
            } catch (...) {}
        }
    }

    // Priority 3: rgtime + duration_hours/days
    if (expiryMs == 0) {
        std::string rgStr = getJsonField(sJson, "rgtime");
        long long rgMs = 0;
        try {
            double d = std::stod(rgStr);
            if (d > 1e11 && d < 9.2e18) rgMs = (long long)d;
        } catch (...) {}

        if (rgMs > 0) {
            std::string durH = getJsonField(sJson, "duration_hours");
            if (!durH.empty()) {
                try {
                    double h = std::stod(durH);
                    if (h > 0 && h < 1e6) expiryMs = rgMs + (long long)(h * 3600000.0);
                } catch (...) {}
            }
            if (expiryMs == 0) {
                std::string durD = getJsonField(sJson, "duration_days");
                if (!durD.empty()) {
                    try {
                        double d = std::stod(durD);
                        if (d > 0 && d < 36500) expiryMs = rgMs + (long long)(d * 86400000.0);
                    } catch (...) {}
                }
            }
        }
    }

    // ---------- Enforce expiry ----------
    if (expiryMs > 0) {
        long long nowMs = (long long)time(nullptr) * 1000LL;
        if (nowMs > expiryMs) return fail("expired");
    }

    // ---------- Generate HMAC-signed session token ----------
    std::string payload = dbUser + "|" + dbPass + "|" +
                          std::to_string(expiryMs) + "|" + dbStatus;
    std::string token = SecSHA::hmacHex(std::string(HMAC_SECRET()), payload);

    std::string out = "{\"ok\":true,\"token\":\"";
    out += token;
    out += "\",\"user\":\""; out += dbUser;
    out += "\",\"status\":\"true\",\"expiry\":\"";
    out += std::to_string(expiryMs);
    out += "\"}";
    return env->NewStringUTF(out.c_str());
}

// ================================================================
// JNI: verifySessionToken
// ================================================================
extern "C" JNIEXPORT jboolean JNICALL
Java_com_android_support_SecurityNative_verifySessionToken(
        JNIEnv* env, jclass,
        jstring jToken, jstring jUser, jstring jPass, jstring jExpiry) {

    if (!jToken || !jUser || !jPass || !jExpiry) return JNI_FALSE;

    const char* ct = env->GetStringUTFChars(jToken, nullptr);
    const char* cu = env->GetStringUTFChars(jUser, nullptr);
    const char* cp = env->GetStringUTFChars(jPass, nullptr);
    const char* ce = env->GetStringUTFChars(jExpiry, nullptr);

    if (!ct || !cu || !cp || !ce) {
        if (ct) env->ReleaseStringUTFChars(jToken, ct);
        if (cu) env->ReleaseStringUTFChars(jUser, cu);
        if (cp) env->ReleaseStringUTFChars(jPass, cp);
        if (ce) env->ReleaseStringUTFChars(jExpiry, ce);
        return JNI_FALSE;
    }

    std::string token(ct), user(cu), pass(cp), expiry(ce);
    env->ReleaseStringUTFChars(jToken, ct);
    env->ReleaseStringUTFChars(jUser, cu);
    env->ReleaseStringUTFChars(jPass, cp);
    env->ReleaseStringUTFChars(jExpiry, ce);

    std::string payload = user + "|" + pass + "|" + expiry + "|true";
    std::string expected = SecSHA::hmacHex(std::string(HMAC_SECRET()), payload);

    if (!constTimeEquals(token, expected)) return JNI_FALSE;

    // Also re-check expiry
    try {
        long long expiryMs = std::stoll(expiry);
        if (expiryMs > 0) {
            long long now = (long long)time(nullptr) * 1000LL;
            if (now > expiryMs) return JNI_FALSE;
        }
    } catch (...) {}

    return JNI_TRUE;
}

// ================================================================
// JNI: getQueryUrl — native URL building (Firebase URL not in dex)
// ================================================================
extern "C" JNIEXPORT jstring JNICALL
Java_com_android_support_SecurityNative_getQueryUrl(
        JNIEnv* env, jclass, jstring jUser) {
    if (!jUser) return env->NewStringUTF("");
    const char* user = env->GetStringUTFChars(jUser, nullptr);
    if (!user) return env->NewStringUTF("");
    std::string u(user);
    env->ReleaseStringUTFChars(jUser, user);
    std::string url = buildQueryUrl(u);
    return env->NewStringUTF(url.c_str());
}

// ================================================================
// JNI: getUpdateUrl
// ================================================================
extern "C" JNIEXPORT jstring JNICALL
Java_com_android_support_SecurityNative_getUpdateUrl(JNIEnv* env, jclass) {
    std::string url = buildUpdateUrl();
    return env->NewStringUTF(url.c_str());
}