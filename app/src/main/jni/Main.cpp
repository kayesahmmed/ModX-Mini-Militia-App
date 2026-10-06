// ================================================================
// Mini Militia — Main.cpp v116.0 (Secure + Teleport Pad)
//  - Offset encryption (XOR runtime-decrypted)
//  - String obfuscation via OBFUSCATE()
//  - Teleport: recursion-guarded body-pointer discovery (wider scan)
//  - Dual hook: Soldier + CollisionObject getBodyPosition
//  - CRASH FIX: force-`out` ONLY after discovery success
//  - NEW: 4-quadrant on-screen Teleport Pad (tap to teleport)
// ================================================================

#include <list>
#include <vector>
#include <cstring>
#include <cstdarg>
#include <cstdio>
#include <cstdlib>
#include <ctime>
#include <cmath>
#include <cstdint>
#include <cctype>
#include <algorithm>
#include <pthread.h>
#include <thread>
#include <string>
#include <jni.h>
#include <unistd.h>
#include <fstream>
#include <iostream>
#include <dlfcn.h>
#include <fcntl.h>
#include <signal.h>
#include <setjmp.h>
#include <chrono>
#include <atomic>
#include <mutex>
#include <sstream>
#include <iomanip>
#include <unordered_set>
#include <unordered_map>
#include <android/log.h>
#include <sys/mman.h>

#include "Includes/Logger.h"
#include "Includes/obfuscate.h"
#include "Includes/Utils.hpp"
#include "Menu/Menu.hpp"
#include "Menu/Jni.hpp"
#include "Includes/Macros.h"

#ifndef CP_VECT_DEFINED
#define CP_VECT_DEFINED
struct cpVect { double x; double y; };
#endif
struct MPoint { float x, y; };
struct MSSize { float w, h; };

#define targetLibName OBFUSCATE("libcocos2dcpp.so")
#define LOG_TAG       "MMMod"
static constexpr float RAD2DEG = 57.29577951f;
static constexpr float DEG2RAD = 0.01745329252f;

// ==================================================================
// OFFSET ENCRYPTION
// ==================================================================
namespace SecOff {
    static constexpr uint64_t KEY = 0xB4E7A1C3D9F20586ULL;
    static constexpr uintptr_t enc(uintptr_t v) {
        return v ^ (uintptr_t)(KEY & 0x0000FFFFFFFFFFFFULL);
    }
    __attribute__((noinline))
    static uintptr_t dec(uintptr_t v) {
        volatile uint64_t k = KEY;
        return v ^ (uintptr_t)(k & 0x0000FFFFFFFFFFFFULL);
    }
}
#define ENC_OFF(v) SecOff::enc(v)
#define DEC_OFF(v) SecOff::dec(v)

// ==================================================================
// Logging
// ==================================================================
static int g_logFd = -1;
static std::atomic<int> g_crashCount{0};
static constexpr int MAX_CRASHES_PER_SESSION = 200;

static void ensureLogFd() {
    if (g_logFd >= 0) return;
    const char* paths[] = {
        "/storage/emulated/0/Android/data/com.appsomniacs.da2/files/MM_Mod.log",
        "/sdcard/Android/data/com.appsomniacs.da2/files/MM_Mod.log",
        "/storage/emulated/0/MM_Mod.log",
        "/sdcard/MM_Mod.log",
        "/data/local/tmp/MM_Mod.log"
    };
    for (auto p : paths) {
        int fd = open(p, O_WRONLY | O_CREAT | O_APPEND, 0666);
        if (fd >= 0) { g_logFd = fd; return; }
    }
}
static void crashLogRaw(const char* msg, int len) {
    ensureLogFd(); if (g_logFd < 0) return;
    write(g_logFd, msg, len);
}
static void crashLog(const char* tag, const char* fmt, ...) {
    ensureLogFd();
    char msg[480];
    va_list args; va_start(args, fmt);
    vsnprintf(msg, sizeof(msg), fmt, args);
    va_end(args);
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "[%s] %s", tag, msg);
    if (g_logFd < 0) return;
    char buf[520];
    int n = snprintf(buf, sizeof(buf), "[%s] %s\n", tag, msg);
    if (n > 0) write(g_logFd, buf, n);
}
static void traceLog(const char* fmt, ...) {
    char msg[400];
    va_list args; va_start(args, fmt);
    vsnprintf(msg, sizeof(msg), fmt, args);
    va_end(args);
    __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, "%s", msg);
    if (g_logFd < 0) ensureLogFd();
    if (g_logFd >= 0) {
        char b[440];
        int n = snprintf(b, sizeof(b), "[TRACE] %s\n", msg);
        if (n > 0) write(g_logFd, b, n);
    }
}

// ==================================================================
// Crash guard
// ==================================================================
static __thread sigjmp_buf tls_guard;
static __thread volatile sig_atomic_t tls_guardActive = 0;
static void native_crash_handler(int sig, siginfo_t* info, void*) {
    if (tls_guardActive) { tls_guardActive = 0; siglongjmp(tls_guard, 1); }
    ensureLogFd();
    if (g_crashCount.fetch_add(1) < MAX_CRASHES_PER_SESSION) {
        char buf[256];
        int n = snprintf(buf, sizeof(buf), "\n!!! SIG %d fault=%p\n",
                         sig, info ? info->si_addr : nullptr);
        if (n > 0) crashLogRaw(buf, n);
    }
    struct sigaction sa; memset(&sa, 0, sizeof(sa));
    sa.sa_handler = SIG_DFL; sigaction(sig, &sa, nullptr); raise(sig);
}
static void install_crash_handler() {
    ensureLogFd();
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "=== MMMod v116.0 boot ===");
    crashLog("BOOT", "Crash handler installed");
    struct sigaction sa; memset(&sa, 0, sizeof(sa));
    sa.sa_sigaction = native_crash_handler;
    sa.sa_flags = SA_SIGINFO | SA_NODEFER;
    sigemptyset(&sa.sa_mask);
    sigaction(SIGSEGV, &sa, nullptr); sigaction(SIGABRT, &sa, nullptr);
    sigaction(SIGBUS,  &sa, nullptr); sigaction(SIGILL,  &sa, nullptr);
    sigaction(SIGFPE,  &sa, nullptr);
}
#define GUARD_ENTER() (sigsetjmp(tls_guard, 1) == 0)
#define GUARD_SET()   (tls_guardActive = 1)
#define GUARD_CLR()   (tls_guardActive = 0)

// ==================================================================
// ESP colors
// ==================================================================
std::atomic<unsigned int> g_espColorArgb{ 0xFF00FF88u };
static int SKY_R = 0x00, SKY_G = 0xFF, SKY_B = 0x88;
static int SKY_LIGHT_R = 0x99, SKY_LIGHT_G = 0xFF, SKY_LIGHT_B = 0xCF;
static int SKY_DEEP_R = 0x00, SKY_DEEP_G = 0x99, SKY_DEEP_B = 0x51;
static inline void RecomputeSkyColors() {
    unsigned int c = g_espColorArgb.load();
    SKY_R = (int)((c >> 16) & 0xFF);
    SKY_G = (int)((c >>  8) & 0xFF);
    SKY_B = (int)( c        & 0xFF);
    SKY_LIGHT_R = SKY_R + ((255 - SKY_R) * 3) / 5;
    SKY_LIGHT_G = SKY_G + ((255 - SKY_G) * 3) / 5;
    SKY_LIGHT_B = SKY_B + ((255 - SKY_B) * 3) / 5;
    SKY_DEEP_R  = (SKY_R * 3) / 5;
    SKY_DEEP_G  = (SKY_G * 3) / 5;
    SKY_DEEP_B  = (SKY_B * 3) / 5;
}
__attribute__((constructor)) void early_init() {
    install_crash_handler(); RecomputeSkyColors();
}
extern "C" JNIEXPORT void JNICALL
Java_com_android_support_Main_setNativeCrashDir(JNIEnv*, jclass, jstring) {}

// ==================================================================
// ENCRYPTED OFFSETS
// ==================================================================
namespace Off {
    // Weapon
    static const uintptr_t Weapon_getRandomFiringAngle     = ENC_OFF(0x00f40ad0);
    static const uintptr_t Weapon_getBulletSpeed           = ENC_OFF(0x00f40ab0);
    static const uintptr_t Weapon_getRange                 = ENC_OFF(0x00f40784);
    static const uintptr_t Weapon_setFireAngle             = ENC_OFF(0x00f40b5c);
    static const uintptr_t Weapon_getDamage                = ENC_OFF(0x00f4076c);
    static const uintptr_t Weapon_getRoundsPerFire         = ENC_OFF(0x00f40aa0);
    static const uintptr_t Weapon_getAmmo                  = ENC_OFF(0x00f406bc);
    static const uintptr_t Weapon_setAmmo                  = ENC_OFF(0x00f40720);
    static const uintptr_t Weapon_subAmmo                  = ENC_OFF(0x00f40884);
    static const uintptr_t Weapon_getClip                  = ENC_OFF(0x00f406dc);
    static const uintptr_t Weapon_setClip                  = ENC_OFF(0x00f40730);
    static const uintptr_t Weapon_getClipCapacity          = ENC_OFF(0x00f40794);
    static const uintptr_t Weapon_getAmmoCapacity          = ENC_OFF(0x00f4078c);
    static const uintptr_t Weapon_getReloadTime            = ENC_OFF(0x00f4079c);
    static const uintptr_t Weapon_isDualWield              = ENC_OFF(0x00f40ab8);
    static const uintptr_t Weapon_isDualWieldOnly          = ENC_OFF(0x00f40ac0);
    static const uintptr_t Weapon_isDualWieldPrimaryOnly   = ENC_OFF(0x00f40ac8);
    static const uintptr_t Weapon_pickupAsDual             = ENC_OFF(0x00f40b4c);
    static const uintptr_t Weapon_setPickupAsDual          = ENC_OFF(0x00f40b54);
    static const uintptr_t Weapon_getZoomLevel             = ENC_OFF(0x00f40a90);
    static const uintptr_t Weapon_setZoomLevel             = ENC_OFF(0x00f409d4);
    static const uintptr_t Weapon_applyMaxZoomScale        = ENC_OFF(0x00f40a70);
    static const uintptr_t Weapon_getZoomScale             = ENC_OFF(0x00f408f4);
    static const uintptr_t Weapon_getMeleeDamage           = ENC_OFF(0x00f40774);
    static const uintptr_t Weapon_getMeleeLength           = ENC_OFF(0x00f4077c);
    // MapManager
    static const uintptr_t MapManager_addStaticBodyShape   = ENC_OFF(0x00eeb038);
    static const uintptr_t MapManager_addStaticBodyPoly    = ENC_OFF(0x00eeac7c);
    static const uintptr_t MapManager_isCollisionTile      = ENC_OFF(0x00eec664);
    static const uintptr_t MapManager_mapCollision         = ENC_OFF(0x00eec9f0);
    static const uintptr_t MapManager_isBoundryTile        = ENC_OFF(0x00eece3c);
    static const uintptr_t MapManager_getMaxPower          = ENC_OFF(0x00eea748);
    static const uintptr_t MapManager_getGravityFactor     = ENC_OFF(0x00eea740);
    // EffectsManager
    static const uintptr_t EffectsManager_addExplosionAt   = ENC_OFF(0x00eb1f20);
    static const uintptr_t EffectsManager_addGasCloudAt    = ENC_OFF(0x00eb2360);
    // ProjectileManager
    static const uintptr_t ProjectileManager_addBullet     = ENC_OFF(0x00f04b7c);
    static const uintptr_t ProjectileManager_addShell      = ENC_OFF(0x00f052d8);
    static const uintptr_t ProjectileManager_addRocket     = ENC_OFF(0x00f05008);
    static const uintptr_t ProjectileManager_addGrenade    = ENC_OFF(0x00f04d58);
    static const uintptr_t ProjectileManager_addSaw        = ENC_OFF(0x00f05750);
    static const uintptr_t ProjectileManager_addFlame      = ENC_OFF(0x00f055a4);
    // SoldierController
    static const uintptr_t SoldierController_getBodyPosition    = ENC_OFF(0x00f13828);
    static const uintptr_t SoldierController_getHP              = ENC_OFF(0x00f137d4);
    static const uintptr_t SoldierController_setHP              = ENC_OFF(0x00f137e4);
    static const uintptr_t SoldierController_setAlive           = ENC_OFF(0x00f13860);
    static const uintptr_t SoldierController_getSoldierView     = ENC_OFF(0x00f13074);
    static const uintptr_t SoldierController_isDead             = ENC_OFF(0x00f137f4);
    static const uintptr_t SoldierController_addDamage          = ENC_OFF(0x00f135a8);
    static const uintptr_t SoldierController_getPrimaryWeapon   = ENC_OFF(0x00f1321c);
    static const uintptr_t SoldierController_getSecondaryWeapon = ENC_OFF(0x00f13224);
    static const uintptr_t SoldierController_getDualWeapon      = ENC_OFF(0x00f1322c);
    static const uintptr_t SoldierController_getSideWeapon      = ENC_OFF(0x00f13234);
    static const uintptr_t SoldierController_fire               = ENC_OFF(0x00f1323c);
    static const uintptr_t SoldierController_setThrust          = ENC_OFF(0x00f13044);
    // Collision
    static const uintptr_t CollisionObject_getBodyPosition      = ENC_OFF(0x00eac428);
    static const uintptr_t CollisionObject_getTeamId            = ENC_OFF(0x00eac4f0);
    // SoldierLocalController
    static const uintptr_t SoldierLocalController_updateStep            = ENC_OFF(0x00f14478);
    static const uintptr_t SoldierLocalController_addDamage             = ENC_OFF(0x00f18c64);
    static const uintptr_t SoldierLocalController_activatePlayer        = ENC_OFF(0x00f17bb4);
    static const uintptr_t SoldierLocalController_setPower              = ENC_OFF(0x00f156b4);
    static const uintptr_t SoldierLocalController_switchPrimaryToDual   = ENC_OFF(0x00f17760);
    static const uintptr_t SoldierLocalController_switchSecondaryToDual = ENC_OFF(0x00f178b8);
    // SoldierManager
    static const uintptr_t SoldierManager_getLocalController   = ENC_OFF(0x00f1aa00);
    static const uintptr_t SoldierManager_updateRemoteSoldiers = ENC_OFF(0x00f1a888);
    static const uintptr_t SoldierManager_updateStep           = ENC_OFF(0x00f1a348);
    static const uintptr_t SoldierManager_spawnPlayer          = ENC_OFF(0x00f1a618);
    static const uintptr_t SoldierManager_respawnPlayer        = ENC_OFF(0x00f19f78);
    static const uintptr_t SoldierManager_getRespawnTime       = ENC_OFF(0x00f1b24c);
    static const uintptr_t SoldierManager_isRespawning         = ENC_OFF(0x00f1b254);
    // Controllers
    static const uintptr_t SoldierRemoteController_updateStep = ENC_OFF(0x00f1d620);
    static const uintptr_t SoldierAIController_updateStep     = ENC_OFF(0x00f0fd80);
    static const uintptr_t EnemyManager_updateStep            = ENC_OFF(0x00eb4d18);
    // Drones
    static const uintptr_t HumanoidDrone_addDamage    = ENC_OFF(0x00edf408);
    static const uintptr_t HawkDrone_addDamage        = ENC_OFF(0x00eddc44);
    static const uintptr_t WormDrone_addDamage        = ENC_OFF(0x00f4b320);
    static const uintptr_t HumanoidDrone_updateStep   = ENC_OFF(0x00edf000);
    static const uintptr_t HawkDrone_updateStep       = ENC_OFF(0x00edd640);
    static const uintptr_t WormDrone_updateStep       = ENC_OFF(0x00f4aaa8);
    // WeaponsModel
    static const uintptr_t WeaponsModel_isUnlockable            = ENC_OFF(0x01113a88);
    static const uintptr_t WeaponsModel_isUpgradable            = ENC_OFF(0x01113a60);
    static const uintptr_t WeaponsModel_getDualWieldUnlockLevel = ENC_OFF(0x01113984);
    // Misc
    static const uintptr_t Stage_update                 = ENC_OFF(0x00f21938);
        static const uintptr_t PhysicsManager_updateStep = ENC_OFF(0x00f00564);
    static const uintptr_t NetworkMessageDispatcher_updatePeerDamage = ENC_OFF(0x00ef5d60);
    static const uintptr_t NetworkManager_sendWeaponChange = ENC_OFF(0x00ef3ec4);
    static const uintptr_t CCNode_convertToWorldSpaceAR = ENC_OFF(0x00f88018);
    static const uintptr_t CCDirector_sharedDirector    = ENC_OFF(0x00f8f5c4);
    static const uintptr_t CCDirector_getVisibleSize    = ENC_OFF(0x00f90378);
    static const uintptr_t Joypad_getDirectionAngle     = ENC_OFF(0x00ee284c);
    static const uintptr_t Joypad_getDirectionVector    = ENC_OFF(0x00ee2aa0);
    static const uintptr_t SoldierView_setPlayerHealth  = ENC_OFF(0x00f20960);
    static const uintptr_t SoldierView_getPlayerName    = ENC_OFF(0x00f20994);
    // Weapon sprayers
    static const uintptr_t AK47_triggerPull    = ENC_OFF(0x00ea2e74);
    static const uintptr_t AA12_triggerPull    = ENC_OFF(0x00ea2128);
    static const uintptr_t DEAGLE_triggerPull  = ENC_OFF(0x00eacaf0);
    static const uintptr_t M16_triggerPull     = ENC_OFF(0x00ee4298);
    static const uintptr_t MINIGUN_triggerPull = ENC_OFF(0x00ee7334);
    static const uintptr_t EMP_triggerPull     = ENC_OFF(0x00ead8d0);
    static const uintptr_t RG6_triggerPull     = ENC_OFF(0x00f06640);
    static const uintptr_t M14_triggerPull     = ENC_OFF(0x00ee3608);
    static const uintptr_t MAGNUM_triggerPull  = ENC_OFF(0x00ee63ac);
    static const uintptr_t MP5_triggerPull     = ENC_OFF(0x00ee90a0);
    static const uintptr_t TAVOR_triggerPull   = ENC_OFF(0x00f2e8e4);
    static const uintptr_t TEC9_triggerPull    = ENC_OFF(0x00f2f534);
    static const uintptr_t HUNTING_triggerPull = ENC_OFF(0x00edf94c);
    static const uintptr_t SAWGUN_triggerPull  = ENC_OFF(0x00f0afb8);
    static const uintptr_t SMAW_triggerPull    = ENC_OFF(0x00f0cb2c);
    static const uintptr_t XM8_triggerPull     = ENC_OFF(0x00f4b834);
    static const uintptr_t PHASR_triggerPull   = ENC_OFF(0x00ef921c);
    // Enemy / Explosion
    static const uintptr_t Enemy_canSeeTarget          = ENC_OFF(0x00eb3940);
    static const uintptr_t Explosion_applyDamage       = ENC_OFF(0x00eb7ac8);
    static const uintptr_t GasCloud_applyDamage        = ENC_OFF(0x00ed5808);
    static const uintptr_t PlasmaBall_applyDamage      = ENC_OFF(0x00f00b84);
    static const uintptr_t SAW_checkMapCollision       = ENC_OFF(0x00f0a410);
    static const uintptr_t SAW_updateItemStep          = ENC_OFF(0x00f0a2a8);
    static const uintptr_t ProxyMine_updateStep        = ENC_OFF(0x00f05db8);
    static const uintptr_t ProxyMine_reset             = ENC_OFF(0x00f05c98);
    // Special
    static const uintptr_t MaxLevel_patch              = ENC_OFF(0x011bae9c);
}

