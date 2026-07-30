#include <android/log.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <jni.h>

#include <cstdarg>
#include <cstdio>
#include <cstring>
#include <mutex>
#include <string>
#include <cstdint>
#include <atomic>
#include <sys/mman.h>
#include <unistd.h>
#include <time.h>

#include "krkr_bytehook.h"

namespace {

constexpr const char* kTag = "KrkrBridge";
constexpr const char* kGetSceneSymbol = "_ZN12TVPMainScene11GetInstanceEv";
constexpr const char* kStartupSymbol = "_ZN12TVPMainScene11startupFromERKSs";
constexpr const char* kUpdateSymbol = "_ZN12TVPMainScene6updateEf";
constexpr const char* kConsoleLogUtf16Symbol = "_ZN3TJS13TVPConsoleLogEPKDs";
constexpr size_t kUiFormContainerOffset = 0x348;
constexpr size_t kUiFormCountVtableOffset = 0x238;

using GetScene = void* (*)();

// Forward declaration so LegacyCowString's constructor can increment the
// leak counter; the definition lives with the other globals below.
extern std::atomic<int> gLeakedCowStringCount;

// libgame was built against the old GNU libstdc++ copy-on-write string ABI.
// Its startupFrom symbol receives a one-word string object that points to the
// character data; the three words immediately before that data are length,
// capacity and reference count. Passing std::__ndk1::string here corrupts the
// engine, despite the same mangled "Ss" spelling in the game symbol.
//
// OWNERSHIP: startupFrom retains this legacy string object after returning.
// The KRKR process is short-lived (one launch per process invocation),
// so the one allocation per launch is intentionally leaked. A debug counter
// is tracked below for diagnostic visibility.
struct LegacyCowString {
    char* data = nullptr;

    explicit LegacyCowString(const std::string& value) {
        constexpr size_t kHeaderSize = sizeof(size_t) * 3;
        const size_t size = value.size();
        auto* header = static_cast<unsigned char*>(std::malloc(kHeaderSize + size + 1));
        if (header == nullptr) return;
        auto* words = reinterpret_cast<size_t*>(header);
        words[0] = size;
        words[1] = size;
        words[2] = 1;
        data = reinterpret_cast<char*>(header + kHeaderSize);
        std::memcpy(data, value.data(), size);
        data[size] = '\0';
        gLeakedCowStringCount.fetch_add(1, std::memory_order_relaxed);
    }

    // startupFrom retains this legacy string object after returning. The old
    // bridge intentionally leaked the one allocation per process launch; the
    // KRKR process is short-lived, so preserve that ownership contract.
    ~LegacyCowString() = default;

