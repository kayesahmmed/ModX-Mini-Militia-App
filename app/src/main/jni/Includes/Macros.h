// thanks to shmoo and joeyjurjens for the useful stuff under this comment.
#ifndef ANDROID_MOD_MENU_MACROS_H
#define ANDROID_MOD_MENU_MACROS_H

#include "KittyMemory/MemoryPatch.hpp"
#include "KittyMemory/KittyInclude.hpp"

#if defined(__aarch64__) //Compile for arm64 lib only
#include <And64InlineHook/And64InlineHook.hpp>
#else //Compile for armv7 lib only.
#include <Substrate/SubstrateHook.h>
#include <Substrate/CydiaSubstrate.h>
#endif

void hook(void *offset, void* ptr, void **orig)
{
#if defined(__aarch64__)
    A64HookFunction(offset, ptr, orig);
#else
    MSHookFunction(offset, ptr, orig);
#endif
}

// ============================================================
// HOOK macros — these are CORRECT in your file. Keep as-is.
// ============================================================
#define HOOK(lib, offset, ptr, orig) \
    hook((void *)getAbsoluteAddress(lib, offset), (void *)ptr, (void **)&orig)

#define HOOK_NO_ORIG(lib, offset, ptr) \
    hook((void *)getAbsoluteAddress(lib, offset), (void *)ptr, NULL)

// 🔥 NEW: Direct absolute-address hook (avoids getAbsoluteAddress dependency)
#define HOOK_ABS(addr, ptr, orig) \
    hook((void *)(addr), (void *)ptr, (void **)&orig)

#define HOOKSYM(lib, sym, ptr, org) \
    hook(dlsym(dlopen(lib, 4), sym), (void *)ptr, (void **)&org)

#define HOOKSYM_NO_ORIG(lib, sym, ptr) \
    hook(dlsym(dlopen(lib, 4), sym), (void *)ptr, NULL)

// ============================================================
// Patching
// ============================================================
void patchOffset(const char *libName, uint64_t offset, std::string hexBytes)
{
    ElfScanner g_il2cppELF = ElfScanner::createWithPath(libName);
    uintptr_t il2cppBase = g_il2cppELF.base();
    MemoryPatch patch = MemoryPatch::createWithHex(il2cppBase + offset, hexBytes);

    if (!patch.isValid())
    {
        LOGE(OBFUSCATE("Failing offset: 0x%llx, please re-check the hex you entered."), offset);
        return;
    }
    if (!patch.Modify())
    {
        LOGE(OBFUSCATE("Something went wrong while patching this offset: 0x%llx"), offset);
        return;
    }
}

void patchOffsetSym(uintptr_t offset, std::string hexBytes)
{
    MemoryPatch patch = MemoryPatch::createWithHex(offset, hexBytes);
    if (!patch.isValid())
    {
        LOGE(OBFUSCATE("Failing offset: 0x%llu, please re-check the hex you entered."), offset);
        return;
    }
    if (!patch.Modify())
    {
        LOGE(OBFUSCATE("Something went wrong while patching this offset: 0x%llu"), offset);
        return;
    }
}

#define PATCH(lib, offset, hex) patchOffset(lib, offset, hex)

// 🔥 FIXED: was broken (had stray parens)
#define PATCH_SYM(lib, sym, hex) \
    patchOffsetSym((uintptr_t)dlsym(dlopen(lib, 4), sym), hex)

#endif //ANDROID_MOD_MENU_MACROS_H