uintptr_t         g_libBase = 0;
std::atomic<bool> g_libReady{false};

// ==================================================================
// Mod registry
// ==================================================================
struct ModDef {
    const char*  name     = nullptr;
    uintptr_t    offset   = 0;
    const char*  patchHex = nullptr;
    MemoryPatch  patch;
    bool         enabled  = false;
    bool         init     = false;
};
static std::vector<ModDef> g_mods;
static std::mutex          g_modsMutex;
static int RegisterMod(const char* name, uintptr_t off, const char* hex) {
    std::lock_guard<std::mutex> l(g_modsMutex);
    ModDef d{}; d.name = name; d.offset = off; d.patchHex = hex;
    g_mods.push_back(d);
    return (int)g_mods.size() - 1;
}
static void ApplyModByIndex(int idx, bool on) {
    if (idx < 0) return;
    std::lock_guard<std::mutex> l(g_modsMutex);
    if (idx >= (int)g_mods.size()) return;
    if (!g_libReady.load()) return;
    ModDef& m = g_mods[idx];
    if (!m.init) {
        m.patch = MemoryPatch::createWithHex(g_libBase + DEC_OFF(m.offset), m.patchHex);
        m.init = true;
    }
    if (on && !m.enabled)      { m.patch.Modify();  m.enabled = true; }
    else if (!on && m.enabled) { m.patch.Restore(); m.enabled = false; }
}

// ==================================================================
// Typedefs
// ==================================================================
typedef void  (*MgrUpdateRemote_t)(void*, float);
typedef void  (*MgrUpdateStep_t)(void*, float);
typedef void  (*MgrSpawnPlayer_t)(void*);
typedef void  (*MgrRespawnPlayer_t)(void*);
typedef void  (*RemoteUpdateStep_t)(void*, float);
typedef void  (*AIUpdateStep_t)(void*, float);
typedef void  (*EnemyMgrUpdateStep_t)(void*, float);
typedef void  (*StageUpdate_t)(void*, float);
typedef void  (*PhysicsMgrUpdate_t)(void*, float);
typedef void  (*LocalActivate_t)(void*);
typedef void* (*getLocalController_t)(void*);
typedef void  (*getBodyPosition_t)(cpVect*, void*);
typedef int   (*getHP_t)(void*);
typedef void  (*setHP_t)(void*, int);
typedef void  (*setAlive_t)(void*, bool);
typedef int   (*getTeamId_t)(void*);
typedef void* (*getSoldierView_t)(void*);
typedef int   (*isDead_t)(void*);
typedef void  (*getPlayerName_t)(std::string*, void*);
typedef MPoint (*convertToWorldSpaceAR_t)(void*, const MPoint*);
typedef void  (*soldierAddDamage_t)(void*, float, void*, int, bool);
typedef void  (*droneAddDamage_t)(void*, int, void*, int);
typedef void  (*droneUpdateStep_t)(void*, float);
typedef void  (*updatePeerDamage_t)(void*, void*, void*);
typedef void  (*setPlayerHealth_t)(void*, float);
typedef void*  (*directorShared_t)();
typedef MSSize (*directorGetSize_t)(void*);
typedef void* (*getWeapon_t)(void*);
typedef void  (*soldierFire_t)(void*, float);
typedef float (*getRandomFiringAngle_t)(void*);
typedef int   (*getBulletSpeed_t)(void*);
typedef int   (*getRange_t)(void*);
typedef void  (*setFireAngle_wpn_t)(void*, float);
typedef int   (*getRoundsPerFire_t)(void*);
typedef int   (*getAmmo_t)(void*);
typedef void  (*setAmmo_t)(void*, int);
typedef void  (*subAmmo_t)(void*, int);
typedef int   (*getClip_t)(void*);
typedef void  (*setClip_t)(void*, int);
typedef int   (*getClipCap_t)(void*);
typedef int   (*getAmmoCap_t)(void*);
typedef int   (*getReloadTime_t)(void*);
typedef bool  (*isDualWield_t)(void*);
typedef bool  (*isDualWieldOnly_t)(void*);
typedef bool  (*isDualWieldPrimaryOnly_t)(void*);
typedef void  (*pickupAsDual_t)(void*);
typedef void  (*setPickupAsDual_t)(void*, bool);
typedef int   (*getZoomLevel_t)(void*);
typedef void  (*setZoomLevel_t)(void*, int);
typedef void  (*applyMaxZoomScale_t)(void*);
typedef int   (*getDamage_w_t)(void*);
typedef float (*getZoomScale_t)(void*);
typedef bool  (*isUnlockable_t)(void*, void*, unsigned int);
typedef bool  (*isUpgradable_t)(void*, void*, unsigned int);
typedef int   (*getDualWieldUnlockLevel_t)(void*, void*);
typedef void  (*soldierLocalUpdateStep_t)(void*, float, cpVect, cpVect, float);
typedef void  (*addBullet_t)(void*, cpVect, float, cpVect, void*, int, cpVect, void*);
typedef void  (*addShell_t)(void*, cpVect, float, cpVect, void*, bool, void*);
typedef void  (*addRocket_t)(void*, cpVect, float, cpVect, void*, bool, void*);
typedef void  (*addGrenade_t)(void*, cpVect, float, cpVect, bool, void*, int);
typedef void  (*addSaw_t)(void*, cpVect, float, cpVect, void*, bool, void*);
typedef void  (*addFlame_t)(void*, cpVect, float, cpVect, void*, int, cpVect, void*);
typedef void  (*addExplosionAt_t)(void*, cpVect, float, void*, int, bool);
typedef void  (*addGasCloudAt_t)(void*, cpVect, float, void*, int);
typedef void  (*switchToDual_t)(void*);
typedef float (*getMaxPower_t)(void*);
typedef float (*getGravityFactor_t)(void*);
typedef void  (*setPowerF_t)(void*, float);
typedef void  (*getVector_t)(cpVect*, void*);
typedef bool  (*isCollisionTile_t)(void*, cpVect);
typedef bool  (*mapCollision_t)(void*, cpVect);
typedef bool  (*isBoundryTile_t)(void*, cpVect);
typedef void  (*setThrust_t)(void*, bool);
typedef int   (*getRespawnTime_t)(void*);
typedef int   (*isRespawning_t)(void*);
typedef void  (*addStaticShape_t)(void*, int, int);
typedef void  (*addStaticPoly_t)(void*, void*);

MgrUpdateRemote_t      old_MgrUpdateRemote      = nullptr;
MgrUpdateStep_t        old_MgrUpdateStep        = nullptr;
MgrSpawnPlayer_t       old_MgrSpawnPlayer       = nullptr;
MgrRespawnPlayer_t     old_MgrRespawnPlayer     = nullptr;
RemoteUpdateStep_t     old_RemoteUpdateStep     = nullptr;
AIUpdateStep_t         old_AIUpdateStep         = nullptr;
EnemyMgrUpdateStep_t   old_EnemyMgrUpdateStep   = nullptr;
StageUpdate_t          old_StageUpdate          = nullptr;
PhysicsMgrUpdate_t     old_physicsUpdate        = nullptr;
std::atomic<bool>      g_physHookOk{false};
LocalActivate_t        old_LocalActivate        = nullptr;
soldierAddDamage_t     old_addDamage            = nullptr;
soldierAddDamage_t     old_localAddDamage       = nullptr;
droneAddDamage_t       old_humanoidAddDamage    = nullptr;
droneAddDamage_t       old_hawkAddDamage        = nullptr;
droneAddDamage_t       old_wormAddDamage        = nullptr;
droneUpdateStep_t      old_humanoidUpdateStep   = nullptr;
droneUpdateStep_t      old_hawkUpdateStep       = nullptr;
droneUpdateStep_t      old_wormUpdateStep       = nullptr;
setHP_t                old_setHP                = nullptr;
setAlive_t             old_setAlive             = nullptr;
updatePeerDamage_t     old_updatePeerDamage     = nullptr;
setPlayerHealth_t      old_setPlayerHealth      = nullptr;
addBullet_t            old_addBullet            = nullptr;
addExplosionAt_t       old_addExplosionAt       = nullptr;
getRandomFiringAngle_t old_getRandomFiringAngle = nullptr;
getVector_t            old_Joypad_getDirVector  = nullptr;
float (*old_Joypad_getDirAngle)(void*)          = nullptr;
getRange_t             old_getRange             = nullptr;
getBulletSpeed_t       old_getBulletSpeed       = nullptr;
getRoundsPerFire_t     old_getRoundsPerFire     = nullptr;
getAmmo_t              old_getAmmo              = nullptr;
setAmmo_t              old_setAmmo              = nullptr;
subAmmo_t              old_subAmmo              = nullptr;
getClip_t              old_getClip              = nullptr;
setClip_t              old_setClip              = nullptr;
getClipCap_t           old_getClipCapacity      = nullptr;
getAmmoCap_t           old_getAmmoCapacity      = nullptr;
getReloadTime_t        old_getReloadTime        = nullptr;
isDualWield_t            old_isDualWield            = nullptr;
isDualWieldOnly_t        old_isDualWieldOnly        = nullptr;
isDualWieldPrimaryOnly_t old_isDualWieldPrimaryOnly = nullptr;
pickupAsDual_t           old_pickupAsDual           = nullptr;
setPickupAsDual_t        old_setPickupAsDual        = nullptr;
getZoomLevel_t         old_getZoomLevel         = nullptr;
setZoomLevel_t         old_setZoomLevel         = nullptr;
applyMaxZoomScale_t    old_applyMaxZoomScale    = nullptr;
getDamage_w_t          old_getDamage_w          = nullptr;
getZoomScale_t         old_getZoomScale         = nullptr;
isUnlockable_t              old_isUnlockable              = nullptr;
isUpgradable_t              old_isUpgradable              = nullptr;
getDualWieldUnlockLevel_t   old_getDualWieldUnlockLevel   = nullptr;
soldierLocalUpdateStep_t    old_soldierLocalUpdateStep    = nullptr;
getMaxPower_t               old_getMaxPower               = nullptr;
getGravityFactor_t          old_getGravityFactor          = nullptr;
isCollisionTile_t           old_isCollisionTile           = nullptr;
mapCollision_t              old_mapCollision              = nullptr;
isBoundryTile_t             old_isBoundryTile             = nullptr;
setThrust_t                 old_setThrust                 = nullptr;
getRespawnTime_t            old_getRespawnTime            = nullptr;
isRespawning_t              old_isRespawning              = nullptr;
getBodyPosition_t           old_getBodyPosition_hook      = nullptr;
getBodyPosition_t           old_collGetBody               = nullptr;
addStaticShape_t            old_addStaticBodyShape        = nullptr;
addStaticPoly_t             old_addStaticBodyPoly         = nullptr;
isBoundryTile_t             old_isBoundryTile_Hook        = nullptr;

getLocalController_t   fn_getLocalController = nullptr;
getBodyPosition_t      fn_getBodyPosition    = nullptr;
getHP_t                fn_getHP              = nullptr;
getTeamId_t            fn_getTeamId          = nullptr;
getSoldierView_t       fn_getSoldierView     = nullptr;
getPlayerName_t        fn_getPlayerName      = nullptr;
isDead_t               fn_isDead             = nullptr;
convertToWorldSpaceAR_t fn_convertToWorldSpaceAR = nullptr;
directorShared_t       fn_directorShared     = nullptr;
directorGetSize_t      fn_directorGetVisible = nullptr;
getWeapon_t            fn_getPrimaryWeapon   = nullptr;
getWeapon_t            fn_getSecondaryWeapon = nullptr;
getWeapon_t            fn_getDualWeapon      = nullptr;
getWeapon_t            fn_getSideWeapon      = nullptr;
soldierFire_t          fn_soldierFire        = nullptr;
getBulletSpeed_t       fn_getBulletSpeed     = nullptr;
getRange_t             fn_getRange           = nullptr;
setFireAngle_wpn_t     fn_setFireAngleWpn    = nullptr;
addShell_t             fn_addShell           = nullptr;
addRocket_t            fn_addRocket          = nullptr;
addGrenade_t           fn_addGrenade         = nullptr;
addSaw_t               fn_addSaw             = nullptr;
addFlame_t             fn_addFlame           = nullptr;
addGasCloudAt_t        fn_addGasCloudAt      = nullptr;
setPowerF_t            fn_setPowerF          = nullptr;
switchToDual_t         fn_switchPrimaryToDual   = nullptr;
switchToDual_t         fn_switchSecondaryToDual = nullptr;
setThrust_t            fn_setThrust          = nullptr;

// ==================================================================
// HP table
// ==================================================================
static constexpr int HP_TABLE_SIZE = 1024;
struct ViewHPEntry { std::atomic<void*> view{nullptr}; std::atomic<int> hp{-1}; };
static ViewHPEntry g_viewHPTable[HP_TABLE_SIZE];
static inline void viewHPStore(void* view, int hp) {
    if (!view) return;
    uintptr_t v = (uintptr_t)view;
    int base = (int)((v >> 4) % HP_TABLE_SIZE);
    for (int i = 0; i < 32; i++) {
        int idx = (base + i) % HP_TABLE_SIZE;
        void* cur = g_viewHPTable[idx].view.load(std::memory_order_acquire);
        if (cur == view) { g_viewHPTable[idx].hp.store(hp); return; }
        if (cur == nullptr) {
            void* expected = nullptr;
            if (g_viewHPTable[idx].view.compare_exchange_strong(expected, view,
                    std::memory_order_acq_rel, std::memory_order_acquire)) {
                g_viewHPTable[idx].hp.store(hp); return;
            }
            if (expected == view) { g_viewHPTable[idx].hp.store(hp); return; }
        }
    }
}
static inline int viewHPLoad(void* view) {
    if (!view) return -1;
    uintptr_t v = (uintptr_t)view;
    int base = (int)((v >> 4) % HP_TABLE_SIZE);
    for (int i = 0; i < 32; i++) {
        int idx = (base + i) % HP_TABLE_SIZE;
        void* cur = g_viewHPTable[idx].view.load(std::memory_order_acquire);
        if (cur == view) return g_viewHPTable[idx].hp.load(std::memory_order_acquire);
        if (cur == nullptr) return -1;
    }
    return -1;
}

// ==================================================================
// ESP data
// ==================================================================
struct ESPSoldier {
    void*       instance;
    bool        isLocal;
    struct { float x, y, z; } position;
    bool        hasValidPos;
    float       screenX, screenY;
    bool        hasScreen;
    int         hp, maxHP;
    bool        alive;
    int         teamId;
    std::string name;
    bool        isAimTarget;
    int         weaponCount;
};
struct SoldierEntry {
    void*       instance;
    uint64_t    lastSeenTick;
    int         lastKnownHP;
    int         observedMaxHP;
    uint64_t    lastDamageMs;
    bool        isDead;
    int         teamId;
    float       worldX, worldY;
    bool        worldValid;
    float       screenX, screenY;
    bool        screenValid;
    bool        nameResolved, nameLookupFailed;
    std::string cachedName;
    float       prevWorldX, prevWorldY;
    float       velX, velY;
    uint64_t    lastVelSampleMs;
    bool        hasPrevPos;
    int         weaponCount;
};
static std::unordered_map<void*, SoldierEntry> g_soldierMap;
std::vector<ESPSoldier> g_soldierSnapshots;
std::mutex              g_soldierMutex;

// ==================================================================
// Feature flags
// ==================================================================
std::atomic<bool> g_espEnabled {false};
std::atomic<bool> g_espBox     {true};
std::atomic<bool> g_espLine    {true};
std::atomic<bool> g_espHealth  {true};
std::atomic<bool> g_espDistance{false};
std::atomic<bool> g_espEnemyOnly{false};
std::atomic<int>  g_espBoxWidth{3};
std::atomic<int>  g_boxSizeMul{115};
std::atomic<bool> g_silentAim   {false};
std::atomic<bool> g_autoFire    {false};
std::atomic<bool> g_aimMagnet   {false};
std::atomic<bool> g_drawFovCircle{false};
std::atomic<bool> g_rangeBoost  {true};
std::atomic<int>  g_fovPixels   {300};
std::atomic<int>  g_weaponSpeedMul{3};

std::atomic<bool> g_wpnUnlimitedAmmo  {false};
std::atomic<bool> g_wpnMultiShot      {false};
std::atomic<int>  g_wpnBulletsPerFire {10};
std::atomic<bool> g_wpnFastReload     {false};
std::atomic<bool> g_wpnMaxRange       {false};
std::atomic<bool> g_wpnBulletSpeedUp  {false};
std::atomic<int>  g_wpnBulletSpeedMul {5};
std::atomic<bool> g_wpnHighDamage     {false};
std::atomic<int>  g_wpnDamageMul      {5};
std::atomic<bool> g_wpnNoRecoil       {false};
std::atomic<bool> g_wpnZoomSelect     {false};
std::atomic<int>  g_wpnZoomLevel      {5};
std::atomic<bool> g_charSpeedOn       {false};
std::atomic<int>  g_charSpeedMul      {2};
std::atomic<bool> g_wpnUnlockAll      {false};
std::atomic<bool> g_wpnMaxUpgrade     {false};
std::atomic<bool> g_wpnDualWieldUnlock{false};

