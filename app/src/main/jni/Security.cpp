// ================================================================
// Security.cpp — Silent Security Layer (v10 — Hardened)
//
// CHANGES vs v9:
//   ✅ Project ID moved to native (OBFUSCATED)
//   ✅ Cert pins moved to native (OBFUSCATED)
//   ✅ HMAC-native-signature verify (verifyNativeSig)
//   ✅ verifyLoginSig — HMAC verify of login payload
//   ✅ Anti-Frida: syscall-based + thread-name detection
//   ✅ Anti-Xposed: syscall-based file access
//   ✅ Removed Firebase query URL builders
//   ✅ All strings OBFUSCATED via OBF_STR
//
// Preserved:
//   • APK signature verify
//   • DEX hash check (optional)
//   • Native lib hash check
//   • Anti-debug / anti-emulator
//   • Anti-memory-dump
//   • HMAC session tokens
//   • JWT decode for session
//   • Cloud Function URL
//
// NOTE: JNI_OnLoad defined in Setup.cpp — do NOT add here.
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
#include <atomic>
#include <unistd.h>
#include <fcntl.h>
#include <dirent.h>
#include <dlfcn.h>
#include <sys/ptrace.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <sys/mman.h>
#include <sys/resource.h>
#include <sys/syscall.h>
#include <sys/system_properties.h>
#include <android/log.h>
#include <pthread.h>

#include "Includes/obfuscate.h"

#define OBF_STR(s) (static_cast<const char*>(OBFUSCATE(s)))

// ================================================================
// 🔑 CONFIGURATION
// ================================================================

static const char* SHA256_PRIMARY() {
    return OBF_STR("8ad4db86f888c47cb3483c93afae4514274e77cb6a62c0ee63d2dcd249c2f89a");
}
static const char* SHA256_ALT1() { return OBF_STR(""); }
static const char* SHA256_ALT2() { return OBF_STR(""); }

static const char* EXPECTED_DEX_HASH() { return OBF_STR(""); }
static const char* EXPECTED_LIB_HASH() {
    return OBF_STR("0000000000000000000000000000000000000000000000000000000000000000");
}

// ✅ Project ID moved from Java → native OBFUSCATED
static const char* APPWRITE_PROJECT_ID() {
    return OBF_STR("modxlab");
}

// ✅ Function URL (corrected ID)
static const char* CLOUD_FN_URL() {
    return OBF_STR("https://sgp.cloud.appwrite.io/v1/functions/6ab760b200276b627cbe/executions");
}

// ────────────────────────────────────────────────────────────────
// 🔐 NATIVE_SHARED_KEY — must match server env var NATIVE_SHARED_KEY
// ────────────────────────────────────────────────────────────────
static std::string deriveNativeKey() {
    std::string p1 = OBF_STR("Nx7KpQ2m9vT");
    std::string p2 = OBF_STR("bL4Rs8Wz3Yh");
    std::string p3 = OBF_STR("Jc6FgD9Ae1N");
    std::string p4 = OBF_STR("qU5TrXc");
    std::string p5 = OBF_STR("PzB3MwL0Kv");
    return p1 + p2 + p3 + p4 + p5;
}

// Legacy HMAC key (for verifyLoginWithTime fallback)
static std::string deriveKey() {
    std::string p1 = OBF_STR("xK9mP2QvLt7");
    std::string p2 = OBF_STR("Rn5Bs4Wz8Yh");
    std::string p3 = OBF_STR("J6CgD3FeA1N");
    std::string p4 = OBF_STR("qU4TrXc");
    return p1 + p2 + p3 + p4;
}

