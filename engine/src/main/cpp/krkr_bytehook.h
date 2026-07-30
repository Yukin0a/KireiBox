#pragma once

#include <android/log.h>
#include <dlfcn.h>

// Minimal dynamic adapter for the bundled ByteHook PLT interceptor. This is
// the same API family the former libkirikiroid3 open hook used, but it avoids
// calling the incompatible ShadowHook initialiser directly on Android 16.
class KrkrByteHook {
public:
    using Init = int (*)(int mode, bool debug);
    using HookSingle = void* (*)(const char* callerLibrary, const char* symbol,
                                 void* replacement, void* callback, void* callbackArg);
    using CallerFilter = bool (*)(const char* callerPathName, void* arg);
    using HookPartial = void* (*)(CallerFilter allowFilter, void* allowFilterArg,
                                  const char* callerPathName, const char* symbol, void* replacement,
                                  void* callback, void* callbackArg);
    using Previous = void* (*)(void* stub);

    bool load(const char* tag) {
        if (ready_) return true;
        handle_ = dlopen("libbytehook.so", RTLD_NOW | RTLD_LOCAL);
        if (handle_ == nullptr) {
            __android_log_print(ANDROID_LOG_ERROR, tag, "dlopen libbytehook.so failed: %s", dlerror());
            return false;
        }
        init_ = reinterpret_cast<Init>(dlsym(handle_, "bytehook_init"));
        hookSingle_ = reinterpret_cast<HookSingle>(dlsym(handle_, "bytehook_hook_single"));
        hookPartial_ = reinterpret_cast<HookPartial>(dlsym(handle_, "bytehook_hook_partial"));
        previous_ = reinterpret_cast<Previous>(dlsym(handle_, "bytehook_get_prev_func"));
        if (init_ == nullptr || hookSingle_ == nullptr || hookPartial_ == nullptr || previous_ == nullptr) {
            __android_log_print(ANDROID_LOG_ERROR, tag, "ByteHook API incomplete: %s", dlerror());
            return false;
        }
        // libbytehook is deliberately shipped as a native-only backend here;
        // its Java façade is a compatibility stub. Initialise the native
        // runtime explicitly before registering any PLT task.
        const int initResult = init_(0 /* MODE_AUTOMATIC */, false);
        if (initResult != 0) {
            __android_log_print(ANDROID_LOG_ERROR, tag, "bytehook_init failed: %d", initResult);
            return false;
        }
        ready_ = true;
        return true;
    }

    void* hookSingle(const char* library, const char* symbol, void* replacement) const {
        return ready_ ? hookSingle_(library, symbol, replacement, nullptr, nullptr) : nullptr;
    }

    // hook_single requires the loader's canonical absolute caller path. Game
    // packages are loaded by SONAME, so use ByteHook's caller filter to match
    // their PLT imports without guessing that private linker path.
    void* hookPartial(CallerFilter allowFilter, const char* symbol, void* replacement,
                      void* filterArg = nullptr) const {
        return ready_ ? hookPartial_(allowFilter, filterArg, nullptr, symbol, replacement, nullptr, nullptr)
                      : nullptr;
    }

    void* previous(void* stub) const { return ready_ && stub != nullptr ? previous_(stub) : nullptr; }

private:
    void* handle_ = nullptr;
    Init init_ = nullptr;
    HookSingle hookSingle_ = nullptr;
    HookPartial hookPartial_ = nullptr;
    Previous previous_ = nullptr;
    bool ready_ = false;
};