std::atomic<bool> g_dualWieldAll      {false};
std::atomic<bool> g_unlimitedFlyPower {false};
std::atomic<bool> g_flyThroughWalls   {false};

std::atomic<bool>  g_teleportActive{false};
std::atomic<float> g_teleportX{0.f};
std::atomic<float> g_teleportY{0.f};
std::atomic<bool>  g_teleportFollowAim{false};
static std::atomic<float> g_lastSafeX{0.f};
static std::atomic<float> g_lastSafeY{0.f};
static std::atomic<bool>  g_lastSafeValid{false};
std::atomic<bool>  g_tpPadEnabled{false};   // feature toggle
std::atomic<bool> g_lagAntiLagMode    {false};
std::atomic<int>  g_lagEspUpdateHz    {60};
std::atomic<bool> g_lagSkipExtraDraw  {false};
std::atomic<bool> g_lagThrottleAim    {false};
static uint64_t   g_lagLastEspUpdateMs = 0;

static constexpr float MAX_AIM_RANGE = 99999.f;
static void*     g_stickyTarget        = nullptr;
static uint64_t  g_stickyTargetLastMs  = 0;
static constexpr uint64_t STICKY_HOLD_MS = 350;
static uint64_t g_lastFireMs = 0;
static constexpr uint64_t MIN_FIRE_INTERVAL_MS = 55;
static std::atomic<uint64_t> g_lastForceRespawnMs{0};

std::atomic<bool>  g_hasAimTarget{false};
std::atomic<float> g_aimTargetRawX{0.f};
std::atomic<float> g_aimTargetRawY{0.f};
std::atomic<bool>  g_hasAimAngle{false};
std::atomic<float> g_aimAngle{0.f};
std::atomic<void*> g_currentAimTarget{nullptr};
std::atomic<int>   g_localTeam{-1};
std::atomic<void*> g_localInstance{nullptr};
std::atomic<void*> g_lastLocalInstance{nullptr};
std::atomic<bool>  g_localSeen{false};
std::atomic<bool>  g_localDead{false};
std::atomic<uint64_t> g_localInstanceSetMs{0};
std::atomic<uint64_t> g_lastLocalAliveMs{0};
std::atomic<float> g_localWorldX{0.f};
std::atomic<float> g_localWorldY{0.f};
std::atomic<float> g_designW{1280.f};
std::atomic<float> g_designH{720.f};
std::atomic<bool>  g_designValid{false};
std::atomic<bool>  g_aimResolved{false};

std::atomic<bool> g_wpnHooksOk{false};
std::atomic<bool> g_wpnUnlockHooksOk{false};
std::atomic<bool> g_mgrHooksOk{false};
std::atomic<bool> g_droneHooksOk{false};
std::atomic<bool> g_flyHooksOk{false};
std::atomic<bool> g_bombGasHooksOk{false};
std::atomic<bool> g_wallHooksOk{false};
std::atomic<bool> g_teleportHooksOk{false};

static __thread volatile sig_atomic_t tls_bulletRaycast = 0;

static bool ApplyTeleportPosition();
static std::atomic<bool> g_teleportJustFinished{false};
static bool SafeGetActualPosition(void* s, cpVect& out);

static std::atomic<float> g_smoothTP_TargetX{0.f};
static std::atomic<float> g_smoothTP_TargetY{0.f};
static std::atomic<bool>  g_smoothTP_Active{false};
static std::atomic<int>   g_smoothTP_Frames{0};
static constexpr float    SMOOTH_TP_STEP   = 180.f;   // world units per frame
static constexpr int      SMOOTH_TP_MAX_F  = 60;      // max 60 frames (~1 sec)

// Forward declarations for teleport map bounds functions
static void DetectMapBounds();
static void InvalidateMapBounds();

// MapManager instance — প্রথম hook call থেকে capture হবে
static std::atomic<void*> g_mapManagerInstance{nullptr};
static std::atomic<bool>  g_mapBoundsDetected{false};
// Map bounds (world coords)



static std::atomic<uintptr_t> g_bodyOffsetFromSelf{(uintptr_t)-1};
static std::atomic<int>       g_posOffsetInBody{-1};
static std::atomic<bool>      g_bodyDiscoveryDone{false};
static std::atomic<bool>      g_discoveryInProgress{false};
static std::atomic<int>       g_gbpCallLogs{0};


static inline bool PlausiblePtr(const void* p) {
    uintptr_t v = (uintptr_t)p;
    return v >= 0x10000UL && v < 0xFFFFF000UL;
}
static inline uint64_t NowMs() {
    using namespace std::chrono;
    return duration_cast<milliseconds>(steady_clock::now().time_since_epoch()).count();
}
static inline bool IsModActive() {
    return g_espEnabled.load() || g_silentAim.load() || g_autoFire.load()
        || g_aimMagnet.load() || g_drawFovCircle.load() || g_teleportActive.load();
}
static inline float NormalizeDeg(float d) {
    while (d > 180.0f) d -= 360.0f;
    while (d < -180.0f) d += 360.0f;
    return d;
}
static inline bool IsAddressMapped(uintptr_t addr) {
    uintptr_t pageStart = addr & ~(uintptr_t)0xFFF;
    unsigned char vec = 0;
    return mincore((void*)pageStart, 0x1000, &vec) == 0;
}

// ==================================================================
// Safe wrappers
// ==================================================================
static bool SafeGetPosition(void* s, cpVect& out) {
    if (!PlausiblePtr(s) || !fn_getBodyPosition) return false;
    out.x = out.y = 0;
    if (GUARD_ENTER()) { GUARD_SET(); fn_getBodyPosition(&out, s); GUARD_CLR(); }
    else { GUARD_CLR(); return false; }
    if (std::isnan(out.x) || std::isnan(out.y) || std::isinf(out.x) || std::isinf(out.y)) return false;
    if (std::fabs(out.x) < 5.0 && std::fabs(out.y) < 5.0) return false;
    if (std::fabs(out.x) > 20000.0 || std::fabs(out.y) > 20000.0) return false;
    return true;
}
static bool SafeIsDeadStrict(void* s) {
    if (!PlausiblePtr(s) || !fn_isDead) return false;
    int r;
    if (GUARD_ENTER()) { GUARD_SET(); r = fn_isDead(s); GUARD_CLR(); }
    else { GUARD_CLR(); return false; }
    return (r == 1);
}
static int SafeGetHP_NoHook(void* s) {
    if (!PlausiblePtr(s) || !fn_getHP) return -1;
    int hp;
    if (GUARD_ENTER()) { GUARD_SET(); hp = fn_getHP(s); GUARD_CLR(); }
    else { GUARD_CLR(); return -1; }
    if (hp < -100 || hp > 100000) return -1;
    return hp;
}
static int SafeGetTeamId(void* s) {
    if (!PlausiblePtr(s) || !fn_getTeamId) return 0;
    int t;
    if (GUARD_ENTER()) { GUARD_SET(); t = fn_getTeamId(s); GUARD_CLR(); }
    else { GUARD_CLR(); return 0; }
    if (t < 0 || t > 32) return 0;
    return t;
}
static void ClearAllState() {
    std::lock_guard<std::mutex> l(g_soldierMutex);
    g_soldierMap.clear(); g_soldierSnapshots.clear();
    g_hasAimTarget.store(false); g_hasAimAngle.store(false);
    g_stickyTarget = nullptr; g_stickyTargetLastMs = 0;
    g_currentAimTarget.store(nullptr);
    g_smoothTP_Active.store(false);
    g_smoothTP_Frames.store(0);
    g_teleportActive.store(false);
    g_teleportJustFinished.store(false);
}
static void RefreshDesignSize() {
    if (!fn_directorShared || !fn_directorGetVisible) return;
    void* d = fn_directorShared();
    if (!PlausiblePtr(d)) return;
    MSSize vs = fn_directorGetVisible(d);
    if (std::isfinite(vs.w) && std::isfinite(vs.h) && vs.w > 10.f && vs.h > 10.f) {
        g_designW.store(vs.w); g_designH.store(vs.h); g_designValid.store(true);
    }
}
static bool QueryScreenPosition(void* soldier, float& outX, float& outY) {
    outX = outY = 0.f;
    if (!PlausiblePtr(soldier) || !fn_getSoldierView || !fn_convertToWorldSpaceAR) return false;
    void* view;
    if (GUARD_ENTER()) { GUARD_SET(); view = fn_getSoldierView(soldier); GUARD_CLR(); }
    else { GUARD_CLR(); return false; }
    if (!PlausiblePtr(view)) return false;
    MPoint origin{0.f, 0.f}, gl;
    if (GUARD_ENTER()) { GUARD_SET(); gl = fn_convertToWorldSpaceAR(view, &origin); GUARD_CLR(); }
    else { GUARD_CLR(); return false; }
    if (!std::isfinite(gl.x) || !std::isfinite(gl.y)) return false;
    if (std::fabs(gl.x) > 100000.f || std::fabs(gl.y) > 100000.f) return false;
    outX = gl.x; outY = gl.y; return true;
}
static void SanitizeName(std::string& s) {
    if (s.empty()) return;
    if (s.size() > 32) s.resize(32);
    std::string out; out.reserve(s.size());
    for (unsigned char c : s) {
        if (c == '\n' || c == '\r' || c == '\t') { out += ' '; continue; }
        if (c < 0x20) continue;
        out += (char)c;
    }
    s = std::move(out);
}
static bool SafeGetName(void* soldier, std::string& outName) {
    if (!PlausiblePtr(soldier) || !fn_getSoldierView || !fn_getPlayerName) return false;
    void* view;
    if (GUARD_ENTER()) { GUARD_SET(); view = fn_getSoldierView(soldier); GUARD_CLR(); }
    else { GUARD_CLR(); return false; }
    if (!PlausiblePtr(view)) return false;
    outName.clear();
    if (GUARD_ENTER()) { GUARD_SET(); fn_getPlayerName(&outName, view); GUARD_CLR(); }
    else { GUARD_CLR(); return false; }
    if (outName.empty()) return false;
    SanitizeName(outName);
    return !outName.empty();
}
static SoldierEntry& EnsureEntryLocked(void* s) {
    auto it = g_soldierMap.find(s);
    if (it != g_soldierMap.end()) return it->second;
    SoldierEntry e{};
    e.instance = s; e.lastSeenTick = NowMs();
    e.lastKnownHP = -1; e.observedMaxHP = 100;
    auto res = g_soldierMap.emplace(s, std::move(e));
    return res.first->second;
}
static int SafeCountWeapons(void* soldier) {
    int count = 0;
    if (!PlausiblePtr(soldier)) return 0;
    if (fn_getPrimaryWeapon) {
        void* w = nullptr;
        if (GUARD_ENTER()) { GUARD_SET(); w = fn_getPrimaryWeapon(soldier); GUARD_CLR(); } else GUARD_CLR();
        if (PlausiblePtr(w)) count++;
    }
    if (fn_getSecondaryWeapon) {
        void* w = nullptr;
        if (GUARD_ENTER()) { GUARD_SET(); w = fn_getSecondaryWeapon(soldier); GUARD_CLR(); } else GUARD_CLR();
        if (PlausiblePtr(w)) count++;
    }
    if (fn_getDualWeapon) {
        void* w = nullptr;
        if (GUARD_ENTER()) { GUARD_SET(); w = fn_getDualWeapon(soldier); GUARD_CLR(); } else GUARD_CLR();
        if (PlausiblePtr(w)) count++;
    }
    if (fn_getSideWeapon) {
        void* w = nullptr;
        if (GUARD_ENTER()) { GUARD_SET(); w = fn_getSideWeapon(soldier); GUARD_CLR(); } else GUARD_CLR();
        if (PlausiblePtr(w)) count++;
    }
    return count;
}
static void RefreshSoldierData(void* s) {
    if (!PlausiblePtr(s)) return;
    uint64_t now = NowMs();
    int  hp     = SafeGetHP_NoHook(s);
    bool isDead = SafeIsDeadStrict(s);
    int  teamId = SafeGetTeamId(s);
    cpVect p; bool hasPos = SafeGetPosition(s, p);
    float sx = 0, sy = 0; bool hasScreen = QueryScreenPosition(s, sx, sy);
    int wCount = SafeCountWeapons(s);
    void* view = nullptr;
    if (fn_getSoldierView) {
        if (GUARD_ENTER()) { GUARD_SET(); view = fn_getSoldierView(s); GUARD_CLR(); }
        else GUARD_CLR();
    }
    int vhp = (view && PlausiblePtr(view)) ? viewHPLoad(view) : -1;
    std::lock_guard<std::mutex> lock(g_soldierMutex);
    SoldierEntry& e = EnsureEntryLocked(s);
    e.lastSeenTick = now;
    if (vhp >= 0) { e.lastKnownHP = vhp; if (vhp > e.observedMaxHP) e.observedMaxHP = vhp; }
    else if (hp >= 0) {
        if (e.lastKnownHP < 0 || hp < e.lastKnownHP) e.lastKnownHP = hp;
        if (hp > e.observedMaxHP) e.observedMaxHP = hp;
    }
    e.isDead = isDead; e.teamId = teamId; e.weaponCount = wCount;
    if (hasPos) {
        float nx = (float)p.x, ny = (float)p.y;
        if (e.hasPrevPos && e.lastVelSampleMs > 0) {
            uint64_t dtMs = now - e.lastVelSampleMs;
            if (dtMs >= 8 && dtMs <= 400) {
                float dtSec = (float)dtMs * 0.001f;
                float dx = nx - e.prevWorldX, dy = ny - e.prevWorldY;
                if (dx*dx + dy*dy < 2000.f * 2000.f) {
                    float vx = dx / dtSec, vy = dy / dtSec;
                    const float a = 0.65f;
                    if (std::isfinite(vx) && std::isfinite(vy)) {
                        e.velX = e.velX * (1.f - a) + vx * a;
                        e.velY = e.velY * (1.f - a) + vy * a;
                    }
                } else { e.velX = 0.f; e.velY = 0.f; }
            }
        }
        e.prevWorldX = nx; e.prevWorldY = ny;
        e.lastVelSampleMs = now; e.hasPrevPos = true;
        e.worldX = nx; e.worldY = ny; e.worldValid = true;
        if (s == g_localInstance.load()) g_lastLocalAliveMs.store(now);
    } else e.worldValid = false;
    if (hasScreen) { e.screenX = sx; e.screenY = sy; e.screenValid = true; }
    else e.screenValid = false;
    if (!e.nameResolved && !e.nameLookupFailed) {
        std::string name;
        if (SafeGetName(s, name)) { e.cachedName = name; e.nameResolved = true; }
        else { e.cachedName = "?"; e.nameLookupFailed = true; }
    }
}
static void BuildSnapshots() {
    uint64_t now = NowMs();
    void* localInst = g_localInstance.load();
    void* aimTarget = g_currentAimTarget.load();
    std::vector<ESPSoldier> newSnaps;
    newSnaps.reserve(g_soldierMap.size());
    {
        std::lock_guard<std::mutex> lock(g_soldierMutex);
        if (localInst != nullptr) {
            auto lit = g_soldierMap.find(localInst);
            if (lit != g_soldierMap.end() && lit->second.isDead) {
                if (!g_localDead.load()) {
                    g_localInstance.store(nullptr); g_localSeen.store(false);
                    g_localDead.store(true);
                    g_hasAimTarget.store(false); g_hasAimAngle.store(false);
                }
            } else if (g_localDead.load()) g_localDead.store(false);
        }
        for (auto it = g_soldierMap.begin(); it != g_soldierMap.end(); ) {
            SoldierEntry& e = it->second;
            if (now - e.lastSeenTick > 15000) { it = g_soldierMap.erase(it); continue; }
            bool isLocal = (it->first == localInst);
            int  hpNow = e.lastKnownHP < 0 ? 100 : e.lastKnownHP;
            if (!isLocal && (hpNow <= 0 || e.isDead)) { ++it; continue; }
            ESPSoldier snap{};
            snap.instance = it->first; snap.isLocal = isLocal;
            snap.position.x = e.worldX; snap.position.y = e.worldY;
            snap.hasValidPos = e.worldValid || e.screenValid;
            snap.screenX = e.screenX; snap.screenY = e.screenY;
            snap.hasScreen = e.screenValid;
            snap.hp = hpNow;
            snap.maxHP = e.observedMaxHP > 100 ? e.observedMaxHP : 100;
            snap.alive = (snap.hp > 0) || isLocal;
            snap.teamId = e.teamId;
            snap.name = e.nameResolved ? e.cachedName : "?";
            snap.isAimTarget = (it->first == aimTarget);
            snap.weaponCount = e.weaponCount;
            newSnaps.push_back(std::move(snap));
            if (isLocal && e.worldValid) {
                g_localWorldX.store(e.worldX); g_localWorldY.store(e.worldY);
                g_localSeen.store(true); g_localTeam.store(e.teamId);
            }
            ++it;
        }
    }
    { std::lock_guard<std::mutex> lock(g_soldierMutex); g_soldierSnapshots.swap(newSnaps); }
}
bool mapCollision_Hook(void* self, cpVect pos) {
    if (g_mapManagerInstance.load() == nullptr) g_mapManagerInstance.store(self);
    if (g_flyThroughWalls.load()) return false;
    if (g_tpPadEnabled.load()) return false;
    return old_mapCollision ? old_mapCollision(self, pos) : false;
}
bool isCollisionTile_Hook(void* self, cpVect pos) {
    if (g_mapManagerInstance.load() == nullptr) g_mapManagerInstance.store(self);
    if (g_flyThroughWalls.load()) return false;
    if (g_tpPadEnabled.load()) return false;
    return old_isCollisionTile ? old_isCollisionTile(self, pos) : false;
}
bool isBoundryTile_Hook(void* self, cpVect pos) {
    if (g_mapManagerInstance.load() == nullptr) g_mapManagerInstance.store(self);
    if (g_flyThroughWalls.load()) return false;
    if (g_tpPadEnabled.load()) return false;   // ★ শুধু Teleport
    return old_isBoundryTile ? old_isBoundryTile(self, pos) : false;
}
void addStaticBodyShape_Hook(void* self, int a, int b) {
    if (g_flyThroughWalls.load()) return;
    if (old_addStaticBodyShape) old_addStaticBodyShape(self, a, b);
}
void addStaticBodyPoly_Hook(void* self, void* obj) {
    if (g_flyThroughWalls.load()) return;
    if (old_addStaticBodyPoly) old_addStaticBodyPoly(self, obj);
}
float getMaxPower_Hook(void* self) {
    if (g_unlimitedFlyPower.load()) return 100.0f;
    return old_getMaxPower ? old_getMaxPower(self) : 100.0f;
}
void setThrust_Hook(void* self, bool value) {
    if (old_setThrust) old_setThrust(self, value);
}