    LegacyCowString(const LegacyCowString&) = delete;
    LegacyCowString& operator=(const LegacyCowString&) = delete;
};

using StartupFrom = void (*)(void*, const LegacyCowString&);
using Update = void (*)(void*, float);
using UiFormCount = int (*)(void*);
using OpenFn = int (*)(const char*, int, ...);
using ConsoleLogUtf16 = void (*)(const char16_t*);

struct GameApi {
    std::string library;
    void* handle = nullptr;
    GetScene getScene = nullptr;
    StartupFrom startupFrom = nullptr;
    Update update = nullptr;
};

JavaVM* gVm = nullptr;
jclass gBridgeClass = nullptr;
jmethodID gConsoleLineMethod = nullptr;
std::mutex gMutex;
GameApi gGame;
std::string gPathPrefix;
KrkrByteHook gByteHook;
void* gOpenStub = nullptr;
void* gOpen64Stub = nullptr;
void* gConsoleLogStub = nullptr;
std::atomic<bool> gConsoleLogHookInstalled{false};
std::atomic<Update> gOriginalSceneUpdate{nullptr};
void** gSceneUpdateSlot = nullptr;
std::atomic<int> gLeakedCowStringCount{0};
std::atomic<bool> gGameReadyReported{false};
std::atomic<int> gLastLaunchReadiness{-1};
int64_t gFirstSceneUpdateNs = 0;
constexpr int64_t kMenuShrinkWaitNs = 1200LL * 1000LL * 1000LL;

bool supportedGameLibrary(const char* library) {
    return library != nullptr
            && (std::strcmp(library, "libgame.so") == 0
                    || std::strcmp(library, "libgame134.so") == 0
                    || std::strcmp(library, "libgame126.so") == 0);
}

bool isKrkrGameCaller(const char* callerPathName, void*) {
    return callerPathName != nullptr
            && (std::strstr(callerPathName, "libgame.so") != nullptr
                    || std::strstr(callerPathName, "libgame134.so") != nullptr
                    || std::strstr(callerPathName, "libgame126.so") != nullptr);
}

void notifyConsoleLine(const char16_t* text) {
    if (gVm == nullptr || gBridgeClass == nullptr || gConsoleLineMethod == nullptr || text == nullptr) return;
    size_t length = 0;
    while (text[length] != 0 && length < 4096) ++length;
    if (length == 0) return;

    JNIEnv* env = nullptr;
    bool attached = false;
    if (gVm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        if (gVm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
        attached = true;
    }
    jstring line = env->NewString(
            reinterpret_cast<const jchar*>(text),
            static_cast<jsize>(length));
    if (line != nullptr) {
        env->CallStaticVoidMethod(gBridgeClass, gConsoleLineMethod, line);
        env->DeleteLocalRef(line);
    }
    if (env->ExceptionCheck()) {
        env->ExceptionDescribe();
        env->ExceptionClear();
    }
    if (attached) gVm->DetachCurrentThread();
}

void hookedConsoleLogUtf16(const char16_t* text) {
    auto original = reinterpret_cast<ConsoleLogUtf16>(gByteHook.previous(gConsoleLogStub));
    if (original != nullptr) original(text);
    notifyConsoleLine(text);
}

bool installConsoleLogHook() {
    if (gConsoleLogHookInstalled.load()) return true;
    if (!gByteHook.load(kTag)) return false;
    if (gConsoleLogStub == nullptr) {
        gConsoleLogStub = gByteHook.hookPartial(
                isKrkrGameCaller,
                kConsoleLogUtf16Symbol,
                reinterpret_cast<void*>(hookedConsoleLogUtf16));
    }
    const bool installed = gConsoleLogStub != nullptr;
    gConsoleLogHookInstalled.store(installed);
    __android_log_print(
            installed ? ANDROID_LOG_INFO : ANDROID_LOG_WARN,
            kTag,
            "TVPConsoleLog UTF-16 hook %s stub=%p",
            installed ? "installed" : "unavailable",
            gConsoleLogStub);
    return installed;
}

std::string takeString(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

bool resolveGameLocked(const char* library) {
    if (!supportedGameLibrary(library)) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "unsupported game library: %s",
                            library == nullptr ? "(null)" : library);
        return false;
    }
    if (gGame.handle != nullptr) {
        if (gGame.library == library) return true;
        __android_log_print(ANDROID_LOG_ERROR, kTag, "refusing to switch game library %s -> %s",
                            gGame.library.c_str(), library);
        return false;
    }

    void* handle = dlopen(library, RTLD_NOW | RTLD_LOCAL);
    if (handle == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "dlopen %s failed: %s", library, dlerror());
        return false;
    }
    auto getScene = reinterpret_cast<GetScene>(dlsym(handle, kGetSceneSymbol));
    auto startupFrom = reinterpret_cast<StartupFrom>(dlsym(handle, kStartupSymbol));
    auto update = reinterpret_cast<Update>(dlsym(handle, kUpdateSymbol));
    if (getScene == nullptr || startupFrom == nullptr || update == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, kTag,
                            "required KRKR symbols missing in %s scene=%p startup=%p update=%p error=%s", library,
                            reinterpret_cast<void*>(getScene), reinterpret_cast<void*>(startupFrom),
                            reinterpret_cast<void*>(update), dlerror());
        dlclose(handle);
        return false;
    }
    gGame.library = library;
    gGame.handle = handle;
    gGame.getScene = getScene;
    gGame.startupFrom = startupFrom;
    gGame.update = update;
    __android_log_print(ANDROID_LOG_INFO, kTag, "initialized %s scene=%p startup=%p update=%p", library,
                        reinterpret_cast<void*>(getScene), reinterpret_cast<void*>(startupFrom),
                        reinterpret_cast<void*>(update));
    return true;
}

