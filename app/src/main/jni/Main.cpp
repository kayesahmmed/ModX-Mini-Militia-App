// ================================================================
// Mini Militia — Main.cpp v113.0
//  - Teleport: real physics body write (safe, no SIGBUS)
//  - Dual hook: Soldier + CollisionObject getBodyPosition
//  - Dual Wield: pickup-prompt only (no auto-convert)
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
    __android_log_print(ANDROID_LOG_INFO, LOG_TAG, "=== MMMod v113.0 boot ===");
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
// OFFSETS
// ==================================================================
namespace Off {
    // Weapon
    constexpr uintptr_t Weapon_getRandomFiringAngle     = 0x00f40ad0;
    constexpr uintptr_t Weapon_getBulletSpeed           = 0x00f40ab0;
    constexpr uintptr_t Weapon_getRange                 = 0x00f40784;
    constexpr uintptr_t Weapon_setFireAngle             = 0x00f40b5c;
    constexpr uintptr_t Weapon_getDamage                = 0x00f4076c;
    constexpr uintptr_t Weapon_getRoundsPerFire         = 0x00f40aa0;
    constexpr uintptr_t Weapon_getAmmo                  = 0x00f406bc;
    constexpr uintptr_t Weapon_setAmmo                  = 0x00f40720;
    constexpr uintptr_t Weapon_subAmmo                  = 0x00f40884;
    constexpr uintptr_t Weapon_getClip                  = 0x00f406dc;
    constexpr uintptr_t Weapon_setClip                  = 0x00f40730;
    constexpr uintptr_t Weapon_getClipCapacity          = 0x00f40794;
    constexpr uintptr_t Weapon_getAmmoCapacity          = 0x00f4078c;
    constexpr uintptr_t Weapon_getReloadTime            = 0x00f4079c;
    constexpr uintptr_t Weapon_isDualWield              = 0x00f40ab8;
    constexpr uintptr_t Weapon_isDualWieldOnly          = 0x00f40ac0;
    constexpr uintptr_t Weapon_isDualWieldPrimaryOnly   = 0x00f40ac8;
    constexpr uintptr_t Weapon_pickupAsDual             = 0x00f40b4c;
    constexpr uintptr_t Weapon_setPickupAsDual          = 0x00f40b54;
    constexpr uintptr_t Weapon_getZoomLevel             = 0x00f40a90;
    constexpr uintptr_t Weapon_setZoomLevel             = 0x00f409d4;
    constexpr uintptr_t Weapon_applyMaxZoomScale        = 0x00f40a70;
    constexpr uintptr_t Weapon_getZoomScale             = 0x00f408f4;
    constexpr uintptr_t Weapon_getMeleeDamage           = 0x00f40774;
    constexpr uintptr_t Weapon_getMeleeLength           = 0x00f4077c;

    // Map / Walls
    constexpr uintptr_t MapManager_addStaticBodyShape   = 0x00eeb038;
    constexpr uintptr_t MapManager_addStaticBodyPoly    = 0x00eeac7c;
    constexpr uintptr_t MapManager_isCollisionTile      = 0x00eec664;
    constexpr uintptr_t MapManager_mapCollision         = 0x00eec9f0;
    constexpr uintptr_t MapManager_isBoundryTile        = 0x00eece3c;
    constexpr uintptr_t MapManager_getMaxPower          = 0x00eea748;
    constexpr uintptr_t MapManager_getGravityFactor     = 0x00eea740;

    // Effects
    constexpr uintptr_t EffectsManager_addExplosionAt   = 0x00eb1f20;
    constexpr uintptr_t EffectsManager_addGasCloudAt    = 0x00eb2360;

    // Projectiles
    constexpr uintptr_t ProjectileManager_addBullet     = 0x00f04b7c;
    constexpr uintptr_t ProjectileManager_addShell      = 0x00f052d8;
    constexpr uintptr_t ProjectileManager_addRocket     = 0x00f05008;
    constexpr uintptr_t ProjectileManager_addGrenade    = 0x00f04d58;
    constexpr uintptr_t ProjectileManager_addSaw        = 0x00f05750;
    constexpr uintptr_t ProjectileManager_addFlame      = 0x00f055a4;

    // Soldier
    constexpr uintptr_t SoldierController_getBodyPosition    = 0x00f13828;
    constexpr uintptr_t SoldierController_getHP              = 0x00f137d4;
    constexpr uintptr_t SoldierController_setHP              = 0x00f137e4;
    constexpr uintptr_t SoldierController_setAlive           = 0x00f13860;
    constexpr uintptr_t SoldierController_getSoldierView     = 0x00f13074;
    constexpr uintptr_t SoldierController_isDead             = 0x00f137f4;
    constexpr uintptr_t SoldierController_addDamage          = 0x00f135a8;
    constexpr uintptr_t SoldierController_getPrimaryWeapon   = 0x00f1321c;
    constexpr uintptr_t SoldierController_getSecondaryWeapon = 0x00f13224;
    constexpr uintptr_t SoldierController_getDualWeapon      = 0x00f1322c;
    constexpr uintptr_t SoldierController_getSideWeapon      = 0x00f13234;
    constexpr uintptr_t SoldierController_fire               = 0x00f1323c;
    constexpr uintptr_t SoldierController_setThrust          = 0x00f13044;

    // CollisionObject base — extra getBodyPosition
    constexpr uintptr_t CollisionObject_getBodyPosition      = 0x00eac428;

    constexpr uintptr_t SoldierLocalController_updateStep            = 0x00f14478;
    constexpr uintptr_t SoldierLocalController_addDamage             = 0x00f18c64;
    constexpr uintptr_t SoldierLocalController_activatePlayer        = 0x00f17bb4;
    constexpr uintptr_t SoldierLocalController_setPower              = 0x00f156b4;
    constexpr uintptr_t SoldierLocalController_switchPrimaryToDual   = 0x00f17760;
    constexpr uintptr_t SoldierLocalController_switchSecondaryToDual = 0x00f178b8;