void addExplosionAt_Hook(void* self, cpVect pos, float radius, void* str, int teamId, bool flag) {
    if (old_addExplosionAt) old_addExplosionAt(self, pos, radius, str, teamId, flag);
}
int getRoundsPerFire_Hook(void* self) {
    if (g_wpnMultiShot.load()) {
        int n = g_wpnBulletsPerFire.load();
        if (n < 1) n = 1; if (n > 30) n = 30;
        return n;
    }
    return old_getRoundsPerFire ? old_getRoundsPerFire(self) : 1;
}
int getAmmo_Hook(void* self) {
    if (g_wpnUnlimitedAmmo.load()) return 9999;
    return old_getAmmo ? old_getAmmo(self) : 0;
}
void setAmmo_Hook(void* self, int amount) {
    if (g_wpnUnlimitedAmmo.load()) { if (old_setAmmo) old_setAmmo(self, 9999); return; }
    if (old_setAmmo) old_setAmmo(self, amount);
}
void subAmmo_Hook(void* self, int amount) { if (g_wpnUnlimitedAmmo.load()) return; if (old_subAmmo) old_subAmmo(self, amount); }
int getClip_Hook(void* self) { if (g_wpnUnlimitedAmmo.load()) return 9999; return old_getClip ? old_getClip(self) : 0; }
void setClip_Hook(void* self, int amount) {
    if (g_wpnUnlimitedAmmo.load()) { if (old_setClip) old_setClip(self, 9999); return; }
    if (old_setClip) old_setClip(self, amount);
}
int getClipCapacity_Hook(void* self) { if (g_wpnUnlimitedAmmo.load()) return 9999; return old_getClipCapacity ? old_getClipCapacity(self) : 0; }
int getAmmoCapacity_Hook(void* self) { if (g_wpnUnlimitedAmmo.load()) return 9999; return old_getAmmoCapacity ? old_getAmmoCapacity(self) : 0; }
int getReloadTime_Hook(void* self) { if (g_wpnFastReload.load()) return 0; return old_getReloadTime ? old_getReloadTime(self) : 1000; }

bool isDualWield_Hook(void* self) {
    if (g_dualWieldAll.load()) {
        void* local = g_localInstance.load();
        if (PlausiblePtr(local) && fn_getPrimaryWeapon) {
            void* prim = nullptr;
            if (GUARD_ENTER()) { GUARD_SET(); prim = fn_getPrimaryWeapon(local); GUARD_CLR(); }
            else GUARD_CLR();
            if (prim == self) return true;
        }
    }
    return old_isDualWield ? old_isDualWield(self) : false;
}
bool isDualWieldOnly_Hook(void* self) {
    return old_isDualWieldOnly ? old_isDualWieldOnly(self) : false;
}
bool isDualWieldPrimaryOnly_Hook(void* self) {
    if (g_dualWieldAll.load()) return false;
    return old_isDualWieldPrimaryOnly ? old_isDualWieldPrimaryOnly(self) : false;
}
void setPickupAsDual_Hook(void* self, bool v) {
    if (g_dualWieldAll.load()) {
        if (old_setPickupAsDual) old_setPickupAsDual(self, true);
        return;
    }
    if (old_setPickupAsDual) old_setPickupAsDual(self, v);
}
void pickupAsDual_Hook(void* self) {
    if (old_pickupAsDual) old_pickupAsDual(self);
}

int getZoomLevel_Hook(void* self) { if (g_wpnMaxZoom.load()) return 5; return old_getZoomLevel ? old_getZoomLevel(self) : 0; }
void applyMaxZoomScale_Hook(void* self) {
    if (old_applyMaxZoomScale) old_applyMaxZoomScale(self);
    if (g_wpnMaxZoom.load() && old_setZoomLevel) for (int i = 0; i < 5; i++) old_setZoomLevel(self, i);
}
int getDamage_w_Hook(void* self) {
    int orig = old_getDamage_w ? old_getDamage_w(self) : 20;
    if (!g_wpnHighDamage.load()) return orig;
    int mul = g_wpnDamageMul.load();
    if (mul < 1) mul = 1; if (mul > 20) mul = 20;
    if (orig <= 0 || orig > 100000) return orig;
    long long boosted = (long long)orig * (long long)mul;
    if (boosted > 500000) boosted = 500000;
    return (int)boosted;
}
float getZoomScale_Hook(void* self) {
    if (!g_wpnZoomSelect.load()) return old_getZoomScale ? old_getZoomScale(self) : 1.0f;
    int lvl = g_wpnZoomLevel.load();
    if (lvl < 1) lvl = 1; if (lvl > 11) lvl = 11;
    return 1.0f / (float)lvl;
}
bool isUnlockable_Hook(void* self, void* id, unsigned int lvl) {
    if (g_wpnUnlockAll.load()) return true;
    return old_isUnlockable ? old_isUnlockable(self, id, lvl) : false;
}
bool isUpgradable_Hook(void* self, void* id, unsigned int lvl) {
    if (g_wpnMaxUpgrade.load()) return true;
    return old_isUpgradable ? old_isUpgradable(self, id, lvl) : false;
}
int getDualWieldUnlockLevel_Hook(void* self, void* id) {
    if (g_wpnDualWieldUnlock.load() || g_dualWieldAll.load()) return 0;
    return old_getDualWieldUnlockLevel ? old_getDualWieldUnlockLevel(self, id) : 8;
}


void MgrRespawnPlayer_Hook(void* self) {
    if (old_MgrRespawnPlayer) old_MgrRespawnPlayer(self);
}
int getRespawnTime_Hook(void* self) {
    return old_getRespawnTime ? old_getRespawnTime(self) : 5;
}
int isRespawning_Hook(void* self) {
    return old_isRespawning ? old_isRespawning(self) : 0;
}


void soldierLocalUpdateStep_Hook(void* self, float dt, cpVect a, cpVect b, float c) {
    if (!PlausiblePtr(self)) {
        if (old_soldierLocalUpdateStep) old_soldierLocalUpdateStep(self, dt, a, b, c);
        return;
    }

    // ★ Character speed boost (আগের মতোই)
    if (g_charSpeedOn.load()) {
        int mul = g_charSpeedMul.load();
        if (mul < 1) mul = 1; if (mul > 20) mul = 20;
        float f = (float)mul;
        a.x *= (double)f; a.y *= (double)f;
        b.x *= (double)f; b.y *= (double)f;
    }

    // ★ গেমের নিজের update logic আগে চলুক
    if (old_soldierLocalUpdateStep) old_soldierLocalUpdateStep(self, dt, a, b, c);

    // ★ এরপর unlimited power
    if (g_unlimitedFlyPower.load() && fn_setPowerF) {
        if (GUARD_ENTER()) { GUARD_SET(); fn_setPowerF(self, 9999.0f); GUARD_CLR(); }
        else GUARD_CLR();
    }
}

// ==================================================================
// Aim
// ==================================================================
static void ComputeAimTarget(void* localController) {
    if (!g_aimResolved.load()) return;
    if (!PlausiblePtr(localController)) return;
    if (g_localDead.load()) {
        g_hasAimTarget.store(false); g_hasAimAngle.store(false);
        g_currentAimTarget.store(nullptr);
        return;
    }
    bool needTarget = g_silentAim.load() || g_autoFire.load() || g_aimMagnet.load() || g_teleportFollowAim.load();
    if (!needTarget) {
        g_hasAimTarget.store(false); g_hasAimAngle.store(false);
        g_currentAimTarget.store(nullptr);
        return;
    }
    if (g_lagThrottleAim.load()) {
        static __thread uint32_t frame = 0;
        if ((++frame % 3) != 0) return;
    }
    uint64_t now = NowMs();
    uint64_t setMs = g_localInstanceSetMs.load();
    uint64_t aliveMs = g_lastLocalAliveMs.load();
    bool instanceFresh = (setMs > 0 && (now - setMs) < 800);
    bool aliveRecent   = (aliveMs > 0 && (now - aliveMs) < 1500);
    if (!aliveRecent && !instanceFresh) {
        g_hasAimTarget.store(false); g_hasAimAngle.store(false);
        return;
    }
    cpVect lp;
    if (!SafeGetPosition(localController, lp)) {
        g_hasAimTarget.store(false); g_hasAimAngle.store(false);
        return;
    }
    float lx = (float)lp.x, ly = (float)lp.y;
    int localTeam = g_localTeam.load();
    bool fovGateActive = g_drawFovCircle.load();
    float designW = g_designW.load(), designH = g_designH.load();
    if (!std::isfinite(designW) || designW < 10.f) designW = 1280.f;
    if (!std::isfinite(designH) || designH < 10.f) designH = 720.f;
    float designCX = designW * 0.5f, designCY = designH * 0.5f;
    int fovDesign = g_fovPixels.load();
    if (fovDesign > 350) fovDesign = 350;
    if (fovDesign < 60)  fovDesign = 60;
    float fovRadiusSq = (float)fovDesign * (float)fovDesign;
    void* bestTarget = nullptr;
    float bestDistSq = MAX_AIM_RANGE * MAX_AIM_RANGE;
    float bestAngleRaw = 0.f, bestRawX = 0.f, bestRawY = 0.f;
    {
        std::lock_guard<std::mutex> lock(g_soldierMutex);
        for (auto& kv : g_soldierMap) {
            void* target = kv.first;
            SoldierEntry& e = kv.second;
            if (target == localController) continue;
            if (e.isDead || e.lastKnownHP <= 0) continue;
            if (!e.worldValid) continue;
            if (now - e.lastSeenTick > 1200) continue;
            if (localTeam > 0 && e.teamId == localTeam) continue;
            float wdx = e.worldX - lx, wdy = e.worldY - ly;
            float worldDistSq = wdx*wdx + wdy*wdy;
            if (worldDistSq < 0.25f) continue;
            if (fovGateActive) {
                if (!e.screenValid) continue;
                float sdx = e.screenX - designCX, sdy = e.screenY - designCY;
                if (sdx*sdx + sdy*sdy > fovRadiusSq) continue;
            }
            if (worldDistSq >= bestDistSq) continue;
            bestDistSq = worldDistSq;
            bestTarget = target;
            bestRawX = e.worldX; bestRawY = e.worldY;
            bestAngleRaw = atan2f(wdy, wdx);
        }
    }
    if (!bestTarget) {
        if (g_stickyTarget && (now - g_stickyTargetLastMs) < STICKY_HOLD_MS && g_hasAimAngle.load()) {
            g_hasAimTarget.store(true); return;
        }
        g_stickyTarget = nullptr; g_currentAimTarget.store(nullptr);
        g_hasAimTarget.store(false); g_hasAimAngle.store(false);
        return;
    }
    g_stickyTarget = bestTarget; g_stickyTargetLastMs = now;
    g_currentAimTarget.store(bestTarget);
    g_aimTargetRawX.store(bestRawX); g_aimTargetRawY.store(bestRawY);
    g_aimAngle.store(bestAngleRaw);
    g_hasAimAngle.store(true); g_hasAimTarget.store(true);
}
static void ExecuteAutoFire(void* localController) {
    if (!g_autoFire.load()) return;
    if (!g_hasAimTarget.load()) return;
    if (!PlausiblePtr(localController)) return;
    if (g_localDead.load()) return;
    uint64_t now = NowMs();
    if (now - g_lastFireMs < MIN_FIRE_INTERVAL_MS) return;
    g_lastFireMs = now;
    float angleRad = g_aimAngle.load();
    if (!std::isfinite(angleRad)) return;
    if (fn_soldierFire) {
        if (GUARD_ENTER()) { GUARD_SET(); fn_soldierFire(localController, angleRad); GUARD_CLR(); }
        else GUARD_CLR();
    }
}

// ==================================================================
// Teleport discovery — v116 WIDER SCAN + dual pass
// ==================================================================
static void TryDiscoverBodyPointer(void* self) {
    if (g_bodyDiscoveryDone.load()) return;
    if (g_discoveryInProgress.exchange(true)) return;

    struct Guard { ~Guard() { g_discoveryInProgress.store(false); } } guard;

    if (!PlausiblePtr(self)) return;
    if (!old_getBodyPosition_hook) return;

    cpVect want{0, 0};
    if (GUARD_ENTER()) { GUARD_SET(); old_getBodyPosition_hook(&want, self); GUARD_CLR(); }
    else { GUARD_CLR(); return; }

    if (!std::isfinite(want.x) || !std::isfinite(want.y)) return;
    if (std::fabs(want.x) < 30.0 && std::fabs(want.y) < 30.0) return;
    if (std::fabs(want.x) > 15000.0 || std::fabs(want.y) > 15000.0) return;

    // Throttled log
    static std::atomic<uint64_t> s_lastLog{0};
    uint64_t nowMs = NowMs();
    uint64_t prevLog = s_lastLog.load();
    if (nowMs - prevLog > 1000 && s_lastLog.compare_exchange_strong(prevLog, nowMs)) {
        traceLog("TELEPORT scan: self=%p want=(%.1f,%.1f)", self, want.x, want.y);
    }

    uintptr_t selfAddr = (uintptr_t)self;

    // PASS A: body pointer inside self
    for (int selfOff = 0; selfOff <= 512; selfOff += 4) {
        uintptr_t fieldAddr = selfAddr + selfOff;
        if (!IsAddressMapped(fieldAddr)) continue;

        void* cand = nullptr;
        if (GUARD_ENTER()) { GUARD_SET(); cand = *(void**)fieldAddr; GUARD_CLR(); }
        else { GUARD_CLR(); continue; }
        if (!PlausiblePtr(cand)) continue;

        uintptr_t cbase = (uintptr_t)cand;
        if (cbase & 0x3) continue;

        for (int posOff = 0; posOff <= 1024; posOff += 8) {
            uintptr_t dAddr = cbase + posOff;
            if (dAddr & 0x7) continue;
            if (!IsAddressMapped(dAddr)) continue;
            if (!IsAddressMapped(dAddr + 8)) continue;

            double px = 0, py = 0;
            if (GUARD_ENTER()) {
                GUARD_SET();
                px = *(double*)dAddr;
                py = *(double*)(dAddr + 8);
                GUARD_CLR();
            } else { GUARD_CLR(); continue; }

            if (!std::isfinite(px) || !std::isfinite(py)) continue;
            if (std::fabs(px - want.x) < 3.0 && std::fabs(py - want.y) < 3.0) {
                g_bodyOffsetFromSelf.store((uintptr_t)selfOff);
                g_posOffsetInBody.store(posOff);
                g_bodyDiscoveryDone.store(true);
                traceLog("TELEPORT FOUND: body self+0x%x, p +0x%x (%.2f,%.2f)",
                         selfOff, posOff, px, py);
                return;
            }
        }
    }

    // PASS B: position inline in self
    for (int off = 0; off <= 1024; off += 8) {
        uintptr_t dAddr = selfAddr + off;
        if (dAddr & 0x7) continue;
        if (!IsAddressMapped(dAddr)) continue;
        if (!IsAddressMapped(dAddr + 8)) continue;

        double px = 0, py = 0;
        if (GUARD_ENTER()) {
            GUARD_SET();
            px = *(double*)dAddr;
            py = *(double*)(dAddr + 8);
            GUARD_CLR();
        } else { GUARD_CLR(); continue; }

        if (!std::isfinite(px) || !std::isfinite(py)) continue;
        if (std::fabs(px - want.x) < 3.0 && std::fabs(py - want.y) < 3.0) {
            g_bodyOffsetFromSelf.store(0);
            g_posOffsetInBody.store(off | 0x40000000);
            g_bodyDiscoveryDone.store(true);
            traceLog("TELEPORT FOUND (inline): p self+0x%x (%.2f,%.2f)", off, px, py);
            return;
        }
    }
}


static bool ApplyTeleportPosition() {
    if (!g_teleportActive.load())       return false;
    if (!g_bodyDiscoveryDone.load())    return false;

    void* local = g_localInstance.load();
    if (!PlausiblePtr(local))           return false;
    if (g_localDead.load())             return false;

    float tx = g_teleportX.load();
    float ty = g_teleportY.load();
    if (!std::isfinite(tx) || !std::isfinite(ty)) return false;

    uintptr_t selfOff   = g_bodyOffsetFromSelf.load();
    int       posOffRaw = g_posOffsetInBody.load();
    if (selfOff == (uintptr_t)-1 || posOffRaw < 0) return false;

    // Resolve position address
    uintptr_t pAddr = 0;
    if (posOffRaw & 0x40000000) {
        pAddr = (uintptr_t)local + (posOffRaw & ~0x40000000);
    } else {
        uintptr_t fieldAddr = (uintptr_t)local + selfOff;
        if (!IsAddressMapped(fieldAddr)) return false;
        void* body = nullptr;
        if (GUARD_ENTER()) { GUARD_SET(); body = *(void**)fieldAddr; GUARD_CLR(); }
        else { GUARD_CLR(); return false; }
        if (!PlausiblePtr(body)) return false;
        pAddr = (uintptr_t)body + posOffRaw;
    }

    if ((pAddr & 0x7) != 0) return false;
    if (!IsAddressMapped(pAddr) || !IsAddressMapped(pAddr + 40)) return false;

    // Write position + zero velocity + zero bias
    if (GUARD_ENTER()) {
        GUARD_SET();
        *(double*)(pAddr)      = (double)tx;   // p.x
        *(double*)(pAddr + 8)  = (double)ty;   // p.y
        *(double*)(pAddr + 16) = 0.0;          // v.x
        *(double*)(pAddr + 24) = 0.0;          // v.y
        *(double*)(pAddr + 32) = 0.0;
        *(double*)(pAddr + 40) = 0.0;          // f.x (force) — physics solver ke clear
        GUARD_CLR();
        return true;
    }
    GUARD_CLR();
    return false;
}