bool setWritablePointer(void** slot, void* value) {
    if (slot == nullptr) return false;
    const long pageSize = sysconf(_SC_PAGESIZE);
    if (pageSize <= 0) return false;
    const uintptr_t address = reinterpret_cast<uintptr_t>(slot);
    const uintptr_t page = address & ~static_cast<uintptr_t>(pageSize - 1);
    if (mprotect(reinterpret_cast<void*>(page), static_cast<size_t>(pageSize),
                 PROT_READ | PROT_WRITE) != 0) return false;
    *slot = value;
    __builtin___clear_cache(reinterpret_cast<char*>(slot), reinterpret_cast<char*>(slot + 1));
    return mprotect(reinterpret_cast<void*>(page), static_cast<size_t>(pageSize), PROT_READ) == 0;
}

void notifyGameReady() {
    if (gVm == nullptr) return;
    JNIEnv* env = nullptr;
    bool attached = false;
    if (gVm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        if (gVm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
        attached = true;
    }
    jclass bridge = env->FindClass("bridge/NativeBridge");
    if (bridge != nullptr) {
        jmethodID callback = env->GetStaticMethodID(bridge, "onKrkrGameReady", "()V");
        if (callback != nullptr) env->CallStaticVoidMethod(bridge, callback);
        env->DeleteLocalRef(bridge);
    }
    if (env->ExceptionCheck()) env->ExceptionClear();
    if (attached) gVm->DetachCurrentThread();
}

void hookedSceneUpdate(void* scene, float dt) {
    Update original = gOriginalSceneUpdate.load();
    if (original != nullptr) original(scene, dt);
    if (gGameReadyReported.load()) return;

    timespec now{};
    clock_gettime(CLOCK_MONOTONIC, &now);
    const int64_t nowNs = static_cast<int64_t>(now.tv_sec) * 1000LL * 1000LL * 1000LL + now.tv_nsec;
    if (gFirstSceneUpdateNs == 0) {
        // doStartup schedules this update immediately after calling
        // TVPGameMainMenu::shrinkWithTime(1.0f). Do not expose that menu while
        // its move/fade action is still running.
        gFirstSceneUpdateNs = nowNs;
        __android_log_print(ANDROID_LOG_INFO, kTag, "TVPMainScene first update; waiting for menu shrink");
        return;
    }
    if (nowNs - gFirstSceneUpdateNs < kMenuShrinkWaitNs) return;

    if (!gGameReadyReported.exchange(true)) {
        if (gSceneUpdateSlot != nullptr) {
            // Restore after the one-off transition window so the hook has no
            // steady-state per-frame cost.
            setWritablePointer(gSceneUpdateSlot, reinterpret_cast<void*>(original));
        }
        const int leakedCount = gLeakedCowStringCount.load(std::memory_order_relaxed);
        __android_log_print(ANDROID_LOG_INFO, kTag,
                            "TVPMainScene startup completed; menu shrink finished; leakedCowStrings=%d",
                            leakedCount);
        notifyGameReady();
    }
}

bool installGameReadySignal(void* scene) {
    if (scene == nullptr || gGame.update == nullptr) return false;
    if (gSceneUpdateSlot != nullptr) return true;
    auto** vtable = *reinterpret_cast<void***>(scene);
    if (vtable == nullptr) return false;
    for (size_t index = 0; index < 256; ++index) {
        if (vtable[index] != reinterpret_cast<void*>(gGame.update)) continue;
        gOriginalSceneUpdate.store(gGame.update);
        if (!setWritablePointer(&vtable[index], reinterpret_cast<void*>(hookedSceneUpdate))) {
            gOriginalSceneUpdate.store(nullptr);
            __android_log_print(ANDROID_LOG_ERROR, kTag, "TVPMainScene update vtable patch failed index=%zu", index);
            return false;
        }
        gSceneUpdateSlot = &vtable[index];
        gGameReadyReported.store(false);
        gFirstSceneUpdateNs = 0;
        __android_log_print(ANDROID_LOG_INFO, kTag, "TVPMainScene ready signal installed vtableIndex=%zu", index);
        return true;
    }
    __android_log_print(ANDROID_LOG_ERROR, kTag, "TVPMainScene update not found in vtable");
    return false;
}

bool isLaunchSceneReady(void* scene) {
    if (scene == nullptr) return false;
    auto* uiForms = *reinterpret_cast<void**>(reinterpret_cast<uintptr_t>(scene) + kUiFormContainerOffset);
    if (uiForms == nullptr) return false;
    auto** vtable = *reinterpret_cast<void***>(uiForms);
    if (vtable == nullptr) return false;
    auto count = reinterpret_cast<UiFormCount>(
            vtable[kUiFormCountVtableOffset / sizeof(*vtable)]);
    const int formCount = count(uiForms);
    const int previous = gLastLaunchReadiness.exchange(formCount);
    if (previous != formCount) {
        __android_log_print(ANDROID_LOG_INFO, kTag, "launch UI forms ready count=%d", formCount);
    }
    return formCount > 0;
}

bool pathMatchesPrefix(const char* path) {
    std::lock_guard<std::mutex> lock(gMutex);
    return path != nullptr && !gPathPrefix.empty()
            && std::strncmp(path, gPathPrefix.c_str(), gPathPrefix.size()) == 0;
}

int callOriginal(OpenFn original, const char* path, int flags, mode_t mode) {
    if (original == nullptr) return -1;
    if ((flags & O_CREAT) != 0) return original(path, flags, mode);
    return original(path, flags);
}

int callJavaOpen(const char* path, int flags) {
    if (gVm == nullptr || path == nullptr) return -1;
    JNIEnv* env = nullptr;
    bool attached = false;
    if (gVm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        if (gVm->AttachCurrentThread(&env, nullptr) != JNI_OK) return -1;
        attached = true;
    }

    int fd = -1;
    jclass clazz = env->FindClass("bridge/NativeBridge");
    if (clazz != nullptr) {
        jmethodID method = env->GetStaticMethodID(clazz, "open", "(Ljava/lang/String;I)I");
        if (method != nullptr) {
            jstring javaPath = env->NewStringUTF(path);
            if (javaPath != nullptr) {
                fd = env->CallStaticIntMethod(clazz, method, javaPath, flags);
                env->DeleteLocalRef(javaPath);
            }
        }
        env->DeleteLocalRef(clazz);
    }
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        fd = -1;
    }
    if (attached) gVm->DetachCurrentThread();
    return fd;
}