// ================================================================
// SHA-256 + HMAC
// ================================================================
namespace SecSHA {
    struct Ctx { uint32_t h[8]; uint64_t len; uint8_t buf[64]; size_t used; };

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
        for (int i = 0; i < 64; i++) { o[i] ^= k[i]; inn[i] ^= k[i]; }
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

static std::string base64UrlDecode(const std::string& in) {
    std::string out;
    int val = 0, bits = 0;
    for (size_t i = 0; i < in.size(); i++) {
        char c = in[i];
        if (c == '=') break;
        int idx;
        if (c >= 'A' && c <= 'Z') idx = c - 'A';
        else if (c >= 'a' && c <= 'z') idx = 26 + (c - 'a');
        else if (c >= '0' && c <= '9') idx = 52 + (c - '0');
        else if (c == '-') idx = 62;
        else if (c == '_') idx = 63;
        else continue;
        val = (val << 6) | idx;
        bits += 6;
        if (bits >= 8) {
            bits -= 8;
            out += (char)((val >> bits) & 0xFF);
        }
    }
    return out;
}

// ================================================================
// Anti-tamper detectors — SYSCALL-based (bypass libc hooks)
// ================================================================

// Raw read of /proc/self/maps via syscall
static bool detectFridaMaps() {
    int fd = (int)syscall(SYS_openat, AT_FDCWD, "/proc/self/maps", O_RDONLY, 0);
    if (fd < 0) return false;

    char buf[8192];
    ssize_t n;
    std::string data;
    while ((n = syscall(SYS_read, fd, buf, sizeof(buf))) > 0) {
        data.append(buf, (size_t)n);
        if (data.size() > 512 * 1024) break;
    }
    syscall(SYS_close, fd);

    const char* patterns[] = {
        "frida-agent", "frida-gadget", "libfrida-gadget",
        "libfrida-agent", "gum-js-loop", "frida-server",
        "re.frida.server", "frida_agent", "linjector",
        nullptr
    };
    for (int i = 0; patterns[i]; i++) {
        if (data.find(patterns[i]) != std::string::npos) return true;
    }
    return false;
}

// Thread name check — frida/gum injects threads
static bool detectFridaThreads() {
    DIR* dir = opendir("/proc/self/task");
    if (!dir) return false;
    struct dirent* de;
    bool found = false;
    while ((de = readdir(dir)) != nullptr) {
        if (de->d_name[0] == '.') continue;

        std::string commPath = "/proc/self/task/";
        commPath += de->d_name;
        commPath += "/comm";

        int fd = (int)syscall(SYS_openat, AT_FDCWD, commPath.c_str(), O_RDONLY, 0);
        if (fd < 0) continue;

        char nbuf[64] = {0};
        ssize_t r = syscall(SYS_read, fd, nbuf, sizeof(nbuf) - 1);
        syscall(SYS_close, fd);
        if (r <= 0) continue;

        std::string comm(nbuf);
        if (comm.find("gmain") != std::string::npos ||
            comm.find("gum-js") != std::string::npos ||
            comm.find("gdbus") != std::string::npos ||
            comm.find("frida") != std::string::npos ||
            comm.find("pool-frida") != std::string::npos) {
            found = true;
            break;
        }
    }
    closedir(dir);
    return found;
}

// Frida default port 27042
static bool detectFridaPort() {
    const char* paths[] = {
        "/proc/net/tcp", "/proc/net/tcp6", nullptr
    };
    for (int i = 0; paths[i]; i++) {
        int fd = (int)syscall(SYS_openat, AT_FDCWD, paths[i], O_RDONLY, 0);
        if (fd < 0) continue;
        char buf[16384];
        ssize_t n = syscall(SYS_read, fd, buf, sizeof(buf) - 1);
        syscall(SYS_close, fd);
        if (n <= 0) continue;
        buf[n] = 0;
        std::string s(buf);
        // 27042 = 0x69A2 → in /proc/net/tcp format "69A2"
        if (s.find(":69A2") != std::string::npos) return true;
    }
    return false;
}

static bool detectFrida() {
    if (detectFridaMaps())    return true;
    if (detectFridaThreads()) return true;
    if (detectFridaPort())    return true;
    return false;
}

// Syscall-based file existence check
static bool sysExists(const char* path) {
    int fd = (int)syscall(SYS_openat, AT_FDCWD, path, O_RDONLY, 0);
    if (fd >= 0) {
        syscall(SYS_close, fd);
        return true;
    }
    return false;
}

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
        if (sysExists(paths[i])) return true;
    }
    return false;
}