// ==================================================================
// Smooth Teleport Tick — physics-এর আগে call হয়
// Collision check করে একটু একটু করে player-কে target-এর দিকে নিয়ে যায়
// ==================================================================
static void UpdateSmoothTeleport() {
    if (!g_smoothTP_Active.load()) return;

    void* local = g_localInstance.load();
    if (!PlausiblePtr(local) || g_localDead.load()) {
        g_smoothTP_Active.store(false);
        g_teleportActive.store(false);
        g_teleportJustFinished.store(false);
        return;
    }

    int frames = g_smoothTP_Frames.fetch_add(1);
    if (frames > SMOOTH_TP_MAX_F) {
        g_smoothTP_Active.store(false);
        g_teleportJustFinished.store(true);   // ★ final write then deactivate
        return;
    }

    cpVect cur;
    if (!SafeGetActualPosition(local, cur)) {
        g_smoothTP_Active.store(false);
        g_teleportActive.store(false);
        g_teleportJustFinished.store(false);
        return;
    }
    float cx = (float)cur.x;
    float cy = (float)cur.y;

    float tx = g_smoothTP_TargetX.load();
    float ty = g_smoothTP_TargetY.load();

    float dx = tx - cx;
    float dy = ty - cy;
    float dist = sqrtf(dx*dx + dy*dy);

    if (dist < SMOOTH_TP_STEP) {
        // ★ Final position reached
        g_teleportX.store(tx);
        g_teleportY.store(ty);
        g_teleportActive.store(true);
        g_smoothTP_Active.store(false);
        g_teleportJustFinished.store(true);   // deactivate after next Apply
        traceLog("SmoothTP: done at (%.0f,%.0f)", tx, ty);
        return;
    }

    float stepX = cx + (dx / dist) * SMOOTH_TP_STEP;
    float stepY = cy + (dy / dist) * SMOOTH_TP_STEP;

    g_teleportX.store(stepX);
    g_teleportY.store(stepY);
    g_teleportActive.store(true);
}
void getBodyPosition_Hooked(cpVect* out, void* self) {
    if (g_gbpCallLogs.load() < 5) {
        if (g_gbpCallLogs.fetch_add(1) < 5) {
            traceLog("getBodyPosition call: out=%p self=%p", out, self);
        }
    }

    if (old_getBodyPosition_hook) old_getBodyPosition_hook(out, self);
    if (!out) return;

    void* local = g_localInstance.load();

    // Discovery: local player এর body pointer একবারই খুঁজি
    if (local && self == local && !g_bodyDiscoveryDone.load()) {
        TryDiscoverBodyPointer(self);
    }

    // ★ Position write এখন ApplyTeleportPosition() এ centralize করা হয়েছে
    //   এখানে শুধু return value override করি যাতে game-এর physics
    //   আমাদের write কে আবার সঠিকভাবে read করে।
    if (g_teleportActive.load()
        && g_bodyDiscoveryDone.load()
        && local && self == local
        && !g_localDead.load()) {
        out->x = (double)g_teleportX.load();
        out->y = (double)g_teleportY.load();
    }
}

void getBodyPosition_Coll_Hooked(cpVect* out, void* self) {
    if (old_collGetBody) old_collGetBody(out, self);
    if (!out) return;
    if (!g_teleportActive.load() || !g_bodyDiscoveryDone.load()) return;
    void* local = g_localInstance.load();
    if (!local || self != local || g_localDead.load()) return;
    out->x = (double)g_teleportX.load();
    out->y = (double)g_teleportY.load();
}

// ==================================================================
// Projectile hooks
// ==================================================================
static bool IsLocalPlayerWeapon(void* weapon) {
    if (!PlausiblePtr(weapon)) return false;
    void* local = g_localInstance.load();
    if (!PlausiblePtr(local)) return false;
    void* mine[4] = {nullptr, nullptr, nullptr, nullptr};
    if (fn_getPrimaryWeapon)   { if (GUARD_ENTER()) { GUARD_SET(); mine[0] = fn_getPrimaryWeapon(local);   GUARD_CLR(); } else GUARD_CLR(); }
    if (fn_getSecondaryWeapon) { if (GUARD_ENTER()) { GUARD_SET(); mine[1] = fn_getSecondaryWeapon(local); GUARD_CLR(); } else GUARD_CLR(); }
    if (fn_getDualWeapon)      { if (GUARD_ENTER()) { GUARD_SET(); mine[2] = fn_getDualWeapon(local);      GUARD_CLR(); } else GUARD_CLR(); }
    if (fn_getSideWeapon)      { if (GUARD_ENTER()) { GUARD_SET(); mine[3] = fn_getSideWeapon(local);      GUARD_CLR(); } else GUARD_CLR(); }
    for (int i = 0; i < 4; i++) if (mine[i] == weapon) return true;
    return false;
}
static inline bool IsFiredByLocalPlayer(cpVect spawnPos) {
    if (!g_localSeen.load()) return false;
    float lx = g_localWorldX.load(), ly = g_localWorldY.load();
    float dx = (float)spawnPos.x - lx, dy = (float)spawnPos.y - ly;
    return (dx*dx + dy*dy) < (250.f * 250.f);
}
void addBullet_Hook(void* self, cpVect pos, float rot, cpVect vel,
                    void* weapon, int ammoType, cpVect targetPos, void* strPtr) {
    if (g_silentAim.load() && g_hasAimTarget.load()) {
        float tx = g_aimTargetRawX.load(), ty = g_aimTargetRawY.load(), aimAngle = g_aimAngle.load();
        if (std::isfinite(tx) && std::isfinite(ty) && std::isfinite(aimAngle)) {
            float ddx = tx - (float)pos.x, ddy = ty - (float)pos.y;
            float distToTarget = sqrtf(ddx*ddx + ddy*ddy);
            float spawnDist = 25.0f;
            if (distToTarget > 5.0f && distToTarget * 0.6f < spawnDist) spawnDist = distToTarget * 0.6f;
            if (spawnDist < 8.0f) spawnDist = 8.0f;
            int mul = g_weaponSpeedMul.load();
            if (mul < 1) mul = 1; if (mul > 20) mul = 20;
            float nearSpeed = 350.0f + (float)(mul - 1) * 8.0f;
            if (nearSpeed > 500.0f) nearSpeed = 500.0f;
            pos.x = (double)(tx - cosf(aimAngle) * spawnDist);
            pos.y = (double)(ty - sinf(aimAngle) * spawnDist);
            rot = aimAngle;
            vel.x = (double)(cosf(aimAngle) * nearSpeed);
            vel.y = (double)(sinf(aimAngle) * nearSpeed);
            targetPos.x = (double)tx; targetPos.y = (double)ty;
        }
    }
    
    if (old_addBullet) old_addBullet(self, pos, rot, vel, weapon, ammoType, targetPos, strPtr);
}
void Joypad_getDirVector_Hook(cpVect* ret, void* self) {
    if (old_Joypad_getDirVector) old_Joypad_getDirVector(ret, self);
    if (g_aimMagnet.load() && g_hasAimAngle.load() && ret) {
        double mag2 = ret->x * ret->x + ret->y * ret->y;
        if (mag2 > 0.0001) {
            double mag = sqrt(mag2); float a = g_aimAngle.load();
            ret->x = (double)cosf(a) * mag; ret->y = (double)sinf(a) * mag;
        }
    }
}
float Joypad_getDirAngle_Hook(void* self) {
    if (g_aimMagnet.load() && g_hasAimAngle.load())
        return NormalizeDeg(90.0f - g_aimAngle.load() * RAD2DEG);
    return old_Joypad_getDirAngle ? old_Joypad_getDirAngle(self) : 0.0f;
}

// ==================================================================
// HP hooks
// ==================================================================
void setPlayerHealth_Hook(void* self, float health) {
    if (old_setPlayerHealth) old_setPlayerHealth(self, health);
    if (!IsModActive() || !PlausiblePtr(self)) return;
    int hp = (int)health;
    if (hp < -100 || hp > 100000) return;
    viewHPStore(self, hp);
}
void setHP_Hook(void* self, int hp) {
    // ★ NEW: local player detect (fallback সহ)
    void* local = g_localInstance.load();
    if (!PlausiblePtr(local)) local = g_lastLocalInstance.load();

    // ★ Teleport ON এবং local হলে HP floor at 1
    //   → Enemy damage HP কমাবে, কিন্তু মৃত্যু হবে না
    if (g_tpPadEnabled.load()
        && PlausiblePtr(self)
        && self == local
        && hp <= 0)
    {
        static std::atomic<uint64_t> s_lastLog{0};
        uint64_t nowMs = NowMs();
        uint64_t prev = s_lastLog.load();
        if (nowMs - prev > 500 && s_lastLog.compare_exchange_strong(prev, nowMs)) {
            traceLog("setHP: FLOOR HP=%d→1 for local (teleport ON)", hp);
        }
        hp = 1;   // ★ HP=1 এ restore
    }

    if (old_setHP) old_setHP(self, hp);
    if (!IsModActive() || !PlausiblePtr(self) || hp < -100 || hp > 100000) return;
    std::lock_guard<std::mutex> lock(g_soldierMutex);
    SoldierEntry& e = EnsureEntryLocked(self);
    uint64_t now = NowMs();
    uint64_t sinceDamage = (e.lastDamageMs > 0) ? (now - e.lastDamageMs) : 99999;
    if (e.lastKnownHP < 0 || hp <= e.lastKnownHP || sinceDamage > 150) e.lastKnownHP = hp;
    if (hp > e.observedMaxHP) e.observedMaxHP = hp;
}
void setAlive_Hook(void* self, bool alive) {
    // ★ local player detect (fallback সহ)
    void* local = g_localInstance.load();
    if (!PlausiblePtr(local)) local = g_lastLocalInstance.load();

    // ★ Teleport ON এবং local হলে dead flag reverse
    if (!alive
        && g_tpPadEnabled.load()
        && PlausiblePtr(self)
        && self == local)
    {
        static std::atomic<uint64_t> s_lastLog{0};
        uint64_t nowMs = NowMs();
        uint64_t prev = s_lastLog.load();
        if (nowMs - prev > 500 && s_lastLog.compare_exchange_strong(prev, nowMs)) {
            traceLog("setAlive: BLOCKED dead for local (teleport ON)");
        }
        alive = true;   // ★ reverse
    }

    if (old_setAlive) old_setAlive(self, alive);
    if (!IsModActive() || !PlausiblePtr(self)) return;
    std::lock_guard<std::mutex> lock(g_soldierMutex);
    SoldierEntry& e = EnsureEntryLocked(self);
    if (alive) {
        e.isDead = false;
        if (e.lastKnownHP <= 0) e.lastKnownHP = e.observedMaxHP > 0 ? e.observedMaxHP : 100;
        e.lastDamageMs = 0; e.lastSeenTick = NowMs();
        e.hasPrevPos = false; e.velX = 0.f; e.velY = 0.f;
    } else { e.isDead = true; e.lastKnownHP = 0; }
}
void addDamage_Hook(void* self, float damage, void* strPtr, int ammoType, bool flag) {
    if (old_addDamage) old_addDamage(self, damage, strPtr, ammoType, flag);
    if (!IsModActive() || !PlausiblePtr(self)) return;
    int dmgInt = (int)damage;
    if (dmgInt <= 0) return;
    std::lock_guard<std::mutex> lock(g_soldierMutex);
    SoldierEntry& e = EnsureEntryLocked(self);
    int cached = e.lastKnownHP < 0 ? 100 : e.lastKnownHP;
    int est = cached - dmgInt;
    if (est < 0) est = 0;
    e.lastKnownHP = est;
    e.lastDamageMs = NowMs();
}
void LocalAddDamage_Hook(void* self, float damage, void* strPtr, int ammoType, bool flag) {
    // ★ boundary block সরানো হয়েছে — enemy damage স্বাভাবিক কাজ করবে
    if (old_localAddDamage) old_localAddDamage(self, damage, strPtr, ammoType, flag);
    if (!IsModActive() || !PlausiblePtr(self)) return;
    int dmgInt = (int)damage; if (dmgInt <= 0) return;
    std::lock_guard<std::mutex> lock(g_soldierMutex);
    SoldierEntry& e = EnsureEntryLocked(self);
    int cached = e.lastKnownHP < 0 ? 100 : e.lastKnownHP;
    int est = cached - dmgInt; if (est < 0) est = 0;
    e.lastKnownHP = est; e.lastDamageMs = NowMs();
}
void HumanoidAddDamage_Hook(void* self, int damage, void* strPtr, int ammoType) {
    if (old_humanoidAddDamage) old_humanoidAddDamage(self, damage, strPtr, ammoType);
    if (!IsModActive() || !PlausiblePtr(self) || damage <= 0) return;
    std::lock_guard<std::mutex> lock(g_soldierMutex);
    SoldierEntry& e = EnsureEntryLocked(self);
    int cached = e.lastKnownHP < 0 ? 100 : e.lastKnownHP;
    int est = cached - damage; if (est < 0) est = 0;
    e.lastKnownHP = est; e.lastDamageMs = NowMs();
}
void HawkAddDamage_Hook(void* self, int damage, void* strPtr, int ammoType) {
    if (old_hawkAddDamage) old_hawkAddDamage(self, damage, strPtr, ammoType);
    if (!IsModActive() || !PlausiblePtr(self) || damage <= 0) return;
    std::lock_guard<std::mutex> lock(g_soldierMutex);
    SoldierEntry& e = EnsureEntryLocked(self);
    int cached = e.lastKnownHP < 0 ? 100 : e.lastKnownHP;
    int est = cached - damage; if (est < 0) est = 0;
    e.lastKnownHP = est; e.lastDamageMs = NowMs();
}
void WormAddDamage_Hook(void* self, int damage, void* strPtr, int ammoType) {
    if (old_wormAddDamage) old_wormAddDamage(self, damage, strPtr, ammoType);
    if (!IsModActive() || !PlausiblePtr(self) || damage <= 0) return;
    std::lock_guard<std::mutex> lock(g_soldierMutex);
    SoldierEntry& e = EnsureEntryLocked(self);
    int cached = e.lastKnownHP < 0 ? 100 : e.lastKnownHP;
    int est = cached - damage; if (est < 0) est = 0;
    e.lastKnownHP = est; e.lastDamageMs = NowMs();
}
void HumanoidUpdate_Hook(void* self, float dt) { if (old_humanoidUpdateStep) old_humanoidUpdateStep(self, dt); if (IsModActive() && PlausiblePtr(self)) RefreshSoldierData(self); }
void HawkUpdate_Hook(void* self, float dt)     { if (old_hawkUpdateStep) old_hawkUpdateStep(self, dt);     if (IsModActive() && PlausiblePtr(self)) RefreshSoldierData(self); }
void WormUpdate_Hook(void* self, float dt)     { if (old_wormUpdateStep) old_wormUpdateStep(self, dt);     if (IsModActive() && PlausiblePtr(self)) RefreshSoldierData(self); }

void LocalActivate_Hook(void* self) {
    if (PlausiblePtr(self)) {
        g_lastLocalInstance.store(self);              // ★ NEW: সবসময় backup করি
        void* prev = g_localInstance.load();
        if (prev != self || g_localDead.load()) {
            ClearAllState();
            g_localInstance.store(self);
            g_localInstanceSetMs.store(NowMs());
            g_localSeen.store(false); g_localDead.store(false);
            g_bodyDiscoveryDone.store(false);
            // InvalidateMapBounds();                    ★ মুছে দিন — spam বন্ধ হবে
            g_bodyOffsetFromSelf.store((uintptr_t)-1);
            g_posOffsetInBody.store(-1);
        }
        RefreshSoldierData(self);
    }
    if (old_LocalActivate) old_LocalActivate(self);
}
void StageUpdate_Hook(void* self, float dt) {
    if (old_StageUpdate) old_StageUpdate(self, dt);
    if (!IsModActive() && !g_unlimitedFlyPower.load()) return;
    
    uint64_t now = NowMs();
    int hz = g_lagEspUpdateHz.load();
    if (g_lagAntiLagMode.load() && hz > 30) hz = 30;
    if (hz < 10) hz = 10; if (hz > 60) hz = 60;
    uint64_t intervalMs = (uint64_t)(1000 / hz);
    if (now - g_lagLastEspUpdateMs < intervalMs) return;
    g_lagLastEspUpdateMs = now;
    if (!g_designValid.load()) RefreshDesignSize();
    BuildSnapshots();
    void* local = g_localInstance.load();
    if (PlausiblePtr(local)) {
        if (!g_bodyDiscoveryDone.load()) {
            TryDiscoverBodyPointer(local);
        }
        ComputeAimTarget(local);
        ExecuteAutoFire(local);
                // ★ Teleport write — প্রতি frame physics-এর পরে force করি
        if (!g_teleportFollowAim.load()) {
            ApplyTeleportPosition();
        }
        if (g_teleportFollowAim.load() && g_hasAimTarget.load()) {
            float tx = g_aimTargetRawX.load();
            float ty = g_aimTargetRawY.load();
            if (std::isfinite(tx) && std::isfinite(ty)) {
                g_teleportX.store(tx);
                g_teleportY.store(ty);
                g_teleportActive.store(true);
            }
        }
        if (g_wpnUnlimitedAmmo.load()) {
            void* wpns[4] = {nullptr, nullptr, nullptr, nullptr};
            if (fn_getPrimaryWeapon)   { if (GUARD_ENTER()) { GUARD_SET(); wpns[0] = fn_getPrimaryWeapon(local);   GUARD_CLR(); } else GUARD_CLR(); }
            if (fn_getSecondaryWeapon) { if (GUARD_ENTER()) { GUARD_SET(); wpns[1] = fn_getSecondaryWeapon(local); GUARD_CLR(); } else GUARD_CLR(); }
            if (fn_getDualWeapon)      { if (GUARD_ENTER()) { GUARD_SET(); wpns[2] = fn_getDualWeapon(local);      GUARD_CLR(); } else GUARD_CLR(); }
            if (fn_getSideWeapon)      { if (GUARD_ENTER()) { GUARD_SET(); wpns[3] = fn_getSideWeapon(local);      GUARD_CLR(); } else GUARD_CLR(); }
            for (int i = 0; i < 4; i++) {
                if (!PlausiblePtr(wpns[i])) continue;
                if (old_setAmmo) { if (GUARD_ENTER()) { GUARD_SET(); old_setAmmo(wpns[i], 9999); GUARD_CLR(); } else GUARD_CLR(); }
                if (old_setClip) { if (GUARD_ENTER()) { GUARD_SET(); old_setClip(wpns[i], 9999); GUARD_CLR(); } else GUARD_CLR(); }
            }
        }
    }
}