int hookOpenCommon(void* hookStub, const char* path, int flags, va_list arguments) {
    mode_t mode = 0;
    if ((flags & O_CREAT) != 0) mode = static_cast<mode_t>(va_arg(arguments, int));
    auto original = reinterpret_cast<OpenFn>(gByteHook.previous(hookStub));
    if (!pathMatchesPrefix(path)) return callOriginal(original, path, flags, mode);

    const int fd = callJavaOpen(path, flags);
    if (fd >= 0) return fd;
    return callOriginal(original, path, flags, mode);
}

int hookedOpen(const char* path, int flags, ...) {
    va_list arguments;
    va_start(arguments, flags);
    const int result = hookOpenCommon(gOpenStub, path, flags, arguments);
    va_end(arguments);
    return result;
}

int hookedOpen64(const char* path, int flags, ...) {
    va_list arguments;
    va_start(arguments, flags);
    const int result = hookOpenCommon(gOpen64Stub, path, flags, arguments);
    va_end(arguments);
    return result;
}

}  // namespace

extern "C" JNIEXPORT jboolean JNICALL
Java_bridge_NativeBridge_initialize(JNIEnv* env, jclass, jstring gameLibrary) {
    const std::string library = takeString(env, gameLibrary);
    bool resolved = false;
    {
        std::lock_guard<std::mutex> lock(gMutex);
        resolved = resolveGameLocked(library.c_str());
    }
    if (resolved) installConsoleLogHook();
    return resolved ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_bridge_NativeBridge_isConsoleLogHookInstalled(JNIEnv*, jclass) {
    return gConsoleLogHookInstalled.load() ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_bridge_NativeBridge_launch(JNIEnv* env, jclass, jstring gameLibrary, jstring path, jboolean useMaps) {
    const std::string library = takeString(env, gameLibrary);
    const std::string gamePath = takeString(env, path);
    if (gamePath.empty()) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "launch rejected: empty path");
        return JNI_FALSE;
    }

    GetScene getScene = nullptr;
    StartupFrom startupFrom = nullptr;
    {
        std::lock_guard<std::mutex> lock(gMutex);
        if (!resolveGameLocked(library.c_str())) return JNI_FALSE;
        getScene = gGame.getScene;
        startupFrom = gGame.startupFrom;
    }
    void* scene = getScene();
    if (scene == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "launch rejected: TVPMainScene unavailable");
        return JNI_FALSE;
    }
    // The original bridge accepted this argument but did not read it before
    // dispatching startupFrom. Preserve the ABI while making that explicit.
    (void) useMaps;
    LegacyCowString legacyPath(gamePath);
    if (legacyPath.data == nullptr) {
        __android_log_print(ANDROID_LOG_ERROR, kTag, "launch rejected: allocate path failed");
        return JNI_FALSE;
    }
    startupFrom(scene, legacyPath);
    const bool readySignal = installGameReadySignal(scene);
    __android_log_print(ANDROID_LOG_INFO, kTag, "started %s from %s readySignal=%d", library.c_str(), gamePath.c_str(), readySignal);
    return JNI_TRUE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_bridge_NativeBridge_isLaunchSceneReady(JNIEnv* env, jclass, jstring gameLibrary) {
    const std::string library = takeString(env, gameLibrary);
    GetScene getScene = nullptr;
    {
        std::lock_guard<std::mutex> lock(gMutex);
        if (!resolveGameLocked(library.c_str())) return JNI_FALSE;
        getScene = gGame.getScene;
    }
    return isLaunchSceneReady(getScene()) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT void JNICALL
Java_bridge_NativeBridge_interceptor(JNIEnv* env, jclass, jstring prefix) {
    const std::string value = takeString(env, prefix);
    std::lock_guard<std::mutex> lock(gMutex);
    gPathPrefix = value;
    __android_log_print(ANDROID_LOG_INFO, kTag, "SAF open prefix=%s", gPathPrefix.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_bridge_NativeBridge_relocate(JNIEnv*, jclass) {
    std::string library;
    {
        std::lock_guard<std::mutex> lock(gMutex);
        library = gGame.library;
        if (gPathPrefix.empty()) {
            __android_log_print(ANDROID_LOG_WARN, kTag, "SAF open hook skipped: empty prefix");
            return;
        }
    }
    if (library.empty() || !gByteHook.load(kTag)) return;

    if (gOpenStub == nullptr) {
        gOpenStub = gByteHook.hookPartial(isKrkrGameCaller, "open", reinterpret_cast<void*>(hookedOpen));
    }
    if (gOpen64Stub == nullptr) {
        gOpen64Stub = gByteHook.hookPartial(isKrkrGameCaller, "open64", reinterpret_cast<void*>(hookedOpen64));
    }
    __android_log_print((gOpenStub != nullptr || gOpen64Stub != nullptr) ? ANDROID_LOG_INFO : ANDROID_LOG_ERROR,
                        kTag, "SAF open hooks %s open=%p open64=%p", library.c_str(), gOpenStub, gOpen64Stub);
}

extern "C" JNIEXPORT jboolean JNICALL
Java_bridge_NativeBridge_write(JNIEnv* env, jclass, jstring path, jbyteArray bytes) {
    const std::string outputPath = takeString(env, path);
    if (outputPath.empty() || bytes == nullptr) return JNI_FALSE;
    FILE* file = std::fopen(outputPath.c_str(), "wb");
    if (file == nullptr) return JNI_FALSE;
    const jsize length = env->GetArrayLength(bytes);
    jbyte* data = env->GetByteArrayElements(bytes, nullptr);
    if (data == nullptr) {
        std::fclose(file);
        return JNI_FALSE;
    }
    const size_t written = std::fwrite(data, 1, static_cast<size_t>(length), file);
    env->ReleaseByteArrayElements(bytes, data, JNI_ABORT);
    const int closeResult = std::fclose(file);
    return (written == static_cast<size_t>(length) && closeResult == 0) ? JNI_TRUE : JNI_FALSE;
}

// JNI_OnLoad is invoked when libkrkr_bridge.so is loaded via System.loadLibrary("krkr_bridge")
// in Kirikiroid134/139 (KirikiroidLauncherBaseActivity.onLoadNativeLibraries). This must only
// happen in the :kirikiri2 process, where the engine module's classes are visible to the
// application class loader. Loading this library from a non-application context (e.g. an
// isolated native process) will cause FindClass("bridge/NativeBridge") to fail and JNI_OnLoad
// to return JNI_ERR.
extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    gVm = vm;
    // NativeBridge is also exported by the legacy inline-hook library. Bind
    // our bridge methods eagerly, before that library is dlopen'ed by the exit
    // guard, so JNI's lazy symbol lookup can never select the legacy bridge.
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK || env == nullptr) {
        return JNI_ERR;
    }
    jclass bridge = env->FindClass("bridge/NativeBridge");
    if (bridge == nullptr) {
        env->ExceptionClear();
        __android_log_print(ANDROID_LOG_ERROR, kTag,
                            "NativeBridge class unavailable for RegisterNatives; "
                            "ensure libkrkr_bridge.so is only loaded by the :kirikiri2 process "
                            "where the engine module's ClassLoader can resolve bridge/NativeBridge");
        return JNI_ERR;
    }
    gBridgeClass = reinterpret_cast<jclass>(env->NewGlobalRef(bridge));
    gConsoleLineMethod = env->GetStaticMethodID(
            bridge, "onKrkrConsoleLine", "(Ljava/lang/String;)V");
    if (gBridgeClass == nullptr || gConsoleLineMethod == nullptr) {
        env->ExceptionClear();
        if (gBridgeClass != nullptr) {
            env->DeleteGlobalRef(gBridgeClass);
            gBridgeClass = nullptr;
        }
        env->DeleteLocalRef(bridge);
        __android_log_print(ANDROID_LOG_ERROR, kTag, "KR console callback binding failed");
        return JNI_ERR;
    }
    JNINativeMethod methods[] = {
            {const_cast<char*>("initialize"), const_cast<char*>("(Ljava/lang/String;)Z"),
             reinterpret_cast<void*>(Java_bridge_NativeBridge_initialize)},
            {const_cast<char*>("isConsoleLogHookInstalled"), const_cast<char*>("()Z"),
             reinterpret_cast<void*>(Java_bridge_NativeBridge_isConsoleLogHookInstalled)},
            {const_cast<char*>("launch"), const_cast<char*>("(Ljava/lang/String;Ljava/lang/String;Z)Z"),
             reinterpret_cast<void*>(Java_bridge_NativeBridge_launch)},
            {const_cast<char*>("isLaunchSceneReady"), const_cast<char*>("(Ljava/lang/String;)Z"),
             reinterpret_cast<void*>(Java_bridge_NativeBridge_isLaunchSceneReady)},
            {const_cast<char*>("interceptor"), const_cast<char*>("(Ljava/lang/String;)V"),
             reinterpret_cast<void*>(Java_bridge_NativeBridge_interceptor)},
            {const_cast<char*>("relocate"), const_cast<char*>("()V"),
             reinterpret_cast<void*>(Java_bridge_NativeBridge_relocate)},
            {const_cast<char*>("write"), const_cast<char*>("(Ljava/lang/String;[B)Z"),
             reinterpret_cast<void*>(Java_bridge_NativeBridge_write)},
    };
    if (env->RegisterNatives(bridge, methods, sizeof(methods) / sizeof(methods[0])) != JNI_OK) {
        env->ExceptionClear();
        env->DeleteLocalRef(bridge);
        __android_log_print(ANDROID_LOG_ERROR, kTag, "RegisterNatives failed");
        return JNI_ERR;
    }
    env->DeleteLocalRef(bridge);
    __android_log_print(ANDROID_LOG_INFO, kTag, "NativeBridge methods bound explicitly");
    return JNI_VERSION_1_6;
}