static bool detectTracer() {
    int fd = (int)syscall(SYS_openat, AT_FDCWD, "/proc/self/status", O_RDONLY, 0);
    if (fd < 0) return false;
    char buf[4096];
    ssize_t n = syscall(SYS_read, fd, buf, sizeof(buf) - 1);
    syscall(SYS_close, fd);
    if (n <= 0) return false;
    buf[n] = 0;
    const char* p = strstr(buf, "TracerPid:");
    if (!p) return false;
    p += 10;
    while (*p == ' ' || *p == '\t') p++;
    return atoi(p) > 0;
}

static bool detectEmulator() {
    if (sysExists("/dev/socket/qemud")) return true;
    if (sysExists("/dev/qemu_pipe")) return true;
    char value[PROP_VALUE_MAX] = {0};
    if (__system_property_get("ro.kernel.qemu", value) > 0) {
        if (strcmp(value, "1") == 0) return true;
    }
    return false;
}

static bool isEnvironmentSafe() {
    if (detectFrida())    return false;
    if (detectXposed())   return false;
    if (detectTracer())   return false;
    if (detectEmulator()) return false;
    return true;
}

// ================================================================
// Anti-memory-dump
// ================================================================
static bool enableAntiDump() {
    struct rlimit rl;
    rl.rlim_cur = 0;
    rl.rlim_max = 0;
    setrlimit(RLIMIT_CORE, &rl);

    int fd = open("/proc/self/coredump_filter", O_WRONLY);
    if (fd >= 0) {
        const char* zero = "0";
        write(fd, zero, 1);
        close(fd);
    }
    return true;
}

static std::atomic<bool> g_antiDumpDone{false};
static inline void ensureAntiDumpInit() {
    if (g_antiDumpDone.exchange(true)) return;
    enableAntiDump();
}

// ================================================================
// JWT helpers
// ================================================================
static bool looksLikeJwt(const std::string& tok) {
    if (tok.size() < 40) return false;
    if (tok.substr(0, 3) != "eyJ") return false;
    size_t d1 = tok.find('.');
    if (d1 == std::string::npos) return false;
    size_t d2 = tok.find('.', d1 + 1);
    if (d2 == std::string::npos) return false;
    return true;
}

static long long extractJwtExp(const std::string& jwt) {
    size_t d1 = jwt.find('.');
    if (d1 == std::string::npos) return 0;
    size_t d2 = jwt.find('.', d1 + 1);
    if (d2 == std::string::npos) return 0;
    std::string payloadB64 = jwt.substr(d1 + 1, d2 - d1 - 1);
    std::string payload = base64UrlDecode(payloadB64);
    std::string expStr = getJsonField(payload, "exp");
    if (expStr.empty()) return 0;
    try { return std::stoll(expStr); } catch (...) { return 0; }
}

// ================================================================
// Cert pin compare — OBFUSCATED inline
// ================================================================
static bool isPinnedHash(const std::string& h) {
    if (h == OBF_STR("6bd255ea86d4cf05e8aed3d6e071895b8c29736ba83908dbcf409817aa8b03ed")) return true;
    if (h == OBF_STR("fec41e32ca75c295a6240fa639d3abe3bfb5cb131d6690e2331a176bed2e5bd2")) return true;
    if (h == OBF_STR("170b2def1e9c89c59970f25c62ffe64c0fba73989cd29a098dc0a2d405d87ed7")) return true;
    return false;
}