void physicsUpdate_Hook(void* self, float dt) {
    if (old_physicsUpdate) old_physicsUpdate(self, dt);
    if (g_teleportFollowAim.load()) return;

    if (g_smoothTP_Active.load()) {
        UpdateSmoothTeleport();
    }
    if (g_teleportActive.load()) {
        ApplyTeleportPosition();
    }
    if (g_teleportJustFinished.exchange(false)) {
        g_teleportActive.store(false);
        traceLog("TP: deactivated — player free");
    }

    // ★★★ NEW: Boundary Guard — সবসময় safe position এ আটকে রাখে ★★★
    if (g_tpPadEnabled.load()
        && g_bodyDiscoveryDone.load()
        && !g_teleportActive.load()
        && !g_smoothTP_Active.load())
    {
        void* local = g_localInstance.load();
        if (!PlausiblePtr(local)) local = g_lastLocalInstance.load();

        if (PlausiblePtr(local) && !g_localDead.load()) {
            cpVect cur;
            if (SafeGetActualPosition(local, cur)) {
                void* mgr = g_mapManagerInstance.load();
                bool isOut = false;

                if (PlausiblePtr(mgr) && old_isBoundryTile) {
                    if (GUARD_ENTER()) {
                        GUARD_SET();
                        isOut = old_isBoundryTile(mgr, cur);
                        GUARD_CLR();
                    } else GUARD_CLR();
                }

                if (!isOut) {
                    // ভেতরে আছি → safe anchor update করি
                    g_lastSafeX.store((float)cur.x);
                    g_lastSafeY.store((float)cur.y);
                    g_lastSafeValid.store(true);
                } else if (g_lastSafeValid.load()) {
                    // বাইরে চলে গেছি → force rescue
                    float sx = g_lastSafeX.load();
                    float sy = g_lastSafeY.load();
                    g_teleportX.store(sx);
                    g_teleportY.store(sy);
                    g_teleportActive.store(true);

                    static std::atomic<uint64_t> s_lastLog{0};
                    uint64_t nowMs = NowMs();
                    uint64_t prev = s_lastLog.load();
                    if (nowMs - prev > 300 && s_lastLog.compare_exchange_strong(prev, nowMs)) {
                        traceLog("BOUNDARY GUARD: rescued (%.0f,%.0f) → (%.0f,%.0f)",
                                 (float)cur.x, (float)cur.y, sx, sy);
                    }
                }
            }
        }
    }
}
void MgrUpdateRemote_Hook(void* self, float dt) {
    if (PlausiblePtr(self) && IsModActive() && fn_getLocalController) {
        void* local = nullptr;
        if (GUARD_ENTER()) { GUARD_SET(); local = fn_getLocalController(self); GUARD_CLR(); } else GUARD_CLR();
        if (PlausiblePtr(local)) {
            void* prev = g_localInstance.load();
            if (prev != local) { ClearAllState(); g_localInstance.store(local); g_localInstanceSetMs.store(NowMs()); g_localSeen.store(false); g_localDead.store(false); }
            RefreshSoldierData(local);
        }
    }
    if (old_MgrUpdateRemote) old_MgrUpdateRemote(self, dt);
}
void MgrUpdateStep_Hook(void* self, float dt) {
    if (PlausiblePtr(self) && IsModActive() && fn_getLocalController) {
        void* local = nullptr;
        if (GUARD_ENTER()) { GUARD_SET(); local = fn_getLocalController(self); GUARD_CLR(); } else GUARD_CLR();
        if (PlausiblePtr(local)) {
            void* prev = g_localInstance.load();
            if (prev != local) { ClearAllState(); g_localInstance.store(local); g_localInstanceSetMs.store(NowMs()); g_localSeen.store(false); g_localDead.store(false); }
            RefreshSoldierData(local);
        }
    }
    if (old_MgrUpdateStep) old_MgrUpdateStep(self, dt);
}
    
void MgrSpawnPlayer_Hook(void* self) {
    if (PlausiblePtr(self) && IsModActive() && fn_getLocalController) {
        void* local = nullptr;
        if (GUARD_ENTER()) { GUARD_SET(); local = fn_getLocalController(self); GUARD_CLR(); } else GUARD_CLR();
        if (PlausiblePtr(local)) {
            ClearAllState();
            g_localInstance.store(local);
            g_localInstanceSetMs.store(NowMs());
            g_localSeen.store(false); g_localDead.store(false);
            RefreshSoldierData(local);
        }
    }
    if (old_MgrSpawnPlayer) old_MgrSpawnPlayer(self);
}
void RemoteUpdateStep_Hook(void* self, float dt) { if (PlausiblePtr(self) && IsModActive()) RefreshSoldierData(self); if (old_RemoteUpdateStep) old_RemoteUpdateStep(self, dt); }
void AIUpdateStep_Hook(void* self, float dt)     { if (PlausiblePtr(self) && IsModActive()) RefreshSoldierData(self); if (old_AIUpdateStep) old_AIUpdateStep(self, dt); }
void EnemyMgrUpdateStep_Hook(void* self, float dt) { if (old_EnemyMgrUpdateStep) old_EnemyMgrUpdateStep(self, dt); }
void UpdatePeerDamage_Hook(void* self, void* data, void* strRef) { if (old_updatePeerDamage) old_updatePeerDamage(self, data, strRef); }

// ==================================================================
// Hook installer
// ==================================================================
#define SAFE_HOOK(enc_off, hook, orig, flag) do { \
    uintptr_t _a = g_libBase + DEC_OFF(enc_off); \
    HOOK_ABS((void*)_a, hook, orig); \
    if (orig) (flag).store(true); \
} while(0)

static void InstallHooksIfNeeded() {
    if (!g_libReady.load()) return;
    static bool logged = false;
    if (!logged) { crashLog("HOOK", "Installing hooks base=%p", (void*)g_libBase); logged = true; }

    fn_getLocalController    = (getLocalController_t)(g_libBase + DEC_OFF(Off::SoldierManager_getLocalController));
    fn_getHP                 = (getHP_t)             (g_libBase + DEC_OFF(Off::SoldierController_getHP));
    fn_getTeamId             = (getTeamId_t)         (g_libBase + DEC_OFF(Off::CollisionObject_getTeamId));
    fn_getSoldierView        = (getSoldierView_t)    (g_libBase + DEC_OFF(Off::SoldierController_getSoldierView));
    fn_getPlayerName         = (getPlayerName_t)     (g_libBase + DEC_OFF(Off::SoldierView_getPlayerName));
    fn_isDead                = (isDead_t)            (g_libBase + DEC_OFF(Off::SoldierController_isDead));
    fn_convertToWorldSpaceAR = (convertToWorldSpaceAR_t)(g_libBase + DEC_OFF(Off::CCNode_convertToWorldSpaceAR));
    fn_directorShared        = (directorShared_t)    (g_libBase + DEC_OFF(Off::CCDirector_sharedDirector));
    fn_directorGetVisible    = (directorGetSize_t)   (g_libBase + DEC_OFF(Off::CCDirector_getVisibleSize));
    fn_getPrimaryWeapon      = (getWeapon_t)         (g_libBase + DEC_OFF(Off::SoldierController_getPrimaryWeapon));
    fn_getSecondaryWeapon    = (getWeapon_t)         (g_libBase + DEC_OFF(Off::SoldierController_getSecondaryWeapon));
    fn_getDualWeapon         = (getWeapon_t)         (g_libBase + DEC_OFF(Off::SoldierController_getDualWeapon));
    fn_getSideWeapon         = (getWeapon_t)         (g_libBase + DEC_OFF(Off::SoldierController_getSideWeapon));
    fn_soldierFire           = (soldierFire_t)       (g_libBase + DEC_OFF(Off::SoldierController_fire));
    fn_getBulletSpeed        = (getBulletSpeed_t)    (g_libBase + DEC_OFF(Off::Weapon_getBulletSpeed));
    fn_getRange              = (getRange_t)          (g_libBase + DEC_OFF(Off::Weapon_getRange));
    fn_setFireAngleWpn       = (setFireAngle_wpn_t)  (g_libBase + DEC_OFF(Off::Weapon_setFireAngle));
    fn_addShell              = (addShell_t)          (g_libBase + DEC_OFF(Off::ProjectileManager_addShell));
    fn_addRocket             = (addRocket_t)         (g_libBase + DEC_OFF(Off::ProjectileManager_addRocket));
    fn_addGrenade            = (addGrenade_t)        (g_libBase + DEC_OFF(Off::ProjectileManager_addGrenade));
    fn_addSaw                = (addSaw_t)            (g_libBase + DEC_OFF(Off::ProjectileManager_addSaw));
    fn_addFlame              = (addFlame_t)          (g_libBase + DEC_OFF(Off::ProjectileManager_addFlame));
    fn_addGasCloudAt         = (addGasCloudAt_t)     (g_libBase + DEC_OFF(Off::EffectsManager_addGasCloudAt));
    fn_setPowerF             = (setPowerF_t)         (g_libBase + DEC_OFF(Off::SoldierLocalController_setPower));
    fn_switchPrimaryToDual   = (switchToDual_t)      (g_libBase + DEC_OFF(Off::SoldierLocalController_switchPrimaryToDual));
    fn_switchSecondaryToDual = (switchToDual_t)      (g_libBase + DEC_OFF(Off::SoldierLocalController_switchSecondaryToDual));
    fn_setThrust             = (setThrust_t)         (g_libBase + DEC_OFF(Off::SoldierController_setThrust));

    g_aimResolved.store(true);
    RefreshDesignSize();

    if (!g_teleportHooksOk.load()) {
        SAFE_HOOK(Off::SoldierController_getBodyPosition, getBodyPosition_Hooked, old_getBodyPosition_hook, g_teleportHooksOk);
        {
            uintptr_t _a = g_libBase + DEC_OFF(Off::CollisionObject_getBodyPosition);
            HOOK_ABS((void*)_a, getBodyPosition_Coll_Hooked, old_collGetBody);
        }
        if (old_getBodyPosition_hook) fn_getBodyPosition = old_getBodyPosition_hook;
        crashLog("HOOK", "Teleport hooks OK [trampoline fixed]");
    }

    if (!g_wpnHooksOk.load()) {
        SAFE_HOOK(Off::ProjectileManager_addBullet,        addBullet_Hook,             old_addBullet,             g_wpnHooksOk);
        SAFE_HOOK(Off::Weapon_getRandomFiringAngle,        getRandomFiringAngle_Hook,  old_getRandomFiringAngle,  g_wpnHooksOk);
        SAFE_HOOK(Off::Weapon_getRange,                    getRange_Hook,              old_getRange,              g_wpnHooksOk);
        SAFE_HOOK(Off::Weapon_getBulletSpeed,              getBulletSpeed_Hook,        old_getBulletSpeed,        g_wpnHooksOk);
        SAFE_HOOK(Off::Joypad_getDirectionAngle,           Joypad_getDirAngle_Hook,    old_Joypad_getDirAngle,    g_wpnHooksOk);
        SAFE_HOOK(Off::Joypad_getDirectionVector,          Joypad_getDirVector_Hook,   old_Joypad_getDirVector,   g_wpnHooksOk);
        SAFE_HOOK(Off::Weapon_getRoundsPerFire,            getRoundsPerFire_Hook,      old_getRoundsPerFire,      g_wpnHooksOk);
        SAFE_HOOK(Off::Weapon_getAmmo,                     getAmmo_Hook,               old_getAmmo,               g_wpnHooksOk);
        SAFE_HOOK(Off::Weapon_setAmmo,                     setAmmo_Hook,               old_setAmmo,               g_wpnHooksOk);
        SAFE_HOOK(Off::Weapon_subAmmo,                     subAmmo_Hook,               old_subAmmo,               g_wpnHooksOk);
        SAFE_HOOK(Off::Weapon_getClip,                     getClip_Hook,               old_getClip,               g_wpnHooksOk);
        SAFE_HOOK(Off::Weapon_setClip,                     setClip_Hook,               old_setClip,               g_wpnHooksOk);
        SAFE_HOOK(Off::Weapon_getClipCapacity,             getClipCapacity_Hook,       old_getClipCapacity,       g_wpnHooksOk);
        SAFE_HOOK(Off::Weapon_getAmmoCapacity,             getAmmoCapacity_Hook,       old_getAmmoCapacity,       g_wpnHooksOk);
        SAFE_HOOK(Off::Weapon_getReloadTime,               getReloadTime_Hook,         old_getReloadTime,         g_wpnHooksOk);
        SAFE_HOOK(Off::Weapon_isDualWield,                 isDualWield_Hook,           old_isDualWield,           g_wpnHooksOk);
        SAFE_HOOK(Off::Weapon_isDualWieldOnly,             isDualWieldOnly_Hook,       old_isDualWieldOnly,       g_wpnHooksOk);
        SAFE_HOOK(Off::Weapon_isDualWieldPrimaryOnly,      isDualWieldPrimaryOnly_Hook,old_isDualWieldPrimaryOnly,g_wpnHooksOk);
        SAFE_HOOK(Off::Weapon_pickupAsDual,                pickupAsDual_Hook,          old_pickupAsDual,          g_wpnHooksOk);
        SAFE_HOOK(Off::Weapon_setPickupAsDual,             setPickupAsDual_Hook,       old_setPickupAsDual,       g_wpnHooksOk);
        SAFE_HOOK(Off::Weapon_getZoomLevel,                getZoomLevel_Hook,          old_getZoomLevel,          g_wpnHooksOk);
        SAFE_HOOK(Off::Weapon_applyMaxZoomScale,           applyMaxZoomScale_Hook,     old_applyMaxZoomScale,     g_wpnHooksOk);
        SAFE_HOOK(Off::Weapon_getDamage,                   getDamage_w_Hook,           old_getDamage_w,           g_wpnHooksOk);
        SAFE_HOOK(Off::Weapon_getZoomScale,                getZoomScale_Hook,          old_getZoomScale,          g_wpnHooksOk);
        SAFE_HOOK(Off::SoldierLocalController_updateStep,  soldierLocalUpdateStep_Hook,old_soldierLocalUpdateStep, g_wpnHooksOk);
        crashLog("HOOK", "Weapon hooks OK");
    }

    if (!g_wpnUnlockHooksOk.load()) {
        SAFE_HOOK(Off::WeaponsModel_isUnlockable,            isUnlockable_Hook,            old_isUnlockable,            g_wpnUnlockHooksOk);
        SAFE_HOOK(Off::WeaponsModel_isUpgradable,            isUpgradable_Hook,            old_isUpgradable,            g_wpnUnlockHooksOk);
        SAFE_HOOK(Off::WeaponsModel_getDualWieldUnlockLevel, getDualWieldUnlockLevel_Hook, old_getDualWieldUnlockLevel, g_wpnUnlockHooksOk);
        crashLog("HOOK", "Unlock hooks OK");
    }

    if (!g_flyHooksOk.load()) {
        SAFE_HOOK(Off::MapManager_getMaxPower,        getMaxPower_Hook,     old_getMaxPower,     g_flyHooksOk);
        SAFE_HOOK(Off::SoldierController_setThrust,   setThrust_Hook,       old_setThrust,       g_flyHooksOk);
        crashLog("HOOK", "Fly hooks OK");
    }

    if (!g_wallHooksOk.load()) {
        SAFE_HOOK(Off::MapManager_addStaticBodyShape, addStaticBodyShape_Hook, old_addStaticBodyShape, g_wallHooksOk);
        SAFE_HOOK(Off::MapManager_addStaticBodyPoly,  addStaticBodyPoly_Hook,  old_addStaticBodyPoly,  g_wallHooksOk);
        SAFE_HOOK(Off::MapManager_isCollisionTile,    isCollisionTile_Hook,    old_isCollisionTile,    g_wallHooksOk);
        SAFE_HOOK(Off::MapManager_mapCollision,       mapCollision_Hook,       old_mapCollision,       g_wallHooksOk);
        SAFE_HOOK(Off::MapManager_isBoundryTile,      isBoundryTile_Hook,      old_isBoundryTile,      g_wallHooksOk);
        crashLog("HOOK", "Wall hooks OK");
    }

    if (!g_bombGasHooksOk.load()) {
        SAFE_HOOK(Off::EffectsManager_addExplosionAt, addExplosionAt_Hook, old_addExplosionAt, g_bombGasHooksOk);
        crashLog("HOOK", "Bomb/gas hooks OK");
    }
        if (!g_physHookOk.load()) {
        SAFE_HOOK(Off::PhysicsManager_updateStep, physicsUpdate_Hook, old_physicsUpdate, g_physHookOk);
        crashLog("HOOK", "Physics hook OK");
    }

    if (!g_mgrHooksOk.load()) {
        SAFE_HOOK(Off::SoldierManager_getRespawnTime,  getRespawnTime_Hook,  old_getRespawnTime,  g_mgrHooksOk);
        SAFE_HOOK(Off::SoldierManager_isRespawning,    isRespawning_Hook,    old_isRespawning,    g_mgrHooksOk);
        SAFE_HOOK(Off::SoldierManager_respawnPlayer,   MgrRespawnPlayer_Hook,old_MgrRespawnPlayer,g_mgrHooksOk);
        SAFE_HOOK(Off::SoldierView_setPlayerHealth,    setPlayerHealth_Hook, old_setPlayerHealth, g_mgrHooksOk);
        SAFE_HOOK(Off::SoldierController_setHP,        setHP_Hook,           old_setHP,           g_mgrHooksOk);
        SAFE_HOOK(Off::SoldierController_setAlive,     setAlive_Hook,        old_setAlive,        g_mgrHooksOk);
        SAFE_HOOK(Off::SoldierController_addDamage,    addDamage_Hook,       old_addDamage,       g_mgrHooksOk);
        SAFE_HOOK(Off::SoldierLocalController_addDamage,LocalAddDamage_Hook, old_localAddDamage,  g_mgrHooksOk);
        SAFE_HOOK(Off::Stage_update,                   StageUpdate_Hook,     old_StageUpdate,     g_mgrHooksOk);
        SAFE_HOOK(Off::SoldierLocalController_activatePlayer, LocalActivate_Hook, old_LocalActivate, g_mgrHooksOk);
        SAFE_HOOK(Off::SoldierManager_updateRemoteSoldiers, MgrUpdateRemote_Hook, old_MgrUpdateRemote, g_mgrHooksOk);
        SAFE_HOOK(Off::SoldierManager_updateStep,      MgrUpdateStep_Hook,   old_MgrUpdateStep,   g_mgrHooksOk);
        SAFE_HOOK(Off::SoldierManager_spawnPlayer,     MgrSpawnPlayer_Hook,  old_MgrSpawnPlayer,  g_mgrHooksOk);
        SAFE_HOOK(Off::SoldierRemoteController_updateStep, RemoteUpdateStep_Hook, old_RemoteUpdateStep, g_mgrHooksOk);
        SAFE_HOOK(Off::SoldierAIController_updateStep, AIUpdateStep_Hook,    old_AIUpdateStep,    g_mgrHooksOk);
        SAFE_HOOK(Off::EnemyManager_updateStep,        EnemyMgrUpdateStep_Hook, old_EnemyMgrUpdateStep, g_mgrHooksOk);
        SAFE_HOOK(Off::NetworkMessageDispatcher_updatePeerDamage, UpdatePeerDamage_Hook, old_updatePeerDamage, g_mgrHooksOk);
        crashLog("HOOK", "Mgr hooks OK");
    }

    if (!g_droneHooksOk.load()) {
        SAFE_HOOK(Off::HumanoidDrone_addDamage,  HumanoidAddDamage_Hook, old_humanoidAddDamage, g_droneHooksOk);
        SAFE_HOOK(Off::HawkDrone_addDamage,      HawkAddDamage_Hook,     old_hawkAddDamage,     g_droneHooksOk);
        SAFE_HOOK(Off::WormDrone_addDamage,      WormAddDamage_Hook,     old_wormAddDamage,     g_droneHooksOk);
        SAFE_HOOK(Off::HumanoidDrone_updateStep, HumanoidUpdate_Hook,    old_humanoidUpdateStep,g_droneHooksOk);
        SAFE_HOOK(Off::HawkDrone_updateStep,     HawkUpdate_Hook,        old_hawkUpdateStep,    g_droneHooksOk);
        SAFE_HOOK(Off::WormDrone_updateStep,     WormUpdate_Hook,        old_wormUpdateStep,    g_droneHooksOk);
        crashLog("HOOK", "Drone hooks OK");
    }
    // ESP diagnostic
{
    void* d = fn_directorShared ? fn_directorShared() : nullptr;
    if (d) {
        MSSize vs = fn_directorGetVisible(d);
        crashLog("ESP", "Director valid: visibleSize=%.1fx%.1f, designValid=%d",
                 vs.w, vs.h, (int)g_designValid.load());
    } else {
        crashLog("ESP", "Director NOT AVAILABLE — fallback 1280x720");
    }
}
}

