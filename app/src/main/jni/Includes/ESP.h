// ================================================================
// ESP.h — Shared ESP data structures + extern declarations
//   Used by Main.cpp (single-file build) to hold the snapshot
//   struct and shared extern symbols. Rendering logic lives in
//   Main.cpp's Draw() function.
// ================================================================
#pragma once

#include <atomic>
#include <mutex>
#include <vector>
#include <string>
#include <jni.h>

// ================================================================
// ESPSoldier — one soldier snapshot, copied per frame.
// Safe to read from the render thread without any locks.
// ================================================================
struct ESPSoldier {
    void*       instance;                // unique SoldierController* id
    bool        isLocal;                 // true if this is the local player
    struct { float x, y, z; } position;  // world coordinates
    bool        hasValidPos;             // position is reliable
    float       screenX, screenY;        // cached screen coords (design space)
    bool        hasScreen;               // screen coords are reliable
    int         hp;                      // current HP (0 = dead)
    int         maxHP;                   // observed maximum HP
    bool        alive;                   // false after setAlive(false)
    int         teamId;                  // 0 = no team
    std::string name;                    // sanitized UTF-8 player name
};

// ================================================================
// Shared state — defined in Main.cpp, declared here for clarity
// ================================================================
extern std::vector<ESPSoldier> g_soldierSnapshots;   // per-frame snapshot list
extern std::mutex              g_soldierMutex;       // protects snapshots + map

// Master + per-feature toggles
extern std::atomic<bool> g_espEnabled;    // master ESP switch
extern std::atomic<bool> g_espBox;        // draw bounding box
extern std::atomic<bool> g_espLine;       // draw target line
extern std::atomic<bool> g_espHealth;     // draw HP bar
extern std::atomic<bool> g_espDistance;   // draw distance text
extern std::atomic<bool> g_espTeamOnly;   // hide teammates
extern std::atomic<bool> g_debugDot;      // draw debug crosshair dot

// Rendering parameters
extern std::atomic<int> g_espLineWidth;   // 1..10 px
extern std::atomic<int> g_boxSizeMul;     // box size multiplier * 100

// Local player state
extern std::atomic<int>   g_localTeam;      // local player team id
extern std::atomic<void*> g_localInstance;  // local SoldierController*
extern std::atomic<float> g_localWorldX;    // local player world X
extern std::atomic<float> g_localWorldY;    // local player world Y

// Cocos2d design resolution
extern std::atomic<float> g_designW;        // design width
extern std::atomic<float> g_designH;        // design height
extern std::atomic<bool>  g_designValid;    // resolved yet?

// ================================================================
// Cocos2d director callbacks (resolved in Main.cpp)
// ================================================================
struct MSSize { float w, h; };
typedef void*   (*directorShared_t)();
typedef MSSize  (*directorGetSize_t)(void*);

extern directorShared_t  fn_directorShared;       // CCDirector::sharedDirector
extern directorGetSize_t fn_directorGetVisible;   // CCDirector::getVisibleSize