// ================================================================
// JNI — verifyHashes
// ================================================================
extern "C" JNIEXPORT jboolean JNICALL
Java_com_android_support_SecurityNative_verifyHashes(
        JNIEnv* env, jclass, jstring jsig, jstring jdex, jboolean jdebug) {

    ensureAntiDumpInit();

    if (!jsig) return JNI_FALSE;
    const char* csig = env->GetStringUTFChars(jsig, nullptr);
    if (!csig) return JNI_FALSE;
    std::string sigHash = toLower(std::string(csig));
    env->ReleaseStringUTFChars(jsig, csig);

    std::string dexHash;
    if (jdex) {
        const char* cdex = env->GetStringUTFChars(jdex, nullptr);
        if (cdex) {
            dexHash = toLower(std::string(cdex));
            env->ReleaseStringUTFChars(jdex, cdex);
        }
    }

    bool isDebug = (jdebug == JNI_TRUE);

    if (!isDebug) {
        std::string expected = toLower(std::string(SHA256_PRIMARY()));
        bool ok = false;
        if (!expected.empty() && sigHash.size() == expected.size())
            if (constTimeEquals(sigHash, expected)) ok = true;

        if (!ok) {
            std::string a1 = toLower(std::string(SHA256_ALT1()));
            if (!a1.empty() && sigHash.size() == a1.size())
                if (constTimeEquals(sigHash, a1)) ok = true;
            std::string a2 = toLower(std::string(SHA256_ALT2()));
            if (!ok && !a2.empty() && sigHash.size() == a2.size())
                if (constTimeEquals(sigHash, a2)) ok = true;
        }
        if (!ok) return JNI_FALSE;

        std::string expectedDex = toLower(std::string(EXPECTED_DEX_HASH()));
        if (!expectedDex.empty()) {
            bool placeholder = true;
            for (char c : expectedDex) if (c != '0') { placeholder = false; break; }
            if (!placeholder) {
                if (dexHash.empty() || dexHash.size() != expectedDex.size())
                    return JNI_FALSE;
                if (!constTimeEquals(dexHash, expectedDex))
                    return JNI_FALSE;
            }
        }
    }

    if (!isEnvironmentSafe()) return JNI_FALSE;
    return JNI_TRUE;
}

// ================================================================
// JNI — verifyLibHash
// ================================================================
extern "C" JNIEXPORT jboolean JNICALL
Java_com_android_support_SecurityNative_verifyLibHash(
        JNIEnv* env, jclass, jstring jhash) {
    if (!jhash) return JNI_FALSE;
    const char* raw = env->GetStringUTFChars(jhash, nullptr);
    if (!raw) return JNI_FALSE;
    std::string given = toLower(std::string(raw));
    env->ReleaseStringUTFChars(jhash, raw);

    std::string expected = toLower(std::string(EXPECTED_LIB_HASH()));
    bool placeholder = true;
    for (char c : expected) if (c != '0') { placeholder = false; break; }
    if (placeholder) return JNI_TRUE;

    if (given.size() != expected.size()) return JNI_FALSE;
    if (!constTimeEquals(given, expected)) return JNI_FALSE;
    return JNI_TRUE;
}