// ==================================================================
// Simple patches
// ==================================================================
static MemoryPatch g_patchMaxLevel, g_patchNoLocalDamage;
static std::atomic<bool> g_maxLevelInit{false}, g_noLocalDamageInit{false};

static void ApplyMaxLevelPatch(bool e) {
    if (!g_libReady.load()) return;
    if (!g_maxLevelInit.load()) {
        g_patchMaxLevel = MemoryPatch::createWithHex(
            g_libBase + DEC_OFF(Off::MaxLevel_patch),
            OBFUSCATE("64 00 A0 E3 1E FF 2F E1"));
        g_maxLevelInit.store(true);
    }
    if (e) g_patchMaxLevel.Modify(); else g_patchMaxLevel.Restore();
}
static void ApplyReloadPatch(bool e) {
    if (!g_libReady.load()) return;
    if (!g_noLocalDamageInit.load()) {
        g_patchNoLocalDamage = MemoryPatch::createWithHex(
            g_libBase + DEC_OFF(Off::SoldierLocalController_addDamage),
            OBFUSCATE("1E FF 2F E1"));
        g_noLocalDamageInit.store(true);
    }
    if (e) g_patchNoLocalDamage.Modify(); else g_patchNoLocalDamage.Restore();
}

// ==================================================================
// ESP Java methods
// ==================================================================
static jclass    g_espClass    = nullptr;
static jmethodID g_espDrawLine = nullptr;
static jmethodID g_espDrawRect = nullptr;
static jmethodID g_espDrawText = nullptr;
static void CacheESPMethods(JNIEnv* env, jobject espView) {
    if (g_espClass) return;
    jclass cls = env->GetObjectClass(espView);
    if (!cls) return;
    jmethodID ln = env->GetMethodID(cls, "DrawLine", "(Landroid/graphics/Canvas;IIIIFFFFF)V");
    if (env->ExceptionCheck()) env->ExceptionClear();
    jmethodID rt = env->GetMethodID(cls, "DrawRect", "(Landroid/graphics/Canvas;IIIIFFFFF)V");
    if (env->ExceptionCheck()) env->ExceptionClear();
    jmethodID tx = env->GetMethodID(cls, "DrawText", "(Landroid/graphics/Canvas;Ljava/lang/String;FFIIIIF)V");
    if (env->ExceptionCheck()) env->ExceptionClear();
    if (!ln || !rt || !tx) { env->DeleteLocalRef(cls); return; }
    g_espClass = (jclass)env->NewGlobalRef(cls);
    g_espDrawLine = ln; g_espDrawRect = rt; g_espDrawText = tx;
    env->DeleteLocalRef(cls);
}
template<typename... Args>
static inline void DrawLineColored(JNIEnv* env, jobject v, jobject c, Args... args) {
    if (!env || !v || !c || !g_espDrawLine) return;
    env->CallVoidMethod(v, g_espDrawLine, c, args...);
    if (env->ExceptionCheck()) env->ExceptionClear();
}
template<typename... Args>
static inline void DrawRectColored(JNIEnv* env, jobject v, jobject c, Args... args) {
    if (!env || !v || !c || !g_espDrawRect) return;
    env->CallVoidMethod(v, g_espDrawRect, c, args...);
    if (env->ExceptionCheck()) env->ExceptionClear();
}
static void HpGradient(float hpFrac, int& r, int& g, int& b) {
    if (hpFrac < 0.f) hpFrac = 0.f;
    if (hpFrac > 1.f) hpFrac = 1.f;
    if (hpFrac >= 0.5f) { float t = (hpFrac - 0.5f) * 2.0f; r = (int)(255.f * (1.f - t)); g = 235; b = 70; }
    else { float t = hpFrac * 2.0f; r = 255; g = (int)(90.f + 145.f * t); b = (int)(60.f * (1.f - t)); }
}
static void DrawRing(JNIEnv* env, jobject v, jobject c,
                     float cx, float cy, float radius,
                     int a, int r, int g, int b, float thickness) {
    const int SEG = 64;
    float step = 2.0f * 3.14159265f / (float)SEG;
    float px = cx + radius, py = cy;
    for (int i = 1; i <= SEG; i++) {
        float ang = step * (float)i;
        float x = cx + radius * cosf(ang);
        float y = cy + radius * sinf(ang);
        DrawLineColored(env, v, c, a, r, g, b, thickness, px, py, x, y);
        px = x; py = y;
    }
}
static void DrawPremiumFovCircle(JNIEnv* env, jobject v, jobject c,
                                  float cx, float cy, float radius, float timeSec) {
    float pulse = 0.5f + 0.5f * sinf(timeSec * 2.6f);
    int glowPulse = 45 + (int)(60.f * pulse);
    DrawRing(env, v, c, cx, cy, radius + 16.f, glowPulse / 3, SKY_DEEP_R, SKY_DEEP_G, SKY_DEEP_B, 18.f);
    DrawRing(env, v, c, cx, cy, radius + 10.f, glowPulse / 2, SKY_R, SKY_G, SKY_B, 11.f);
    DrawRing(env, v, c, cx, cy, radius + 5.f,  glowPulse,     SKY_LIGHT_R, SKY_LIGHT_G, SKY_LIGHT_B, 6.f);
    DrawRing(env, v, c, cx, cy, radius, 245, SKY_R, SKY_G, SKY_B, 2.8f);
    DrawRing(env, v, c, cx, cy, radius - 5.f, 160, SKY_LIGHT_R, SKY_LIGHT_G, SKY_LIGHT_B, 1.2f);
}
static void DrawPremiumBox(JNIEnv* env, jobject v, jobject c,
                            float x, float y, float w, float h,
                            float thickness, float timeSec, int pulseAlpha) {
    const int cr = SKY_R, cg = SKY_G, cb = SKY_B;
    (void)pulseAlpha;
    const float dashLen = 14.f;
    const float gapLen  = 10.f;
    const float phase   = fmodf(timeSec * 45.f, dashLen + gapLen);
    { float pos = x - dashLen + phase;
      while (pos < x + w) {
          float a = pos, b = pos + dashLen;
          if (a < x) a = x;
          if (b > x + w) b = x + w;
          if (b > a) DrawLineColored(env, v, c, 255, cr, cg, cb, thickness, a, y, b, y);
          pos += dashLen + gapLen;
      } }
    { float pos = x + w + phase;
      while (pos > x - dashLen) {
          float a = pos - dashLen, b = pos;
          if (a < x) a = x;
          if (b > x + w) b = x + w;
          if (b > a) DrawLineColored(env, v, c, 255, cr, cg, cb, thickness, a, y + h, b, y + h);
          pos -= dashLen + gapLen;
      } }
    { float pos = y - dashLen + phase;
      while (pos < y + h) {
          float a = pos, b = pos + dashLen;
          if (a < y) a = y;
          if (b > y + h) b = y + h;
          if (b > a) DrawLineColored(env, v, c, 255, cr, cg, cb, thickness, x, a, x, b);
          pos += dashLen + gapLen;
      } }
    { float pos = y + h + phase;
      while (pos > y - dashLen) {
          float a = pos - dashLen, b = pos;
          if (a < y) a = y;
          if (b > y + h) b = y + h;
          if (b > a) DrawLineColored(env, v, c, 255, cr, cg, cb, thickness, x + w, a, x + w, b);
          pos -= dashLen + gapLen;
      } }
    float cLen = (w < h ? w : h) * 0.24f;
    if (cLen < 10.f) cLen = 10.f;
    if (cLen > 26.f) cLen = 26.f;
    float cw = thickness + 1.0f;
    DrawLineColored(env, v, c, 255, cr, cg, cb, cw, x, y, x + cLen, y);
    DrawLineColored(env, v, c, 255, cr, cg, cb, cw, x, y, x, y + cLen);
    DrawLineColored(env, v, c, 255, cr, cg, cb, cw, x + w, y, x + w - cLen, y);
    DrawLineColored(env, v, c, 255, cr, cg, cb, cw, x + w, y, x + w, y + cLen);
    DrawLineColored(env, v, c, 255, cr, cg, cb, cw, x, y + h, x + cLen, y + h);
    DrawLineColored(env, v, c, 255, cr, cg, cb, cw, x, y + h, x, y + h - cLen);
    DrawLineColored(env, v, c, 255, cr, cg, cb, cw, x + w, y + h, x + w - cLen, y + h);
    DrawLineColored(env, v, c, 255, cr, cg, cb, cw, x + w, y + h, x + w, y + h - cLen);
}


static void InvalidateMapBounds() {
    g_mapBoundsDetected.store(false);
    traceLog("MAP BOUNDS: invalidated (stub)");
}

// ==================================================================
// SafeGetActualPosition — hook bypass করে actual body position পড়ে
// ==================================================================
static bool SafeGetActualPosition(void* s, cpVect& out) {
    if (!PlausiblePtr(s) || !old_getBodyPosition_hook) return false;
    out.x = out.y = 0;
    if (GUARD_ENTER()) { GUARD_SET(); old_getBodyPosition_hook(&out, s); GUARD_CLR(); }
    else { GUARD_CLR(); return false; }
    if (!std::isfinite(out.x) || !std::isfinite(out.y)) return false;
    if (std::fabs(out.x) < 5.0 && std::fabs(out.y) < 5.0) return false;
    if (std::fabs(out.x) > 20000.0 || std::fabs(out.y) > 20000.0) return false;
    return true;
}

// ==================================================================
// IsPositionSafe — map boundary + wall check
// ==================================================================
static bool IsPositionSafe(void* mgr, float x, float y) {
    if (!mgr) return true;
    cpVect p{ (double)x, (double)y };

    if (old_isBoundryTile) {
        bool isB = false;
        if (GUARD_ENTER()) { GUARD_SET(); isB = old_isBoundryTile(mgr, p); GUARD_CLR(); }
        else GUARD_CLR();
        if (isB) return false;    // Outside map
    }
    if (old_isCollisionTile) {
        bool isC = false;
        if (GUARD_ENTER()) { GUARD_SET(); isC = old_isCollisionTile(mgr, p); GUARD_CLR(); }
        else GUARD_CLR();
        if (isC) return false;    // Inside solid wall
    }
    return true;
}

// ==================================================================
// FindSafeTarget — Desired unsafe হলে player এর দিকে walk back
// ==================================================================
static void FindSafeTarget(void* mgr, float px, float py,
                            float desiredX, float desiredY,
                            float& safeX, float& safeY) {
    if (!mgr) { safeX = desiredX; safeY = desiredY; return; }

    // Target safe? use it
    if (IsPositionSafe(mgr, desiredX, desiredY)) {
        safeX = desiredX;
        safeY = desiredY;
        return;
    }

    // Walk from DESIRED back toward PLAYER, find first safe point
    float dx = px - desiredX;
    float dy = py - desiredY;
    float dist = sqrtf(dx*dx + dy*dy);
    if (dist < 50.f) { safeX = px; safeY = py; return; }

    int steps = (int)(dist / 100.f);
    if (steps < 1) steps = 1;
    if (steps > 200) steps = 200;

    for (int i = 1; i <= steps; i++) {
        float t = (float)i / (float)steps;
        float cx = desiredX + dx * t;
        float cy = desiredY + dy * t;
        if (IsPositionSafe(mgr, cx, cy)) {
            safeX = cx;
            safeY = cy;
            return;
        }
    }
    // Fallback: player position
    safeX = px;
    safeY = py;
}