    constexpr uintptr_t SoldierManager_getLocalController   = 0x00f1aa00;
    constexpr uintptr_t SoldierManager_updateRemoteSoldiers = 0x00f1a888;
    constexpr uintptr_t SoldierManager_updateStep           = 0x00f1a348;
    constexpr uintptr_t SoldierManager_spawnPlayer          = 0x00f1a618;
    constexpr uintptr_t SoldierManager_respawnPlayer        = 0x00f19f78;
    constexpr uintptr_t SoldierManager_getRespawnTime       = 0x00f1b24c;
    constexpr uintptr_t SoldierManager_isRespawning         = 0x00f1b254;

    constexpr uintptr_t SoldierRemoteController_updateStep = 0x00f1d620;
    constexpr uintptr_t SoldierAIController_updateStep     = 0x00f0fd80;
    constexpr uintptr_t EnemyManager_updateStep            = 0x00eb4d18;

    constexpr uintptr_t HumanoidDrone_addDamage            = 0x00edf408;
    constexpr uintptr_t HawkDrone_addDamage                = 0x00eddc44;
    constexpr uintptr_t WormDrone_addDamage                = 0x00f4b320;
    constexpr uintptr_t HumanoidDrone_updateStep           = 0x00edf000;
    constexpr uintptr_t HawkDrone_updateStep               = 0x00edd640;
    constexpr uintptr_t WormDrone_updateStep               = 0x00f4aaa8;

    // Model
    constexpr uintptr_t WeaponsModel_isUnlockable            = 0x01113a88;
    constexpr uintptr_t WeaponsModel_isUpgradable            = 0x01113a60;
    constexpr uintptr_t WeaponsModel_getDualWieldUnlockLevel = 0x01113984;

    // Stage
    constexpr uintptr_t Stage_update                 = 0x00f21938;
    constexpr uintptr_t NetworkMessageDispatcher_updatePeerDamage = 0x00ef5d60;
    constexpr uintptr_t NetworkManager_sendWeaponChange = 0x00ef3ec4;

    constexpr uintptr_t CCNode_convertToWorldSpaceAR = 0x00f88018;
    constexpr uintptr_t CCDirector_sharedDirector    = 0x00f8f5c4;
    constexpr uintptr_t CCDirector_getVisibleSize    = 0x00f90378;
    constexpr uintptr_t Joypad_getDirectionAngle     = 0x00ee284c;
    constexpr uintptr_t Joypad_getDirectionVector    = 0x00ee2aa0;
    constexpr uintptr_t SoldierView_setPlayerHealth  = 0x00f20960;
    constexpr uintptr_t SoldierView_getPlayerName    = 0x00f20994;
    constexpr uintptr_t CollisionObject_getTeamId    = 0x00eac4f0;

    // Trigger pulls
    constexpr uintptr_t AK47_triggerPull    = 0x00ea2e74;
    constexpr uintptr_t AA12_triggerPull    = 0x00ea2128;
    constexpr uintptr_t DEAGLE_triggerPull  = 0x00eacaf0;
    constexpr uintptr_t M16_triggerPull     = 0x00ee4298;
    constexpr uintptr_t MINIGUN_triggerPull = 0x00ee7334;
    constexpr uintptr_t EMP_triggerPull     = 0x00ead8d0;
    constexpr uintptr_t RG6_triggerPull     = 0x00f06640;
    constexpr uintptr_t M14_triggerPull     = 0x00ee3608;
    constexpr uintptr_t MAGNUM_triggerPull  = 0x00ee63ac;
    constexpr uintptr_t MP5_triggerPull     = 0x00ee90a0;
    constexpr uintptr_t TAVOR_triggerPull   = 0x00f2e8e4;
    constexpr uintptr_t TEC9_triggerPull    = 0x00f2f534;
    constexpr uintptr_t HUNTING_triggerPull = 0x00edf94c;
    constexpr uintptr_t SAWGUN_triggerPull  = 0x00f0afb8;
    constexpr uintptr_t SMAW_triggerPull    = 0x00f0cb2c;
    constexpr uintptr_t XM8_triggerPull     = 0x00f4b834;
    constexpr uintptr_t PHASR_triggerPull   = 0x00ef921c;