// ================================================================
// JNI — verifyLogin (legacy path)
// ================================================================
extern "C" JNIEXPORT jstring JNICALL
Java_com_android_support_SecurityNative_verifyLogin(
        JNIEnv* env, jclass, jstring jUser, jstring jPass, jstring jUserJson) {

    auto fail = [&](const char* reason) -> jstring {
        std::string s = "{\"ok\":false,\"reason\":\"";
        s += reason; s += "\"}";
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

    std::string uL = toLower(sUser), dUL = toLower(dbUser);
    if (!constTimeEquals(uL, dUL)) return fail("invalid_credentials");
    if (!constTimeEquals(sPass, dbPass)) return fail("invalid_credentials");
    if (dbStatus != "true") return fail("blocked");

    long long expiryMs = 0;
    std::string dbTime = getJsonField(sJson, "time");
    if (!dbTime.empty()) {
        try { double d = std::stod(dbTime);
            if (d > 1e11 && d < 9.2e18) expiryMs = (long long)d;
        } catch (...) {}
    }
    if (expiryMs > 0) {
        long long nowMs = (long long)time(nullptr) * 1000LL;
        if (nowMs > expiryMs) return fail("expired");
    }

    std::string payload = dbUser + "|" + dbPass + "|" + std::to_string(expiryMs) + "|" + dbStatus;
    std::string token = SecSHA::hmacHex(deriveKey(), payload);

    std::string out = "{\"ok\":true,\"token\":\"";
    out += token;
    out += "\",\"user\":\""; out += dbUser;
    out += "\",\"status\":\"true\",\"expiry\":\"";
    out += std::to_string(expiryMs);
    out += "\"}";
    return env->NewStringUTF(out.c_str());
}

// ================================================================
// JNI — verifyLoginWithTime
// ================================================================
extern "C" JNIEXPORT jstring JNICALL
Java_com_android_support_SecurityNative_verifyLoginWithTime(
        JNIEnv* env, jclass, jstring jUser, jstring jPass, jstring jUserJson, jlong jNowMs) {

    auto fail = [&](const char* reason) -> jstring {
        std::string s = "{\"ok\":false,\"reason\":\"";
        s += reason; s += "\"}";
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

    std::string uL = toLower(sUser), dUL = toLower(dbUser);
    if (!constTimeEquals(uL, dUL)) return fail("invalid_credentials");
    if (!constTimeEquals(sPass, dbPass)) return fail("invalid_credentials");
    if (dbStatus != "true") return fail("blocked");

    long long expiryMs = 0;
    std::string dbTime = getJsonField(sJson, "time");
    if (!dbTime.empty()) {
        try { double d = std::stod(dbTime);
            if (d > 1e11 && d < 9.2e18) expiryMs = (long long)d;
        } catch (...) {}
    }
    if (expiryMs > 0) {
        if ((long long)jNowMs > expiryMs) return fail("expired");
    }

    std::string payload = dbUser + "|" + dbPass + "|" + std::to_string(expiryMs) + "|" + dbStatus;
    std::string token = SecSHA::hmacHex(deriveKey(), payload);

    std::string out = "{\"ok\":true,\"token\":\"";
    out += token;
    out += "\",\"user\":\""; out += dbUser;
    out += "\",\"status\":\"true\",\"expiry\":\"";
    out += std::to_string(expiryMs);
    out += "\"}";
    return env->NewStringUTF(out.c_str());
}

// ================================================================
// JNI — verifySessionToken (JWT + legacy HMAC)
// ================================================================
extern "C" JNIEXPORT jboolean JNICALL
Java_com_android_support_SecurityNative_verifySessionToken(
        JNIEnv* env, jclass,
        jstring jToken, jstring jUser, jstring jPass, jstring jExpiry) {

    if (!jToken || !jUser || !jExpiry) return JNI_FALSE;
    const char* ct = env->GetStringUTFChars(jToken, nullptr);
    const char* cu = env->GetStringUTFChars(jUser, nullptr);
    const char* ce = env->GetStringUTFChars(jExpiry, nullptr);
    const char* cp = jPass ? env->GetStringUTFChars(jPass, nullptr) : nullptr;

    if (!ct || !cu || !ce) {
        if (ct) env->ReleaseStringUTFChars(jToken, ct);
        if (cu) env->ReleaseStringUTFChars(jUser, cu);
        if (ce) env->ReleaseStringUTFChars(jExpiry, ce);
        if (cp && jPass) env->ReleaseStringUTFChars(jPass, cp);
        return JNI_FALSE;
    }
    std::string token(ct), user(cu), expiry(ce), pass(cp ? cp : "");
    env->ReleaseStringUTFChars(jToken, ct);
    env->ReleaseStringUTFChars(jUser, cu);
    env->ReleaseStringUTFChars(jExpiry, ce);
    if (cp && jPass) env->ReleaseStringUTFChars(jPass, cp);

    if (token.empty()) return JNI_FALSE;

    // JWT path
    if (looksLikeJwt(token)) {
        long long jwtExpSec = extractJwtExp(token);
        if (jwtExpSec > 0) {
            long long nowSec = (long long)time(nullptr);
            if (nowSec > (jwtExpSec + 60)) return JNI_FALSE;
        }
        try {
            long long expiryMs = std::stoll(expiry);
            if (expiryMs > 0) {
                long long nowMs = (long long)time(nullptr) * 1000LL;
                if (nowMs > (expiryMs + 60000LL)) return JNI_FALSE;
            }
        } catch (...) {}
        return JNI_TRUE;
    }

    // Legacy HMAC
    std::string payload = user + "|" + pass + "|" + expiry + "|true";
    std::string expected = SecSHA::hmacHex(deriveKey(), payload);
    if (!constTimeEquals(token, expected)) return JNI_FALSE;

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
// JNI — getProjectId (NEW: from native)
// ================================================================
extern "C" JNIEXPORT jstring JNICALL
Java_com_android_support_SecurityNative_getProjectId(JNIEnv* env, jclass) {
    return env->NewStringUTF(APPWRITE_PROJECT_ID());
}

// ================================================================
// JNI — verifyCertPin (NEW: pins in native)
// ================================================================
extern "C" JNIEXPORT jboolean JNICALL
Java_com_android_support_SecurityNative_verifyCertPin(JNIEnv* env, jclass, jstring jHash) {
    if (!jHash) return JNI_FALSE;
    const char* raw = env->GetStringUTFChars(jHash, nullptr);
    if (!raw) return JNI_FALSE;
    std::string h = toLower(std::string(raw));
    env->ReleaseStringUTFChars(jHash, raw);
    return isPinnedHash(h) ? JNI_TRUE : JNI_FALSE;
}

// ================================================================
// JNI — verifyNativeSig (NEW: HMAC server response)
// ================================================================
extern "C" JNIEXPORT jboolean JNICALL
Java_com_android_support_SecurityNative_verifyNativeSig(
        JNIEnv* env, jclass, jstring jUser, jstring jExpiry, jstring jSig) {

    if (!jUser || !jExpiry || !jSig) return JNI_FALSE;
    const char* cu = env->GetStringUTFChars(jUser, nullptr);
    const char* ce = env->GetStringUTFChars(jExpiry, nullptr);
    const char* cs = env->GetStringUTFChars(jSig, nullptr);
    if (!cu || !ce || !cs) {
        if (cu) env->ReleaseStringUTFChars(jUser, cu);
        if (ce) env->ReleaseStringUTFChars(jExpiry, ce);
        if (cs) env->ReleaseStringUTFChars(jSig, cs);
        return JNI_FALSE;
    }
    std::string user(cu), expiry(ce), given(cs);
    env->ReleaseStringUTFChars(jUser, cu);
    env->ReleaseStringUTFChars(jExpiry, ce);
    env->ReleaseStringUTFChars(jSig, cs);

    // Server payload format: user|expiry|true
    std::string payload = user + "|" + expiry + "|true";
    std::string expected = SecSHA::hmacHex(deriveNativeKey(), payload);

    if (given.empty() || given.size() != expected.size()) return JNI_FALSE;
    return constTimeEquals(given, expected) ? JNI_TRUE : JNI_FALSE;
}

// ================================================================
// JNI — verifyLoginSig (NEW: HMAC of login request)
// ================================================================
extern "C" JNIEXPORT jboolean JNICALL
Java_com_android_support_SecurityNative_verifyLoginSig(
        JNIEnv* env, jclass, jstring jUser, jstring jPass, jstring jSig) {

    if (!jUser || !jPass || !jSig) return JNI_FALSE;
    const char* cu = env->GetStringUTFChars(jUser, nullptr);
    const char* cp = env->GetStringUTFChars(jPass, nullptr);
    const char* cs = env->GetStringUTFChars(jSig, nullptr);
    if (!cu || !cp || !cs) {
        if (cu) env->ReleaseStringUTFChars(jUser, cu);
        if (cp) env->ReleaseStringUTFChars(jPass, cp);
        if (cs) env->ReleaseStringUTFChars(jSig, cs);
        return JNI_FALSE;
    }
    std::string user(cu), pass(cp), given(cs);
    env->ReleaseStringUTFChars(jUser, cu);
    env->ReleaseStringUTFChars(jPass, cp);
    env->ReleaseStringUTFChars(jSig, cs);

    std::string payload = user + "|" + pass;
    std::string expected = SecSHA::hmacHex(deriveNativeKey(), payload);
    if (given.empty() || given.size() != expected.size()) return JNI_FALSE;
    return constTimeEquals(given, expected) ? JNI_TRUE : JNI_FALSE;
}

// ================================================================
// JNI — URL builders
// ================================================================
extern "C" JNIEXPORT jstring JNICALL
Java_com_android_support_SecurityNative_getQueryUrl(JNIEnv* env, jclass, jstring jUser) {
    // No longer used (Firebase fallback removed), but kept for compat
    (void)env; (void)jUser;
    return env->NewStringUTF("");
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_android_support_SecurityNative_getUpdateUrl(JNIEnv* env, jclass) {
    std::string url;
    url += OBF_STR("https://modx-lab-5a6ee");
    url += OBF_STR("-default-rtdb.firebaseio.com");
    url += OBF_STR("/update.json");
    return env->NewStringUTF(url.c_str());
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_android_support_SecurityNative_getCloudFnUrl(JNIEnv* env, jclass) {
    return env->NewStringUTF(CLOUD_FN_URL());
}