extern "C" JNIEXPORT void JNICALL
Java_com_android_support_Menu_SetTeleportTargetNorm(JNIEnv*, jclass,
        jfloat nx, jfloat ny) {
    if (!g_libReady.load()) return;
    if (!g_tpPadEnabled.load()) return;
    if (nx < 0.f) nx = 0.f;
    if (nx > 1.f) nx = 1.f;
    if (ny < 0.f) ny = 0.f;
    if (ny > 1.f) ny = 1.f;
    
    const float MARGIN = 0.18f;                       // 18% padding
    const float nxMin  = MARGIN;
    const float nxMax  = 1.0f - MARGIN;               // 0.82
    const float nyMin  = MARGIN;
    const float nyMax  = 1.0f - MARGIN;

    if (nx < nxMin) nx = nxMin;
    if (nx > nxMax) nx = nxMax;
    if (ny < nyMin) ny = nyMin;
    if (ny > nyMax) ny = nyMax;

    g_smoothTP_Active.store(false);
    g_teleportActive.store(false);
    g_teleportJustFinished.store(false);

    void* local = g_localInstance.load();
    if (!PlausiblePtr(local)) { traceLog("TP: no player"); return; }

    cpVect pp;
if (!SafeGetActualPosition(local, pp)) {
    traceLog("TP: no actual pos");
    return;
}
float px = (float)pp.x;
float py = (float)pp.y;

// ★ Hard clamp: world bounds
const float WORLD_MIN_X = -800.f;
const float WORLD_MAX_X =  2000.f;
const float WORLD_MIN_Y = -800.f;
const float WORLD_MAX_Y =  2500.f;

if (px < WORLD_MIN_X) px = WORLD_MIN_X;
if (px > WORLD_MAX_X) px = WORLD_MAX_X;
if (py < WORLD_MIN_Y) py = WORLD_MIN_Y;
if (py > WORLD_MAX_Y) py = WORLD_MAX_Y;

    // ★ Rectangular safe range — map-এর এক চতুর্থাংশ
    const float RANGE_X = 350.f;   // ★ 600 → 350
const float RANGE_Y = 250.f;   // ★ 400 → 250
    float offX = (nx - 0.5f) * 2.0f * RANGE_X;
    float offY = (0.5f - ny) * 2.0f * RANGE_Y;

    float desiredX = px + offX;
    float desiredY = py + offY;

    // Validate + walk back if unsafe
    float safeX = desiredX, safeY = desiredY;
    void* mgr = g_mapManagerInstance.load();
    if (mgr) {
        FindSafeTarget(mgr, px, py, desiredX, desiredY, safeX, safeY);
    }

    float dx = safeX - px, dy = safeY - py;
    if (dx*dx + dy*dy < 900.f) {
        traceLog("TP: too close");
        return;
    }

    g_smoothTP_TargetX.store(safeX);
    g_smoothTP_TargetY.store(safeY);
    g_smoothTP_Frames.store(0);
    g_smoothTP_Active.store(true);
    g_teleportJustFinished.store(false);

    traceLog("TP: player=(%.0f,%.0f) safe=(%.0f,%.0f)", px, py, safeX, safeY);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_android_support_Menu_IsSmoothTeleportActive(JNIEnv*, jclass) {
    return g_smoothTP_Active.load() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_android_support_Menu_GetTeleportEnabled(JNIEnv*, jclass) {
    return g_tpPadEnabled.load() ? JNI_TRUE : JNI_FALSE;
}



extern "C" JNIEXPORT void JNICALL
Java_com_android_support_Menu_Draw(JNIEnv* env, jclass, jobject espView, jobject canvas) {
    if (!espView || !canvas) return;
        bool espOn    = g_espEnabled.load();
    bool fovWants = g_drawFovCircle.load();
    if (!espOn && !fovWants) return;
    CacheESPMethods(env, espView);
    if (!g_espClass || !g_espDrawLine || !g_espDrawRect || !g_espDrawText) return;

    jclass canvasCls = env->GetObjectClass(canvas);
    jmethodID getW = env->GetMethodID(canvasCls, "getWidth",  "()I");
    jmethodID getH = env->GetMethodID(canvasCls, "getHeight", "()I");
    if (env->ExceptionCheck()) env->ExceptionClear();
    int sw = env->CallIntMethod(canvas, getW);
    int sh = env->CallIntMethod(canvas, getH);
    if (env->ExceptionCheck()) env->ExceptionClear();
    env->DeleteLocalRef(canvasCls);
    if (sw <= 0 || sh <= 0) return;

  

    if (!g_designValid.load()) RefreshDesignSize();
float designW = g_designW.load(), designH = g_designH.load();

// ★ Fallback: যদি design না থাকে, তাহলে canvas aspect ratio মানি
if (!std::isfinite(designW) || designW < 10.f) designW = (float)sw;
if (!std::isfinite(designH) || designH < 10.f) designH = (float)sh;
    float scaleX = (float)sw / designW, scaleY = (float)sh / designH;
    float uniformScale = (scaleX < scaleY) ? scaleX : scaleY;
    float offX = ((float)sw - designW * uniformScale) * 0.5f;
    float offY = ((float)sh - designH * uniformScale) * 0.5f;

    uint64_t nowMs = NowMs();
    float timeSec = (float)(nowMs % 100000) * 0.001f;
    float pulse = 0.5f + 0.5f * sinf(timeSec * 4.0f);
    int pulseAlpha = 180 + (int)(75.f * pulse);

   
    if (fovWants) {
        float cx = (float)sw * 0.5f, cy = (float)sh * 0.5f;
        int fovDesign = g_fovPixels.load();
        if (fovDesign > 350) fovDesign = 350;
        if (fovDesign < 60)  fovDesign = 60;
        float radius = (float)fovDesign * uniformScale;
        if (radius < 30.f) radius = 30.f;
        DrawPremiumFovCircle(env, espView, canvas, cx, cy, radius, timeSec);
    }
    if (!espOn) return;

    bool skipExtra = g_lagSkipExtraDraw.load();
    std::vector<ESPSoldier> snaps;
    { std::lock_guard<std::mutex> lock(g_soldierMutex); snaps = g_soldierSnapshots; }

    int   boxThick = g_espBoxWidth.load();
    if (boxThick < 1)  boxThick = 1;
    if (boxThick > 10) boxThick = 10;
    float boxThickness = (float)boxThick;
    float lineThickness = boxThickness;
    int   localTeam = g_localTeam.load();
    void* localInst = g_localInstance.load();
    void* aimTarget = g_currentAimTarget.load();
    float centerTopX = sw * 0.5f;

    float sizeMul = (float)g_boxSizeMul.load() / 100.0f;
    float boxH = sh * 0.16f * sizeMul;
    float boxW = boxH * 0.55f;
    if (boxH < 50.f) boxH = 50.f;
    if (boxH > sh*0.55f) boxH = sh*0.55f;
    if (boxW < 25.f) boxW = 25.f;

    const float lineTopPad = 130.0f;
    if (g_espLine.load()) {
        DrawRing(env, espView, canvas, centerTopX, lineTopPad, 9.0f, 255, 0, 0, 0, 4.5f);
        DrawRing(env, espView, canvas, centerTopX, lineTopPad, 5.5f, pulseAlpha, SKY_R, SKY_G, SKY_B, 2.4f);
    }

    for (const ESPSoldier& s : snaps) {
        if (!s.hasScreen) continue;
        if (s.instance == localInst) continue;
        if (!s.isLocal && s.hp <= 0) continue;
        if (g_espEnemyOnly.load() && localTeam > 0 && s.teamId == localTeam) continue;

        float sx = s.screenX * uniformScale + offX;
        float sy = (float)sh - (s.screenY * uniformScale + offY);
        if (!std::isfinite(sx) || !std::isfinite(sy)) continue;
        if (sx < -sw || sx > sw * 2 || sy < -sh || sy > sh * 2) continue;

        float boxLeft = sx - boxW * 0.5f;
        float boxTop  = sy - boxH * 0.5f;
        float boxBottom = boxTop + boxH;
        bool isAimed = (s.instance == aimTarget);

        if (g_espLine.load()) {
            DrawLineColored(env, espView, canvas, 255, SKY_R, SKY_G, SKY_B, lineThickness,
                            centerTopX, lineTopPad, sx, boxTop);
        }
        if (g_espBox.load()) {
            DrawPremiumBox(env, espView, canvas, boxLeft, boxTop, boxW, boxH,
                           boxThickness, timeSec, pulseAlpha);
        }
        if (isAimed) {
            DrawRectColored(env, espView, canvas, pulseAlpha, SKY_LIGHT_R, SKY_LIGHT_G, SKY_LIGHT_B, 1.2f,
                            boxLeft - 3.f, boxTop - 3.f, boxW + 6.f, boxH + 6.f);
        }
        if (!skipExtra && g_espHealth.load() && s.maxHP > 0) {
            float hpFrac = (float)s.hp / (float)s.maxHP;
            if (hpFrac < 0.f) hpFrac = 0.f;
            if (hpFrac > 1.f) hpFrac = 1.f;
            const float gap = 8.0f;
            float barW = boxH * 0.10f;
            if (barW < 6.f) barW = 6.f;
            if (barW > 14.f) barW = 14.f;
            float barCX = boxLeft - gap - barW * 0.5f;
            if (hpFrac > 0.005f) {
                int hr, hg, hb; HpGradient(hpFrac, hr, hg, hb);
                float fillH = boxH * hpFrac;
                float fillY = boxTop + (boxH - fillH);
                DrawLineColored(env, espView, canvas, 255, hr, hg, hb, barW,
                                barCX, fillY, barCX, fillY + fillH);
            }
        }
        if (!skipExtra && g_espDistance.load() && s.hasValidPos) {
            float dx = s.position.x - g_localWorldX.load();
            float dy = s.position.y - g_localWorldY.load();
            float dist = sqrtf(dx*dx + dy*dy) * 0.05f;
            char buf[32]; snprintf(buf, sizeof(buf), "%.1fm", dist);
            const float fontSize = 28.0f;
            float estWidth = strlen(buf) * fontSize * 0.58f;
            float textX = boxLeft + boxW * 0.5f - estWidth * 0.5f;
            float textY = boxBottom + 16.0f + fontSize;
            jstring jn = env->NewStringUTF(buf);
            if (jn) {
                env->CallVoidMethod(espView, g_espDrawText, canvas, jn, textX, textY,
                                    255, 255, 255, 255, fontSize);
                env->DeleteLocalRef(jn);
            }
        }
        
    }
}


enum {
    M_WPN_HIGH_MELEE_DMG = 0, M_WPN_HIGH_MELEE_LEN,
    M_ENM_REMOVE_ROBOT, M_ENM_ROBOTS_CANT_SEE,
    M_MOD_COUNT
};
static void RegisterAllMods() {
    static bool done = false; if (done) return; done = true;
    RegisterMod(OBFUSCATE("Weapon_HighMeleeDamage"),  Off::Weapon_getMeleeDamage,          OBFUSCATE("E7 03 00 E3 1E FF 2F E1"));
    RegisterMod(OBFUSCATE("Weapon_HighMeleeLength"),  Off::Weapon_getMeleeLength,          OBFUSCATE("E7 03 00 E3 1E FF 2F E1")); 
    RegisterMod(OBFUSCATE("Enemy_RemoveRobot"),       Off::HumanoidDrone_updateStep, OBFUSCATE("1E FF 2F E1"));
    RegisterMod(OBFUSCATE("Enemy_RobotsCantSee"),     Off::Enemy_canSeeTarget,       OBFUSCATE("00 00 A0 E3 1E FF 2F E1"));
    
}
static int ModIdxForFeature(int feat) {
    switch (feat) {
        case 406: return M_WPN_HIGH_MELEE_DMG;
        case 407: return M_WPN_HIGH_MELEE_LEN;
        case 601: return M_ENM_REMOVE_ROBOT;
        case 602: return M_ENM_ROBOTS_CANT_SEE;
        default:  return -1;
    }
}
jobjectArray GetFeatureList(JNIEnv* env, jobject) {
    RegisterAllMods();
    jobjectArray ret;
    const char* features[] = {

    // --- Player Features ---
    OBFUSCATE("Category_Player"),
    OBFUSCATE("10_ButtonOnOff_Max Level"),
    OBFUSCATE("20_ButtonOnOff_God Mode"), // Renamed from No Local Damage
    OBFUSCATE("222_Toggle_Fly Speed Hack"), // Moved from Extras
    OBFUSCATE("223_SeekBar_Speed Multiplier_1_20"), 

    // --- Weapon Features ---
    OBFUSCATE("Category_Weapon"),
    OBFUSCATE("200_Toggle_Unlimited Ammo"),
    OBFUSCATE("201_Toggle_Multi Shot"),
    OBFUSCATE("202_SeekBar_Bullet Per Fire_1_30"),
    OBFUSCATE("203_Toggle_Fast Reload"),
    OBFUSCATE("204_Toggle_Max Range"),
    OBFUSCATE("205_Toggle_Bullet Speed Boost"),
    OBFUSCATE("206_SeekBar_Bullet Speed Multiplier_1_20"),
    OBFUSCATE("207_Toggle_Pick Gun Dual"),
    OBFUSCATE("209_Toggle_High Damage"),
    OBFUSCATE("210_SeekBar_Damage Multiplier_1_20"),
    OBFUSCATE("211_Toggle_No Recoil"),
    OBFUSCATE("406_Toggle_High Damage Melee"),
    OBFUSCATE("407_Toggle_High Melee Length"),

    // --- Aim Features ---
    OBFUSCATE("Category_Aim"),
    OBFUSCATE("109_Toggle_Silent Aim"),
    OBFUSCATE("111_Toggle_Auto Fire"),
    OBFUSCATE("116_Toggle_Aim Magnet"),
    OBFUSCATE("120_Toggle_Show FOV"),
    OBFUSCATE("121_SeekBar_FOV Size_60_350"),

    // --- ESP / Visuals ---
    OBFUSCATE("Category_ESP"),
    OBFUSCATE("100_Toggle_Enable ESP"),
    OBFUSCATE("101_Toggle_Draw Box"),
    OBFUSCATE("102_Toggle_Draw Line"),
    OBFUSCATE("103_Toggle_Show Health"),
    OBFUSCATE("104_Toggle_Show Distance"),
    OBFUSCATE("105_Toggle_Enemy Only"),
    OBFUSCATE("106_SeekBar_Line Thickness_1_10"),
    OBFUSCATE("107_SeekBar_Box Size_85_150"),
    OBFUSCATE("108_ColorPicker_ESP Color_#00FF88"),

    // --- Camera & View ---
    OBFUSCATE("Category_Camera"),
    OBFUSCATE("221_Toggle_Custom Zoom"),
    OBFUSCATE("224_SeekBar_Zoom Level_1_11"),

    // --- Flight Features ---
    OBFUSCATE("Category_Flight"),
    OBFUSCATE("500_Toggle_Unlimited Flying Power"),
    OBFUSCATE("502_Toggle_Fly Through Walls"),

    // --- Teleport Features ---
    OBFUSCATE("Category_Teleport"),
    OBFUSCATE("710_Toggle_Enable Teleport"),
    OBFUSCATE("713_TeleportPadWidget_"),

    // --- Unlock Features ---
    OBFUSCATE("Category_Unlock"),
    OBFUSCATE("230_Toggle_Unlock Weapons"),
    OBFUSCATE("231_Toggle_Bypass Upgrade"),
    OBFUSCATE("232_Toggle_Unlock Dual"),

    // --- Robot Features ---
    OBFUSCATE("Category_Robots"),
    OBFUSCATE("601_Toggle_Remove Robots"),
    OBFUSCATE("602_Toggle_Blind Robots"),

    // --- Performance Optimization ---
    OBFUSCATE("Category_Performance"),
    OBFUSCATE("300_Toggle_Anti-Lag Mode (30Hz ESP)"),
    OBFUSCATE("301_SeekBar_ESP Update Rate (Hz)_10_60"),
    OBFUSCATE("302_Toggle_Skip Extra Draw (HP/Distance)"),
    OBFUSCATE("303_Toggle_Throttle Aim Search"),
    OBFUSCATE("306_Toggle_Low-End Mode (All Performance)"),
    OBFUSCATE("307_Button_Reset Performance Defaults")
};
    int n = (int)(sizeof features / sizeof features[0]);
    ret = (jobjectArray) env->NewObjectArray(n,
        env->FindClass(OBFUSCATE("java/lang/String")), env->NewStringUTF(""));
    for (int i = 0; i < n; i++)
        env->SetObjectArrayElement(ret, i, env->NewStringUTF(features[i]));
    return ret;
}

void Changes(JNIEnv*, jclass, jobject, jint featNum, jstring, jint value, jlong, jboolean boolean, jstring) {
    if (!g_libReady.load()) return;
    InstallHooksIfNeeded();
    if (featNum >= 400 && featNum <= 612) {
        int idx = ModIdxForFeature(featNum);
        if (idx >= 0) { ApplyModByIndex(idx, boolean); return; }
    }
    switch (featNum) {
        case 10: ApplyMaxLevelPatch(boolean); break;
        case 20: ApplyReloadPatch(boolean);   break;

        case 100:
            g_espEnabled = boolean;
            if (boolean) RefreshDesignSize();
            else if (!IsModActive()) {
                ClearAllState();
                g_localInstance.store(nullptr);
                g_localSeen.store(false); g_localDead.store(false);
            }
            break;
        case 101: g_espBox = boolean; break;
        case 102: g_espLine = boolean; break;
        case 103: g_espHealth = boolean; break;
        case 104: g_espDistance = boolean; break;
        case 105: g_espEnemyOnly = boolean; break;
        case 106: { if (value < 1) value = 1; if (value > 10) value = 10; g_espBoxWidth = value; } break;
        case 107: g_boxSizeMul = value; break;
        case 108: g_espColorArgb.store((unsigned int)value); RecomputeSkyColors(); break;
        case 109:
            g_silentAim = boolean;
            if (boolean) RefreshDesignSize();
            else if (!IsModActive()) {
                g_hasAimTarget.store(false);
                g_hasAimAngle.store(false);
                g_stickyTarget = nullptr;
                g_currentAimTarget.store(nullptr);
            }
            break;
        case 111: g_autoFire = boolean; break;
        case 113: g_rangeBoost = boolean; break;
        case 115: { if (value < 1) value = 1; if (value > 20) value = 20; g_weaponSpeedMul = value; } break;
        case 116: g_aimMagnet = boolean; break;
        case 120: g_drawFovCircle = boolean; break;
        case 121: { if (value > 350) value = 350; if (value < 60) value = 60; g_fovPixels = value; } break;

                // ===== Teleport (pad widget inside menu) =====
        case 710: {
            bool on = boolean;
            g_tpPadEnabled.store(on);
            if (!on) g_teleportActive.store(false);
            traceLog("TELEPORT enable=%d", (int)on);
        } break;
        
        case 713: /* pad widget handled in Java */ break;

        case 200: g_wpnUnlimitedAmmo = boolean; break;
        case 201: g_wpnMultiShot = boolean; break;
        case 202: { if (value < 1) value = 1; if (value > 30) value = 30; g_wpnBulletsPerFire = value; } break;
        case 203: g_wpnFastReload = boolean; break;
        case 204: g_wpnMaxRange = boolean; break;
        case 205: g_wpnBulletSpeedUp = boolean; break;
        case 206: { if (value < 1) value = 1; if (value > 20) value = 20; g_wpnBulletSpeedMul = value; } break;
        case 207: g_dualWieldAll = boolean; traceLog("DUAL prompt toggle = %d", (int)boolean); break;     
        case 209: g_wpnHighDamage = boolean; break;
        case 210: { if (value < 1) value = 1; if (value > 20) value = 20; g_wpnDamageMul = value; } break;
        case 211: g_wpnNoRecoil = boolean; break;
        case 500: g_unlimitedFlyPower = boolean; break;
        case 502: g_flyThroughWalls = boolean; traceLog("WALLS toggle = %d", (int)boolean); break;  
        case 221: g_wpnZoomSelect = boolean; break;
        case 224: { if (value < 1) value = 1; if (value > 11) value = 11; g_wpnZoomLevel = value; } break;
        case 222: g_charSpeedOn = boolean; break;
        case 223: { if (value < 1) value = 1; if (value > 20) value = 20; g_charSpeedMul = value; } break;
        case 230: g_wpnUnlockAll = boolean; break;
        case 231: g_wpnMaxUpgrade = boolean; break;
        case 232: g_wpnDualWieldUnlock = boolean; break;

        case 300: g_lagAntiLagMode = boolean; break;
        case 301: { if (value < 10) value = 10; if (value > 60) value = 60; g_lagEspUpdateHz = value; } break;
        case 302: g_lagSkipExtraDraw = boolean; break;
        case 303: g_lagThrottleAim = boolean; break;
        case 306:
            g_lagAntiLagMode = boolean;
            g_lagSkipExtraDraw = boolean;
            g_lagThrottleAim = boolean;
            g_lagEspUpdateHz.store(boolean ? 20 : 60);
            break;
        case 307:
            g_lagAntiLagMode.store(false);
            g_lagEspUpdateHz.store(60);
            g_lagSkipExtraDraw.store(false);
            g_lagThrottleAim.store(false);
            break;
    }
}

// ==================================================================
// Entry
// ==================================================================
ElfScanner g_il2cppELF;
void* hack_thread(void*) {
    int waitCount = 0;
    do { sleep(1); waitCount++; g_il2cppELF = ElfScanner::createWithPath(targetLibName); }
    while (!g_il2cppELF.isValid() && waitCount < 60);
    if (!g_il2cppELF.isValid()) { crashLog("BOOT", "lib not found"); return nullptr; }
    g_libBase = g_il2cppELF.base();
    g_libReady.store(true);
    crashLog("BOOT", "lib base=%p", (void*)g_libBase);
    sleep(3);
    InstallHooksIfNeeded();
    crashLog("BOOT", "All hooks installed — ready");
    return nullptr;
}
__attribute__((constructor))
void lib_main() {
    pthread_t ptid;
    pthread_create(&ptid, NULL, hack_thread, NULL);
}