    // Other
    constexpr uintptr_t Enemy_canSeeTarget          = 0x00eb3940;
    constexpr uintptr_t Explosion_applyDamage       = 0x00eb7ac8;
    constexpr uintptr_t GasCloud_applyDamage        = 0x00ed5808;
    constexpr uintptr_t PlasmaBall_applyDamage      = 0x00f00b84;
    constexpr uintptr_t SAW_checkMapCollision       = 0x00f0a410;
    constexpr uintptr_t SAW_updateItemStep          = 0x00f0a2a8;
    constexpr uintptr_t ProxyMine_updateStep        = 0x00f05db8;
    constexpr uintptr_t ProxyMine_reset             = 0x00f05c98;
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
    if (!m.init) { m.patch = MemoryPatch::createWithHex(g_libBase + m.offset, m.patchHex); m.init = true; }
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

// Originals
MgrUpdateRemote_t      old_MgrUpdateRemote      = nullptr;
MgrUpdateStep_t        old_MgrUpdateStep        = nullptr;
MgrSpawnPlayer_t       old_MgrSpawnPlayer       = nullptr;
MgrRespawnPlayer_t     old_MgrRespawnPlayer     = nullptr;
RemoteUpdateStep_t     old_RemoteUpdateStep     = nullptr;
AIUpdateStep_t         old_AIUpdateStep         = nullptr;
EnemyMgrUpdateStep_t   old_EnemyMgrUpdateStep   = nullptr;
StageUpdate_t          old_StageUpdate          = nullptr;
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
getBodyPosition_t           old_collGetBody               = nullptr;   // CollisionObject base
addStaticShape_t            old_addStaticBodyShape        = nullptr;
addStaticPoly_t             old_addStaticBodyPoly         = nullptr;

// Direct call pointers
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
std::atomic<bool> g_espWeaponCount{true};

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
std::atomic<bool> g_wpnMaxZoom        {false};
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
std::atomic<bool> g_anyGunAsBomb      {false};
std::atomic<bool> g_anyBombAsGas      {false};
std::atomic<bool> g_anyGunAsGasGun    {false};
std::atomic<bool> g_anyGunAsRocket    {false};
std::atomic<bool> g_anyGunAsLaser     {false};
std::atomic<bool> g_flyThroughWalls   {false};
std::atomic<bool> g_bulletThroughWalls{false};
std::atomic<bool> g_respawnTimeMod    {false};

// Teleport
std::atomic<bool>  g_teleportActive{false};
std::atomic<float> g_teleportX{0.f};
std::atomic<float> g_teleportY{0.f};
std::atomic<bool>  g_teleportFollowAim{false};

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

// ==================================================================
// Teleport body-pointer discovery
// ==================================================================
static std::atomic<uintptr_t> g_bodyOffsetFromSelf{(uintptr_t)-1};
static std::atomic<int>       g_posOffsetInBody{-1};
static std::atomic<bool>      g_bodyDiscoveryDone{false};
static std::atomic<int>       g_gbpCallLogs{0};

// ==================================================================
// Small helpers
// ==================================================================
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

// Safe page-mapped check using mincore
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

// ==================================================================
// Wall hooks
// ==================================================================
bool isCollisionTile_Hook(void* self, cpVect pos) {
    if (g_flyThroughWalls.load()) return false;
    if (g_bulletThroughWalls.load() && tls_bulletRaycast) return false;
    return old_isCollisionTile ? old_isCollisionTile(self, pos) : false;
}
bool mapCollision_Hook(void* self, cpVect pos) {
    if (g_flyThroughWalls.load()) return false;
    if (g_bulletThroughWalls.load() && tls_bulletRaycast) return false;
    return old_mapCollision ? old_mapCollision(self, pos) : false;
}
bool isBoundryTile_Hook(void* self, cpVect pos) {
    if (g_flyThroughWalls.load()) return false;
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

// ==================================================================
// Effects hooks
// ==================================================================
void addExplosionAt_Hook(void* self, cpVect pos, float radius, void* str, int teamId, bool flag) {
    if (old_addExplosionAt) old_addExplosionAt(self, pos, radius, str, teamId, flag);
    if (g_anyBombAsGas.load() && fn_addGasCloudAt && PlausiblePtr(self)) {
        if (GUARD_ENTER()) { GUARD_SET(); fn_addGasCloudAt(self, pos, radius, str, teamId); GUARD_CLR(); }
        else GUARD_CLR();
    }
}

// ==================================================================
// Weapon hooks
// ==================================================================
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

// ---- Dual Wield: pickup-prompt only ----
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

// ==================================================================
// Respawn
// ==================================================================
void MgrRespawnPlayer_Hook(void* self) {
    if (old_MgrRespawnPlayer) old_MgrRespawnPlayer(self);
}
int getRespawnTime_Hook(void* self) {
    if (g_respawnTimeMod.load()) return 0;
    return old_getRespawnTime ? old_getRespawnTime(self) : 5;
}
int isRespawning_Hook(void* self) {
    if (g_respawnTimeMod.load()) return 0;
    return old_isRespawning ? old_isRespawning(self) : 0;
}

// ==================================================================
// Local update (char speed only, no auto-convert)
// ==================================================================
void soldierLocalUpdateStep_Hook(void* self, float dt, cpVect a, cpVect b, float c) {
    if (!PlausiblePtr(self)) {
        if (old_soldierLocalUpdateStep) old_soldierLocalUpdateStep(self, dt, a, b, c);
        return;
    }
    if (g_charSpeedOn.load()) {
        int mul = g_charSpeedMul.load();
        if (mul < 1) mul = 1; if (mul > 20) mul = 20;
        float f = (float)mul;
        a.x *= (double)f; a.y *= (double)f;
        b.x *= (double)f; b.y *= (double)f;
    }
    if (old_soldierLocalUpdateStep) old_soldierLocalUpdateStep(self, dt, a, b, c);
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
// Teleport — safe body-pointer discovery + write
// ==================================================================
static void TryDiscoverBodyPointer(void* self) {
    if (g_bodyDiscoveryDone.load()) return;
    if (!PlausiblePtr(self)) return;
    if (!fn_getBodyPosition) return;

    cpVect want{0, 0};
    if (GUARD_ENTER()) { GUARD_SET(); fn_getBodyPosition(&want, self); GUARD_CLR(); }
    else { GUARD_CLR(); return; }

    if (std::fabs(want.x) < 30.0 && std::fabs(want.y) < 30.0) return;

    traceLog("TELEPORT scan: self=%p want=(%.1f,%.1f)", self, want.x, want.y);

    uintptr_t selfAddr = (uintptr_t)self;

    // Scan first 64 bytes of self for cpBody* candidate
    for (int selfOff = 4; selfOff <= 60; selfOff += 4) {
        uintptr_t fieldAddr = selfAddr + selfOff;
        if (!IsAddressMapped(fieldAddr)) continue;

        void* cand = nullptr;
        if (GUARD_ENTER()) { GUARD_SET(); cand = *(void**)fieldAddr; GUARD_CLR(); }
        else { GUARD_CLR(); continue; }
        if (!PlausiblePtr(cand)) continue;

        uintptr_t cbase = (uintptr_t)cand;
        if (cbase & 0x7) continue;

        // Scan first 128 bytes of candidate for matching doubles
        for (int posOff = 0; posOff <= 120; posOff += 8) {
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

            if (std::fabs(px - want.x) < 1.0 && std::fabs(py - want.y) < 1.0) {
                g_bodyOffsetFromSelf.store((uintptr_t)selfOff);
                g_posOffsetInBody.store(posOff);
                g_bodyDiscoveryDone.store(true);
                traceLog("TELEPORT FOUND: body at self+0x%x, p at +0x%x", selfOff, posOff);
                return;
            }
        }
    }
    traceLog("TELEPORT scan: no body found yet, retry later");
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
    if (local && self == local) {
        if (!g_bodyDiscoveryDone.load()) {
            TryDiscoverBodyPointer(self);
        }
    }

    if (!g_teleportActive.load()) return;
    if (!local || self != local) return;

    float tx = g_teleportX.load();
    float ty = g_teleportY.load();

    uintptr_t selfOff = g_bodyOffsetFromSelf.load();
    int       posOff  = g_posOffsetInBody.load();

    if (selfOff != (uintptr_t)-1 && posOff >= 0) {
        uintptr_t fieldAddr = (uintptr_t)self + selfOff;
        if (IsAddressMapped(fieldAddr)) {
            void* body = nullptr;
            if (GUARD_ENTER()) { GUARD_SET(); body = *(void**)fieldAddr; GUARD_CLR(); }
            else GUARD_CLR();

            if (PlausiblePtr(body)) {
                uintptr_t pAddr = (uintptr_t)body + posOff;
                if ((pAddr & 0x7) == 0 &&
                    IsAddressMapped(pAddr) &&
                    IsAddressMapped(pAddr + 24)) {
                    if (GUARD_ENTER()) {
                        GUARD_SET();
                        *(double*)(pAddr)      = (double)tx;
                        *(double*)(pAddr + 8)  = (double)ty;
                        *(double*)(pAddr + 16) = 0.0;
                        *(double*)(pAddr + 24) = 0.0;
                        GUARD_CLR();
                    } else GUARD_CLR();
                }
            }
        }
    }

    // Always override return value
    out->x = (double)tx;
    out->y = (double)ty;
}

// CollisionObject base hook — redirect to same logic
void getBodyPosition_Coll_Hooked(cpVect* out, void* self) {
    if (old_collGetBody) old_collGetBody(out, self);
    if (!out) return;
    if (!g_teleportActive.load()) return;
    void* local = g_localInstance.load();
    if (!local || self != local) return;
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
    bool isMine = IsLocalPlayerWeapon(weapon) || IsFiredByLocalPlayer(pos);
    void* local = g_localInstance.load();
    int teamId = PlausiblePtr(local) ? SafeGetTeamId(local) : 0;

    if (isMine && g_anyGunAsRocket.load() && fn_addRocket) {
        if (GUARD_ENTER()) { GUARD_SET(); fn_addRocket(self, pos, rot, vel, weapon, false, strPtr); GUARD_CLR(); }
        else GUARD_CLR();
        return;
    }
    if (isMine && g_anyGunAsBomb.load() && fn_addGrenade) {
        if (GUARD_ENTER()) { GUARD_SET(); fn_addGrenade(self, pos, rot, vel, false, strPtr, teamId); GUARD_CLR(); }
        else GUARD_CLR();
        return;
    }
    if (isMine && g_anyGunAsGasGun.load() && fn_addGasCloudAt) {
        bool setHint = g_bulletThroughWalls.load();
        if (setHint) tls_bulletRaycast = 1;
        if (old_addBullet) old_addBullet(self, pos, rot, vel, weapon, ammoType, targetPos, strPtr);
        if (setHint) tls_bulletRaycast = 0;
        cpVect gpos = targetPos;
        if (fabs(gpos.x) < 0.01 && fabs(gpos.y) < 0.01) {
            gpos.x = pos.x + vel.x * 0.1;
            gpos.y = pos.y + vel.y * 0.1;
        }
        if (GUARD_ENTER()) { GUARD_SET(); fn_addGasCloudAt(self, gpos, 130.0f, strPtr, teamId); GUARD_CLR(); }
        else GUARD_CLR();
        return;
    }
    if (isMine && g_anyGunAsLaser.load()) {
        vel.x *= 20.0; vel.y *= 20.0;
        bool setHint = g_bulletThroughWalls.load();
        if (setHint) tls_bulletRaycast = 1;
        if (old_addBullet) old_addBullet(self, pos, rot, vel, weapon, ammoType, targetPos, strPtr);
        if (setHint) tls_bulletRaycast = 0;
        return;
    }
    bool setHint = g_bulletThroughWalls.load();
    if (setHint) tls_bulletRaycast = 1;
    if (old_addBullet) old_addBullet(self, pos, rot, vel, weapon, ammoType, targetPos, strPtr);
    if (setHint) tls_bulletRaycast = 0;
}

float getRandomFiringAngle_Hook(void* self) {
    if (g_wpnNoRecoil.load()) return 0.0f;
    if (g_silentAim.load()) return 0.0f;
    return old_getRandomFiringAngle ? old_getRandomFiringAngle(self) : 0.0f;
}
int getRange_Hook(void* self) {
    if (g_wpnMaxRange.load()) return 999999;
    if ((g_silentAim.load() || g_autoFire.load()) && g_rangeBoost.load()) return 999999;
    return old_getRange ? old_getRange(self) : 5000;
}
int getBulletSpeed_Hook(void* self) {
    int orig = old_getBulletSpeed ? old_getBulletSpeed(self) : 800;
    if (g_silentAim.load()) return orig;
    int mul = g_weaponSpeedMul.load();
    if (g_wpnBulletSpeedUp.load()) { int wmul = g_wpnBulletSpeedMul.load(); if (wmul > mul) mul = wmul; }
    if (mul < 1) mul = 1; if (mul > 20) mul = 20;
    if (orig > 50 && orig < 100000) {
        long long boosted = (long long)orig * (long long)mul;
        if (boosted > 100000) boosted = 100000;
        return (int)boosted;
    }
    return orig;
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
    int dmgInt = (int)damage; if (dmgInt <= 0) return;
    std::lock_guard<std::mutex> lock(g_soldierMutex);
    SoldierEntry& e = EnsureEntryLocked(self);
    int cached = e.lastKnownHP < 0 ? 100 : e.lastKnownHP;
    int est = cached - dmgInt; if (est < 0) est = 0;
    e.lastKnownHP = est; e.lastDamageMs = NowMs();
}
void LocalAddDamage_Hook(void* self, float damage, void* strPtr, int ammoType, bool flag) {
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
        void* prev = g_localInstance.load();
        if (prev != self || g_localDead.load()) {
            ClearAllState();
            g_localInstance.store(self);
            g_localInstanceSetMs.store(NowMs());
            g_localSeen.store(false); g_localDead.store(false);
            // Reset body discovery
            g_bodyDiscoveryDone.store(false);
            g_bodyOffsetFromSelf.store((uintptr_t)-1);
            g_posOffsetInBody.store(-1);
        }
        RefreshSoldierData(self);
    }
    if (old_LocalActivate) old_LocalActivate(self);
}

// ==================================================================
// Stage tick
// ==================================================================
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

// ==================================================================
// Soldier manager hooks
// ==================================================================
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
    if (g_respawnTimeMod.load() && g_localDead.load() && old_MgrSpawnPlayer && PlausiblePtr(self)) {
        uint64_t now = NowMs();
        uint64_t last = g_lastForceRespawnMs.load();
        if (now - last > 500) {
            g_lastForceRespawnMs.store(now);
            if (GUARD_ENTER()) { GUARD_SET(); old_MgrSpawnPlayer(self); GUARD_CLR(); }
            else GUARD_CLR();
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
#define SAFE_HOOK(off, hook, orig, flag) do { \
    uintptr_t _a = g_libBase + (off); \
    HOOK_ABS((void*)_a, hook, orig); \
    if (orig) (flag).store(true); \
} while(0)

static void InstallHooksIfNeeded() {
    if (!g_libReady.load()) return;
    static bool logged = false;
    if (!logged) { crashLog("HOOK", "Installing hooks base=%p", (void*)g_libBase); logged = true; }

    fn_getLocalController    = (getLocalController_t)(g_libBase + Off::SoldierManager_getLocalController);
    fn_getBodyPosition       = (getBodyPosition_t)   (g_libBase + Off::SoldierController_getBodyPosition);
    fn_getHP                 = (getHP_t)             (g_libBase + Off::SoldierController_getHP);
    fn_getTeamId             = (getTeamId_t)         (g_libBase + Off::CollisionObject_getTeamId);
    fn_getSoldierView        = (getSoldierView_t)    (g_libBase + Off::SoldierController_getSoldierView);
    fn_getPlayerName         = (getPlayerName_t)     (g_libBase + Off::SoldierView_getPlayerName);
    fn_isDead                = (isDead_t)            (g_libBase + Off::SoldierController_isDead);
    fn_convertToWorldSpaceAR = (convertToWorldSpaceAR_t)(g_libBase + Off::CCNode_convertToWorldSpaceAR);
    fn_directorShared        = (directorShared_t)    (g_libBase + Off::CCDirector_sharedDirector);
    fn_directorGetVisible    = (directorGetSize_t)   (g_libBase + Off::CCDirector_getVisibleSize);
    fn_getPrimaryWeapon      = (getWeapon_t)         (g_libBase + Off::SoldierController_getPrimaryWeapon);
    fn_getSecondaryWeapon    = (getWeapon_t)         (g_libBase + Off::SoldierController_getSecondaryWeapon);
    fn_getDualWeapon         = (getWeapon_t)         (g_libBase + Off::SoldierController_getDualWeapon);
    fn_getSideWeapon         = (getWeapon_t)         (g_libBase + Off::SoldierController_getSideWeapon);
    fn_soldierFire           = (soldierFire_t)       (g_libBase + Off::SoldierController_fire);
    fn_getBulletSpeed        = (getBulletSpeed_t)    (g_libBase + Off::Weapon_getBulletSpeed);
    fn_getRange              = (getRange_t)          (g_libBase + Off::Weapon_getRange);
    fn_setFireAngleWpn       = (setFireAngle_wpn_t)  (g_libBase + Off::Weapon_setFireAngle);
    fn_addShell              = (addShell_t)          (g_libBase + Off::ProjectileManager_addShell);
    fn_addRocket             = (addRocket_t)         (g_libBase + Off::ProjectileManager_addRocket);
    fn_addGrenade            = (addGrenade_t)        (g_libBase + Off::ProjectileManager_addGrenade);
    fn_addSaw                = (addSaw_t)            (g_libBase + Off::ProjectileManager_addSaw);
    fn_addFlame              = (addFlame_t)          (g_libBase + Off::ProjectileManager_addFlame);
    fn_addGasCloudAt         = (addGasCloudAt_t)     (g_libBase + Off::EffectsManager_addGasCloudAt);
    fn_setPowerF             = (setPowerF_t)         (g_libBase + Off::SoldierLocalController_setPower);
    fn_switchPrimaryToDual   = (switchToDual_t)      (g_libBase + Off::SoldierLocalController_switchPrimaryToDual);
    fn_switchSecondaryToDual = (switchToDual_t)      (g_libBase + Off::SoldierLocalController_switchSecondaryToDual);
    fn_setThrust             = (setThrust_t)         (g_libBase + Off::SoldierController_setThrust);

    g_aimResolved.store(true);
    RefreshDesignSize();

    if (!g_teleportHooksOk.load()) {
        SAFE_HOOK(Off::SoldierController_getBodyPosition, getBodyPosition_Hooked, old_getBodyPosition_hook, g_teleportHooksOk);
        // Extra: hook base CollisionObject::getBodyPosition
        {
            uintptr_t _a = g_libBase + Off::CollisionObject_getBodyPosition;
            HOOK_ABS((void*)_a, getBodyPosition_Coll_Hooked, old_collGetBody);
        }
        crashLog("HOOK", "Teleport hooks OK (2)");
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
}

// ==================================================================
// Simple patches
// ==================================================================
static MemoryPatch g_patchMaxLevel, g_patchNoLocalDamage;
static std::atomic<bool> g_maxLevelInit{false}, g_noLocalDamageInit{false};
static void ApplyMaxLevelPatch(bool e) {
    if (!g_libReady.load()) return;
    if (!g_maxLevelInit.load()) {
        g_patchMaxLevel = MemoryPatch::createWithHex(g_libBase + 0x011bae9c, "64 00 A0 E3 1E FF 2F E1");
        g_maxLevelInit.store(true);
    }
    if (e) g_patchMaxLevel.Modify(); else g_patchMaxLevel.Restore();
}
static void ApplyReloadPatch(bool e) {
    if (!g_libReady.load()) return;
    if (!g_noLocalDamageInit.load()) {
        g_patchNoLocalDamage = MemoryPatch::createWithHex(g_libBase + Off::SoldierLocalController_addDamage, "1E FF 2F E1");
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
    if (!std::isfinite(designW) || designW < 10.f) designW = 1280.f;
    if (!std::isfinite(designH) || designH < 10.f) designH = 720.f;
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
        if (g_espWeaponCount.load() && s.weaponCount > 0) {
            char wbuf[10];
            snprintf(wbuf, sizeof(wbuf), "x%d", s.weaponCount);
            jstring jw = env->NewStringUTF(wbuf);
            if (jw) {
                const float fontSize = 26.0f;
                float estW = strlen(wbuf) * fontSize * 0.58f;
                float wx = boxLeft + boxW * 0.5f - estW * 0.5f;
                float wy = boxTop - 6.f;
                int wr, wg, wb;
                if (s.weaponCount >= 2) { wr = 0;   wg = 255; wb = 100; }
                else                    { wr = 255; wg = 200; wb = 0;   }
                env->CallVoidMethod(espView, g_espDrawText, canvas, jw, wx, wy,
                                    255, wr, wg, wb, fontSize);
                env->DeleteLocalRef(jw);
            }
        }
    }
}

// ==================================================================
// Mod Registry
// ==================================================================
enum {
    M_WPN_NO_BULLET_SPREAD = 0, M_WPN_HIDE_WEAPONS,
    M_WPN_HIGH_MELEE_DMG, M_WPN_HIGH_MELEE_LEN,
    M_SPR_AK47, M_SPR_M16, M_SPR_MINIGUN, M_SPR_EMP, M_SPR_RG6, M_SPR_M14,
    M_SPR_MAGNUM, M_SPR_MP5, M_SPR_TAVOR, M_SPR_TEC9, M_SPR_AA12,
    M_SPR_HUNTING, M_SPR_SAWGUN, M_SPR_SMAW, M_SPR_XM8, M_SPR_PHASR, M_SPR_DEAGLE,
    M_ENM_REMOVE_ROBOT, M_ENM_ROBOTS_CANT_SEE,
    M_ENM_DIE_GUNS_ONLY1, M_ENM_DIE_GUNS_ONLY2, M_ENM_DIE_GUNS_ONLY3,
    M_ENM_HIDE_PROXY, M_ENM_ENDLESS_PROXY, M_ENM_ATTACH_PROXY,
    M_ENM_INFINITE_PROXY_THROW, M_ENM_ENDLESS_SAW, M_ENM_SAW_DAMAGE_REMOVE,
    M_MOD_COUNT
};
static void RegisterAllMods() {
    static bool done = false; if (done) return; done = true;
    RegisterMod("Weapon_NoBulletSpread",   Off::Weapon_getRandomFiringAngle,    "00 00 A0 E3 1E FF 2F E1");
    RegisterMod("Weapon_HideWeapons",      Off::NetworkManager_sendWeaponChange,"1E FF 2F E1");
    RegisterMod("Weapon_HighMeleeDamage",  Off::Weapon_getMeleeDamage,          "E7 03 00 E3 1E FF 2F E1");
    RegisterMod("Weapon_HighMeleeLength",  Off::Weapon_getMeleeLength,          "E7 03 00 E3 1E FF 2F E1");
    RegisterMod("Spray_AK47",    Off::AK47_triggerPull,    "1E FF 2F E1");
    RegisterMod("Spray_M16",     Off::M16_triggerPull,     "1E FF 2F E1");
    RegisterMod("Spray_MiniGun", Off::MINIGUN_triggerPull, "1E FF 2F E1");
    RegisterMod("Spray_EMP",     Off::EMP_triggerPull,     "1E FF 2F E1");
    RegisterMod("Spray_RG6",     Off::RG6_triggerPull,     "1E FF 2F E1");
    RegisterMod("Spray_M14",     Off::M14_triggerPull,     "1E FF 2F E1");
    RegisterMod("Spray_Magnum",  Off::MAGNUM_triggerPull,  "1E FF 2F E1");
    RegisterMod("Spray_MP5",     Off::MP5_triggerPull,     "1E FF 2F E1");
    RegisterMod("Spray_TAVOR",   Off::TAVOR_triggerPull,   "1E FF 2F E1");
    RegisterMod("Spray_TEC9",    Off::TEC9_triggerPull,    "1E FF 2F E1");
    RegisterMod("Spray_AA12",    Off::AA12_triggerPull,    "1E FF 2F E1");
    RegisterMod("Spray_Hunting", Off::HUNTING_triggerPull, "1E FF 2F E1");
    RegisterMod("Spray_SAWGun",  Off::SAWGUN_triggerPull,  "1E FF 2F E1");
    RegisterMod("Spray_SMAW",    Off::SMAW_triggerPull,    "1E FF 2F E1");
    RegisterMod("Spray_XM8",     Off::XM8_triggerPull,     "1E FF 2F E1");
    RegisterMod("Spray_PHASR",   Off::PHASR_triggerPull,   "1E FF 2F E1");
    RegisterMod("Spray_DEAGLE",  Off::DEAGLE_triggerPull,  "1E FF 2F E1");
    RegisterMod("Enemy_RemoveRobot",       Off::HumanoidDrone_updateStep, "1E FF 2F E1");
    RegisterMod("Enemy_RobotsCantSee",     Off::Enemy_canSeeTarget,       "00 00 A0 E3 1E FF 2F E1");
    RegisterMod("Enemy_DieByGunsOnly1",    Off::Explosion_applyDamage,    "1E FF 2F E1");
    RegisterMod("Enemy_DieByGunsOnly2",    Off::GasCloud_applyDamage,     "1E FF 2F E1");
    RegisterMod("Enemy_DieByGunsOnly3",    Off::PlasmaBall_applyDamage,   "1E FF 2F E1");
    RegisterMod("Enemy_HideFromProxy",     Off::ProxyMine_updateStep,     "1E FF 2F E1");
    RegisterMod("Enemy_EndlessProxy",      Off::ProxyMine_reset,          "00 00 A0 E1");
    RegisterMod("Enemy_AttachProxy",       Off::ProxyMine_reset,          "00 00 A0 E1");
    RegisterMod("Enemy_InfiniteProxyThrow",Off::ProxyMine_reset,          "00 00 A0 E1");
    RegisterMod("Enemy_EndlessSaw",        Off::SAW_updateItemStep,       "00 00 A0 E1");
    RegisterMod("Enemy_SawDamageRemove",   Off::SAW_checkMapCollision,    "00 00 A0 E3 1E FF 2F E1");
}
static int ModIdxForFeature(int feat) {
    if (feat >= 400 && feat <= 436) {
        static const int map[37] = {
            M_WPN_NO_BULLET_SPREAD, -1, M_WPN_HIDE_WEAPONS, -1, -1, -1,
            M_WPN_HIGH_MELEE_DMG, M_WPN_HIGH_MELEE_LEN, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            M_SPR_AK47, M_SPR_M16, M_SPR_MINIGUN, M_SPR_EMP, M_SPR_RG6,
            M_SPR_M14, M_SPR_MAGNUM, M_SPR_MP5, M_SPR_TAVOR, M_SPR_TEC9,
            M_SPR_AA12, M_SPR_HUNTING, M_SPR_SAWGUN, M_SPR_SMAW,
            M_SPR_XM8, M_SPR_PHASR, M_SPR_DEAGLE
        };
        return map[feat - 400];
    }
    if (feat >= 600 && feat <= 612) {
        static const int map[13] = {
            -1, M_ENM_REMOVE_ROBOT, M_ENM_ROBOTS_CANT_SEE,
            M_ENM_DIE_GUNS_ONLY1, M_ENM_DIE_GUNS_ONLY2, M_ENM_DIE_GUNS_ONLY3,
            -1, M_ENM_HIDE_PROXY, M_ENM_ENDLESS_PROXY, M_ENM_ATTACH_PROXY,
            M_ENM_INFINITE_PROXY_THROW, M_ENM_ENDLESS_SAW, M_ENM_SAW_DAMAGE_REMOVE
        };
        return map[feat - 600];
    }
    return -1;
}

// ==================================================================
// Menu
// ==================================================================
jobjectArray GetFeatureList(JNIEnv* env, jobject) {
    RegisterAllMods();
    jobjectArray ret;
    const char* features[] = {
        OBFUSCATE("Category_Main"),
        OBFUSCATE("10_ButtonOnOff_Max Level"),
        OBFUSCATE("20_ButtonOnOff_No Local Damage"),

        OBFUSCATE("Category_ESP"),
        OBFUSCATE("100_Toggle_ESP Enable"),
        OBFUSCATE("101_Toggle_ESP Box"),
        OBFUSCATE("102_Toggle_ESP Line"),
        OBFUSCATE("103_Toggle_ESP Health Bar"),
        OBFUSCATE("104_Toggle_ESP Distance"),
        OBFUSCATE("105_Toggle_ESP Enemy Only"),
        OBFUSCATE("106_SeekBar_Box & Line Thickness_1_10"),
        OBFUSCATE("107_SeekBar_Box Size %_85_150"),
        OBFUSCATE("108_ColorPicker_ESP Color_#00FF88"),
        OBFUSCATE("130_Toggle_Show Weapon Count (x1/x2)"),

        OBFUSCATE("Category_Aim"),
        OBFUSCATE("109_Toggle_Silent Aim"),
        OBFUSCATE("111_Toggle_Auto Fire"),
        OBFUSCATE("113_Toggle_Extended Range"),
        OBFUSCATE("115_SeekBar_Weapon Speed (x1-x20)_1_20"),
        OBFUSCATE("116_Toggle_Aim Magnet"),
        OBFUSCATE("120_Toggle_Draw FOV Circle"),
        OBFUSCATE("121_SeekBar_FOV Size (px)_60_350"),

        OBFUSCATE("Category_Teleport"),
        OBFUSCATE("700_Toggle_Teleport Freeze at X/Y"),
        OBFUSCATE("704_SeekBar_Teleport X (offset -5000..+5000)_0_100"),
        OBFUSCATE("705_SeekBar_Teleport Y (offset -5000..+5000)_0_100"),
        OBFUSCATE("701_Button_Snap to Aim Target"),
        OBFUSCATE("702_Button_Snap to Map Center (0,0)"),
        OBFUSCATE("703_Button_Cancel Teleport"),

        OBFUSCATE("Category_Weapon"),
        OBFUSCATE("200_Toggle_Unlimited Ammo (9999)"),
        OBFUSCATE("201_Toggle_Multi Shot"),
        OBFUSCATE("202_SeekBar_Bullets Per Fire_1_30"),
        OBFUSCATE("203_Toggle_Instant Reload"),
        OBFUSCATE("204_Toggle_Max Range (Infinite)"),
        OBFUSCATE("205_Toggle_Bullet Speed Boost"),
        OBFUSCATE("206_SeekBar_Bullet Speed Multiplier_1_20"),
        OBFUSCATE("207_Toggle_Any Gun Can Be Picked As Dual"),
        OBFUSCATE("208_Toggle_Max Zoom (Built-in)"),
        OBFUSCATE("209_Toggle_High Damage"),
        OBFUSCATE("210_SeekBar_Damage Multiplier_1_20"),
        OBFUSCATE("211_Toggle_No Recoil / Zero Spread"),

        OBFUSCATE("Category_Flight"),
        OBFUSCATE("500_Toggle_Unlimited Flying Power"),
        OBFUSCATE("502_Toggle_Fly Through Walls"),

        OBFUSCATE("Category_Gun Modes"),
        OBFUSCATE("411_Toggle_Any Gun As Bomb (Grenade AoE)"),
        OBFUSCATE("412_Toggle_Any Gun As Gas (Cloud)"),
        OBFUSCATE("413_Toggle_Any Gun As Rocket"),
        OBFUSCATE("414_Toggle_Any Gun As Laser"),
        OBFUSCATE("606_Toggle_Any Bomb As Gas"),
        OBFUSCATE("410_Toggle_Bullet Through Walls"),

        OBFUSCATE("Category_Player"),
        OBFUSCATE("510_Toggle_Respawn Time Mod (Instant)"),

        OBFUSCATE("Category_Weapon Extras"),
        OBFUSCATE("221_Toggle_Enable Custom Zoom"),
        OBFUSCATE("224_SeekBar_Custom Zoom Level (1x-11x)_1_11"),
        OBFUSCATE("222_Toggle_Character Speed Boost"),
        OBFUSCATE("223_SeekBar_Character Speed Multiplier_1_20"),
        OBFUSCATE("230_Toggle_Unlock All Weapons"),
        OBFUSCATE("231_Toggle_Max Upgrade Level Bypass"),
        OBFUSCATE("232_Toggle_Dual Wield Unlock (No Level Req)"),

        OBFUSCATE("Category_Weapon Mods"),
        OBFUSCATE("400_Toggle_No Bullet Spread"),
        OBFUSCATE("402_Toggle_Hide Your Weapons"),
        OBFUSCATE("406_Toggle_High Damage Melee"),
        OBFUSCATE("407_Toggle_High Melee Length"),

        OBFUSCATE("Category_Weapon Sprayers"),
        OBFUSCATE("420_Toggle_AK47 Sprayer"),
        OBFUSCATE("421_Toggle_M16 Sprayer"),
        OBFUSCATE("422_Toggle_MiniGun Sprayer"),
        OBFUSCATE("423_Toggle_EMP Sprayer"),
        OBFUSCATE("424_Toggle_RG6 (Mortar) Sprayer"),
        OBFUSCATE("425_Toggle_M14 Sprayer"),
        OBFUSCATE("426_Toggle_Magnum Sprayer"),
        OBFUSCATE("427_Toggle_MP5 Sprayer"),
        OBFUSCATE("428_Toggle_TAVOR Sprayer"),
        OBFUSCATE("429_Toggle_TEC9 Sprayer"),
        OBFUSCATE("430_Toggle_AA12 Sprayer"),
        OBFUSCATE("431_Toggle_HuntingPistol Sprayer"),
        OBFUSCATE("432_Toggle_SAWGun Sprayer"),
        OBFUSCATE("433_Toggle_SMAW Sprayer"),
        OBFUSCATE("434_Toggle_XM8 Sprayer"),
        OBFUSCATE("435_Toggle_PHASR Sprayer"),
        OBFUSCATE("436_Toggle_DEAGLE Sprayer"),

        OBFUSCATE("Category_Enemy / Robot"),
        OBFUSCATE("601_Toggle_Remove Robot"),
        OBFUSCATE("602_Toggle_Robots Can't See"),
        OBFUSCATE("603_Toggle_Die By Guns Only 1"),
        OBFUSCATE("604_Toggle_Die By Guns Only 2"),
        OBFUSCATE("605_Toggle_Die By Guns Only 3"),
        OBFUSCATE("607_Toggle_Hide From Proxy"),
        OBFUSCATE("608_Toggle_Endless Proxy"),
        OBFUSCATE("609_Toggle_Attach Proxy"),
        OBFUSCATE("610_Toggle_Infinite Proxy Throw"),
        OBFUSCATE("611_Toggle_Endless Saw"),
        OBFUSCATE("612_Toggle_Saw Damage Remove"),

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
        case 130: g_espWeaponCount = boolean; break;

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

        case 700:
            g_teleportActive.store(boolean);
            traceLog("TELEPORT freeze=%d X=%.1f Y=%.1f bodyScan=%d",
                     (int)boolean, g_teleportX.load(), g_teleportY.load(),
                     (int)g_bodyDiscoveryDone.load());
            break;
        case 701:
            if (g_hasAimTarget.load()) {
                float tx = g_aimTargetRawX.load(), ty = g_aimTargetRawY.load();
                if (std::isfinite(tx) && std::isfinite(ty)) {
                    g_teleportX.store(tx); g_teleportY.store(ty);
                    g_teleportActive.store(true);
                    traceLog("TELEPORT snapAim -> (%.1f,%.1f)", tx, ty);
                } else traceLog("TELEPORT snapAim FAILED non-finite");
            } else traceLog("TELEPORT snapAim FAILED no target");
            break;
        case 702:
            g_teleportX.store(0.f); g_teleportY.store(0.f);
            g_teleportActive.store(true);
            traceLog("TELEPORT snapCenter (0,0)");
            break;
        case 703:
            g_teleportActive.store(false);
            g_teleportFollowAim.store(false);
            traceLog("TELEPORT canceled");
            break;
        case 704:
            g_teleportX.store(-5000.f + (float)value * 100.f);
            traceLog("TELEPORT X = %.1f", g_teleportX.load());
            break;
        case 705:
            g_teleportY.store(-5000.f + (float)value * 100.f);
            traceLog("TELEPORT Y = %.1f", g_teleportY.load());
            break;

        case 200: g_wpnUnlimitedAmmo = boolean; break;
        case 201: g_wpnMultiShot = boolean; break;
        case 202: { if (value < 1) value = 1; if (value > 30) value = 30; g_wpnBulletsPerFire = value; } break;
        case 203: g_wpnFastReload = boolean; break;
        case 204: g_wpnMaxRange = boolean; break;
        case 205: g_wpnBulletSpeedUp = boolean; break;
        case 206: { if (value < 1) value = 1; if (value > 20) value = 20; g_wpnBulletSpeedMul = value; } break;
        case 207: g_dualWieldAll = boolean; traceLog("DUAL prompt toggle = %d", (int)boolean); break;
        case 208: g_wpnMaxZoom = boolean; break;
        case 209: g_wpnHighDamage = boolean; break;
        case 210: { if (value < 1) value = 1; if (value > 20) value = 20; g_wpnDamageMul = value; } break;
        case 211: g_wpnNoRecoil = boolean; break;

        case 500: g_unlimitedFlyPower = boolean; break;
        case 502: g_flyThroughWalls = boolean; traceLog("WALLS toggle = %d", (int)boolean); break;

        case 411: g_anyGunAsBomb = boolean;    traceLog("GUNMODE bomb=%d",    (int)boolean); break;
        case 412: g_anyGunAsGasGun = boolean;  traceLog("GUNMODE gas=%d",     (int)boolean); break;
        case 413: g_anyGunAsRocket = boolean;  traceLog("GUNMODE rocket=%d",  (int)boolean); break;
        case 414: g_anyGunAsLaser = boolean;   traceLog("GUNMODE laser=%d",   (int)boolean); break;
        case 606: g_anyBombAsGas = boolean; break;
        case 410: g_bulletThroughWalls = boolean; break;

        case 510: g_respawnTimeMod = boolean; break;

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