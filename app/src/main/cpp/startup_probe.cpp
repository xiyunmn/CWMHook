#include <android/log.h>
#include <fcntl.h>
#include <jni.h>
#include <sys/stat.h>
#include <time.h>
#include <unistd.h>

#include <atomic>
#include <algorithm>
#include <cctype>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <string>

using NativeOnModuleLoaded = void (*)(const char* name, void* handle);

struct NativeAPIEntries {
    uint32_t version;
    void (*hook_func)(void* func, void* replace, void** backup);
    void (*unhook_func)(void* func);
};

namespace {

constexpr const char* kTag = "CWMHook.NativeStartupProbe";
constexpr const char* kTargetProcess = "com.kuangxiangciweimao.novel";
constexpr const char* kLogDir = "/data/user/0/com.kuangxiangciweimao.novel/files/cwmhook/logs";
constexpr const char* kLogPath =
    "/data/user/0/com.kuangxiangciweimao.novel/files/cwmhook/logs/native_startup_probe.log";
constexpr const char* kStartupOptimizePref =
    "/data/user/0/com.kuangxiangciweimao.novel/shared_prefs/cwmhook_startup_optimize.xml";
constexpr off_t kMaxLogBytes = 256 * 1024;

std::mutex g_log_mutex;
std::atomic<int> g_seen_library_count{0};
std::atomic<int> g_logged_library_count{0};
int64_t g_base_ms = 0;
std::atomic<bool> g_target_process{false};
std::atomic<bool> g_enabled{false};

int64_t now_ms() {
    timespec ts{};
    clock_gettime(CLOCK_BOOTTIME, &ts);
    return static_cast<int64_t>(ts.tv_sec) * 1000 + ts.tv_nsec / 1000000;
}

std::string read_small_file(const char* path, size_t max_bytes) {
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) {
        return {};
    }
    std::string result;
    result.resize(max_bytes);
    ssize_t count = read(fd, result.data(), max_bytes);
    close(fd);
    if (count <= 0) {
        return {};
    }
    result.resize(static_cast<size_t>(count));
    return result;
}

std::string process_name() {
    std::string cmdline = read_small_file("/proc/self/cmdline", 256);
    if (cmdline.empty()) {
        return {};
    }
    size_t end = cmdline.find('\0');
    if (end != std::string::npos) {
        cmdline.resize(end);
    }
    return cmdline;
}

bool pref_boolean_true(const std::string& xml, const char* key) {
    std::string needle = "name=\"";
    needle += key;
    needle += "\"";
    size_t pos = xml.find(needle);
    if (pos == std::string::npos) {
        return false;
    }
    size_t end = xml.find("/>", pos);
    std::string tag = xml.substr(pos, end == std::string::npos ? 160 : end - pos);
    return tag.find("value=\"true\"") != std::string::npos;
}

bool startup_probe_enabled() {
    std::string pref = read_small_file(kStartupOptimizePref, 8192);
    return pref_boolean_true(pref, "enabled");
}

std::string lower_ascii(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char ch) {
        return static_cast<char>(std::tolower(ch));
    });
    return value;
}

std::string basename_of(const std::string& path) {
    size_t slash = path.find_last_of('/');
    if (slash == std::string::npos) {
        return path;
    }
    return path.substr(slash + 1);
}

bool contains_any(const std::string& value, const char* const* needles, size_t needle_count) {
    for (size_t i = 0; i < needle_count; ++i) {
        if (value.find(needles[i]) != std::string::npos) {
            return true;
        }
    }
    return false;
}

void ensure_log_dir() {
    mkdir("/data/user/0/com.kuangxiangciweimao.novel/files", 0700);
    mkdir("/data/user/0/com.kuangxiangciweimao.novel/files/cwmhook", 0700);
    mkdir(kLogDir, 0700);
}

void append_line(const std::string& message, bool require_enabled = true) {
    if (!g_target_process.load()) {
        return;
    }
    if (require_enabled && !g_enabled.load()) {
        return;
    }
    std::lock_guard<std::mutex> guard(g_log_mutex);
    ensure_log_dir();
    int flags = O_WRONLY | O_CREAT | O_APPEND | O_CLOEXEC;
    struct stat st {};
    if (stat(kLogPath, &st) == 0 && st.st_size > kMaxLogBytes) {
        flags = O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC;
    }
    int fd = open(kLogPath, flags, 0600);
    if (fd < 0) {
        __android_log_write(ANDROID_LOG_WARN, kTag, "failed to open native startup probe log");
        return;
    }
    int64_t now = now_ms();
    std::string line = std::to_string(now);
    line += " +";
    line += std::to_string(now - g_base_ms);
    line += "ms ";
    line += message;
    line += "\n";
    write(fd, line.data(), line.size());
    close(fd);
}

bool refresh_target_state() {
    if (g_target_process.load()) {
        return true;
    }
    if (process_name() != kTargetProcess) {
        return false;
    }
    g_target_process.store(true);
    g_enabled.store(startup_probe_enabled());
    std::string message = "native_probe_ready enabled=";
    message += g_enabled.load() ? "true" : "false";
    message += " process=";
    message += kTargetProcess;
    message += " pid=";
    message += std::to_string(getpid());
    append_line(message, false);
    return true;
}

bool should_log_library(const char* name) {
    if (name == nullptr || name[0] == '\0') {
        return false;
    }
    std::string lower = lower_ascii(name);
    std::string base = basename_of(lower);
    const char* path_needles[] = {
        "360",
        ".jiagu",
        "flass",
    };
    const char* library_needles[] = {
        "jiagu",
        "qihoo",
        "stub",
        "shell",
        "protect",
        "curl",
        "cwmhttps",
        "crypto",
        "ssl",
        "tnet",
        "ucrash",
        "umeng",
        "bugly",
        "dolphin",
        "droidsonroids",
        "webview",
        "chromium",
        "renderscript",
        "librs",
        "rs_internal",
        "rsdriver",
        "rscachedir",
        "gdt",
        "pangle",
        "bytedance",
        "toutiao",
        "kuaishou",
        "ksadsdk",
        "adcolony",
        "mintegral",
    };
    return contains_any(lower, path_needles, sizeof(path_needles) / sizeof(path_needles[0])) ||
        contains_any(base, library_needles, sizeof(library_needles) / sizeof(library_needles[0]));
}

void on_library_loaded(const char* name, void* handle) {
    (void)handle;
    if (!refresh_target_state() || !g_enabled.load()) {
        return;
    }
    int seen_index = g_seen_library_count.fetch_add(1) + 1;
    if (!should_log_library(name)) {
        return;
    }
    int logged_index = g_logged_library_count.fetch_add(1) + 1;
    std::string message = "library_loaded logged=";
    message += std::to_string(logged_index);
    message += " seen=";
    message += std::to_string(seen_index);
    message += " name=";
    message += name == nullptr ? "<null>" : name;
    append_line(message);
}

} // namespace

extern "C" __attribute__((visibility("default")))
NativeOnModuleLoaded native_init(const NativeAPIEntries* entries) {
    (void)entries;
    g_base_ms = now_ms();
    if (refresh_target_state()) {
        append_line("native_init process=com.kuangxiangciweimao.novel", false);
    }
    return on_library_loaded;
}

extern "C" jint JNI_OnLoad(JavaVM* vm, void* reserved) {
    (void)vm;
    (void)reserved;
    if (g_base_ms == 0) {
        g_base_ms = now_ms();
    }
    if (refresh_target_state()) {
        append_line("jni_onload java_load=true", false);
    }
    return JNI_VERSION_1_6;
}
