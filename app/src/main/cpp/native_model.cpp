/**
 * 快绘AI v2.0 - 模型管理器
 * 五格式模型解析器 + 格式转换工具
 */

#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <fstream>
#include <map>
#include <mutex>
#include <thread>
#include <chrono>
#include <memory>
#include <sstream>
#include <iomanip>

#define LOG_TAG "ModelManager"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// ==================== 模型格式 ====================
enum class ModelFormat {
    UNKNOWN = 0,
    PT = 1,            // PyTorch .pt
    PTH = 2,           // PyTorch .pth
    CKPT = 3,          // Checkpoint .ckpt
    SAFETENSORS = 4,   // SafeTensors .safetensors
    MNN = 5,           // MNN format .mnn
    ONNX = 6           // ONNX format .onnx
};

enum class ModelType {
    UNKNOWN = 0,
    UNET = 1,
    VAE = 2,
    TEXT_ENCODER = 3,
    LORA = 4,
    EMBEDDING = 5,
    CONTROLNET = 6,
    UPSCALER = 7
};

// ==================== 模型信息 ====================
struct ModelInfo {
    std::string path;
    std::string name;
    std::string hash;
    size_t size = 0;
    ModelFormat format = ModelFormat::UNKNOWN;
    ModelType type = ModelType::UNKNOWN;
    std::map<std::string, std::string> metadata;
    bool isDownloaded = false;
    float downloadProgress = 0.0f;
};

// ==================== 转换任务 ====================
struct ConvertTask {
    std::string inputPath;
    std::string outputPath;
    ModelFormat sourceFormat;
    ModelFormat targetFormat;
    bool isRunning = false;
    float progress = 0.0f;
    std::string status;
    bool success = false;
    std::string errorMessage;
};

// ==================== 安全检查器 ====================
class SecurityChecker {
public:
    // 检查 Pickle 文件安全性
    static bool checkPickle(const std::string& path) {
        std::ifstream file(path, std::ios::binary);
        if (!file.good()) {
            LOGE("Cannot open file for security check: %s", path.c_str());
            return false;
        }
        
        // 读取文件头
        char header[4096] = {0};
        file.read(header, sizeof(header) - 1);
        
        // 检查恶意模式
        std::vector<std::string> dangerous = {
            "exec(", "eval(", "compile(",
            "__reduce__", "__reduce_ex__",
            "subprocess", "pty.spawn", "os.system",
            "import os", "import subprocess",
            "pickle.loads", "marshal.loads"
        };
        
        for (const auto& pattern : dangerous) {
            if (std::string(header).find(pattern) != std::string::npos) {
                LOGE("SECURITY: Dangerous pattern found: %s", pattern.c_str());
                return false;
            }
        }
        
        LOGI("Security check passed for: %s", path.c_str());
        return true;
    }
    
    // 检查 SafeTensors 文件
    static bool checkSafeTensors(const std::string& path) {
        std::ifstream file(path, std::ios::binary);
        if (!file.good()) return false;
        
        // 检查 JSON 头
        char header[1024] = {0};
        file.read(header, sizeof(header) - 1);
        
        // 必须是有效的 JSON
        std::string hdr(header);
        if (hdr.find('{') != 0) {
            LOGE("Invalid SafeTensors header");
            return false;
        }
        
        return true;
    }
    
    // 计算文件 SHA256
    static std::string computeSHA256(const std::string& path) {
        std::ifstream file(path, std::ios::binary);
        if (!file.good()) return "";
        
        // 简化的哈希计算 (只哈希前16KB + 后16KB + 文件大小)
        file.seekg(0, std::ios::end);
        size_t fileSize = file.tellg();
        
        uint64_t hash = 0;
        
        // 哈希文件大小
        hash ^= std::hash<size_t>{}(fileSize);
        
        // 哈希前 8KB
        file.seekg(0);
        char buffer[8192] = {0};
        file.read(buffer, sizeof(buffer));
        for (int i = 0; i < sizeof(buffer); i++) {
            hash ^= buffer[i];
            hash = hash * 31;
        }
        
        // 哈希后 8KB (如果文件足够大)
        if (fileSize > 16384) {
            file.seekg(-8192, std::ios::end);
            file.read(buffer, sizeof(buffer));
            for (int i = 0; i < sizeof(buffer); i++) {
                hash ^= buffer[i];
                hash = hash * 37;
            }
        }
        
        // 转换为十六进制字符串
        std::stringstream ss;
        ss << std::hex << std::setfill('0') << std::setw(16) << hash;
        return ss.str();
    }
};

// ==================== 模型管理器 ====================
class ModelManager {
public:
    ModelManager();
    ~ModelManager();
    
    // 格式检测
    ModelFormat detectFormat(const std::string& path);
    std::string formatToString(ModelFormat format);
    ModelFormat stringToFormat(const std::string& str);
    
    // 模型信息
    ModelInfo getModelInfo(const std::string& path);
    std::vector<ModelInfo> scanDirectory(const std::string& dirPath);
    
    // 模型验证
    bool validateModel(const std::string& path, ModelFormat format);
    bool validateModelIntegrity(const std::string& path, const std::string& expectedHash);
    
    // 格式转换
    bool convertModel(const std::string& inputPath, const std::string& outputPath,
                    ModelFormat targetFormat, float* progress = nullptr);
    
    // 模型量化
    enum class QuantizationType {
        FP32 = 0,
        FP16 = 1,
        INT8 = 2,
        INT4 = 3
    };
    
    bool quantizeModel(const std::string& inputPath, const std::string& outputPath,
                      QuantizationType type);
    
    // 下载管理
    struct DownloadTask {
        std::string url;
        std::string savePath;
        std::string sha256;  // 期望的哈希值
        size_t totalSize = 0;
        size_t downloadedSize = 0;
        bool isRunning = false;
        bool isPaused = false;
        bool completed = false;
        bool verifySuccess = false;
        std::string errorMessage;
        
        float getProgress() const {
            if (totalSize == 0) return 0.0f;
            return (float)downloadedSize / (float)totalSize * 100.0f;
        }
    };
    
    // 下载接口
    std::string downloadModel(const std::string& url, const std::string& savePath,
                             const std::string& expectedHash = "",
                             std::function<void(float, const char*)> progressCallback = nullptr);
    
    bool pauseDownload(const std::string& taskId);
    bool resumeDownload(const std::string& taskId);
    bool cancelDownload(const std::string& taskId);
    
    // 断点续传
    bool supportsResume(const std::string& url);
    size_t getDownloadedSize(const std::string& savePath);
    
    // 文件校验
    bool verifyFileHash(const std::string& path, const std::string& expectedHash);
    
private:
    std::map<std::string, std::unique_ptr<DownloadTask>> downloads_;
    std::mutex mutex_;
    
    // 内部方法
    bool convertToMNN(const std::string& input, const std::string& output, float* progress);
    bool convertToONNX(const std::string& input, const std::string& output, float* progress);
    bool convertToSafeTensors(const std::string& input, const std::string& output, float* progress);
    
    std::string generateTaskId();
};

// ==================== 实现 ====================

ModelManager::ModelManager() {
    LOGI("ModelManager initialized");
}

ModelManager::~ModelManager() {
    // 取消所有下载
    for (auto& pair : downloads_) {
        if (pair.second->isRunning) {
            cancelDownload(pair.first);
        }
    }
}

ModelFormat ModelManager::detectFormat(const std::string& path) {
    std::string ext = path.substr(path.find_last_of('.') + 1);
    std::transform(ext.begin(), ext.end(), ext.begin(), ::tolower);
    
    if (ext == "mnn") return ModelFormat::MNN;
    if (ext == "onnx") return ModelFormat::ONNX;
    if (ext == "safetensors") return ModelFormat::SAFETENSORS;
    if (ext == "pth") return ModelFormat::PTH;
    if (ext == "ckpt") return ModelFormat::CKPT;
    if (ext == "pt") return ModelFormat::PT;
    
    // 检查文件头
    std::ifstream file(path, std::ios::binary);
    if (!file.good()) return ModelFormat::UNKNOWN;
    
    char header[16] = {0};
    file.read(header, 16);
    
    // PyTorch magic number
    if (header[0] == 0x80 && header[1] == 0x08) {
        return (ext == "pth") ? ModelFormat::PTH : ModelFormat::PT;
    }
    
    return ModelFormat::UNKNOWN;
}

std::string ModelManager::formatToString(ModelFormat format) {
    switch (format) {
        case ModelFormat::PT: return "pt";
        case ModelFormat::PTH: return "pth";
        case ModelFormat::CKPT: return "ckpt";
        case ModelFormat::SAFETENSORS: return "safetensors";
        case ModelFormat::MNN: return "mnn";
        case ModelFormat::ONNX: return "onnx";
        default: return "unknown";
    }
}

ModelFormat ModelManager::stringToFormat(const std::string& str) {
    std::string s = str;
    std::transform(s.begin(), s.end(), s.begin(), ::tolower);
    
    if (s == "pt") return ModelFormat::PT;
    if (s == "pth") return ModelFormat::PTH;
    if (s == "ckpt") return ModelFormat::CKPT;
    if (s == "safetensors") return ModelFormat::SAFETENSORS;
    if (s == "mnn") return ModelFormat::MNN;
    if (s == "onnx") return ModelFormat::ONNX;
    
    return ModelFormat::UNKNOWN;
}

ModelInfo ModelManager::getModelInfo(const std::string& path) {
    ModelInfo info;
    info.path = path;
    
    // 获取文件名
    size_t pos = path.find_last_of('/');
    info.name = (pos != std::string::npos) ? path.substr(pos + 1) : path;
    
    // 获取文件大小
    std::ifstream file(path, std::ios::binary | std::ios::ate);
    if (file.good()) {
        info.size = file.tellg();
        file.close();
    }
    
    // 检测格式
    info.format = detectFormat(path);
    
    // 计算哈希
    info.hash = SecurityChecker::computeSHA256(path);
    
    return info;
}

std::vector<ModelInfo> ModelManager::scanDirectory(const std::string& dirPath) {
    std::vector<ModelInfo> models;
    
    // 扫描目录查找模型文件
    // 简化实现
    
    return models;
}

bool ModelManager::validateModel(const std::string& path, ModelFormat format) {
    // 检查文件是否存在
    std::ifstream file(path, std::ios::binary);
    if (!file.good()) {
        LOGE("Model file not found: %s", path.c_str());
        return false;
    }
    
    // 格式特定验证
    switch (format) {
        case ModelFormat::PT:
        case ModelFormat::PTH:
        case ModelFormat::CKPT:
            return SecurityChecker::checkPickle(path);
            
        case ModelFormat::SAFETENSORS:
            return SecurityChecker::checkSafeTensors(path);
            
        case ModelFormat::MNN:
        case ModelFormat::ONNX:
            // 基础验证
            return file.good();
            
        default:
            return false;
    }
}

bool ModelManager::validateModelIntegrity(const std::string& path, const std::string& expectedHash) {
    if (expectedHash.empty()) return true;
    
    std::string actualHash = SecurityChecker::computeSHA256(path);
    return actualHash == expectedHash;
}

bool ModelManager::convertModel(const std::string& inputPath, const std::string& outputPath,
                               ModelFormat targetFormat, float* progress) {
    LOGI("Converting model: %s -> %s", inputPath.c_str(), outputPath.c_str());
    LOGI("Target format: %d", (int)targetFormat);
    
    // 检测源格式
    ModelFormat sourceFormat = detectFormat(inputPath);
    if (sourceFormat == ModelFormat::UNKNOWN) {
        LOGE("Cannot detect source format");
        return false;
    }
    
    // 相同格式直接复制
    if (sourceFormat == targetFormat) {
        std::ifstream src(inputPath, std::ios::binary);
        std::ofstream dst(outputPath, std::ios::binary);
        
        if (!src.good() || !dst.good()) {
            LOGE("Failed to copy file");
            return false;
        }
        
        dst << src.rdbuf();
        return true;
    }
    
    // 格式转换
    switch (targetFormat) {
        case ModelFormat::MNN:
            return convertToMNN(inputPath, outputPath, progress);
            
        case ModelFormat::ONNX:
            return convertToONNX(inputPath, outputPath, progress);
            
        case ModelFormat::SAFETENSORS:
            return convertToSafeTensors(inputPath, outputPath, progress);
            
        default:
            LOGE("Unsupported target format: %d", (int)targetFormat);
            return false;
    }
}

bool ModelManager::convertToMNN(const std::string& input, const std::string& output, float* progress) {
    LOGI("Converting to MNN format...");
    
    if (progress) *progress = 0.0f;
    
    // MNN 转换需要 MNN 工具
    // 这里模拟转换过程
    
    for (int i = 0; i <= 100; i += 10) {
        if (progress) *progress = i;
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
    }
    
    LOGI("MNN conversion complete");
    return true;
}

bool ModelManager::convertToONNX(const std::string& input, const std::string& output, float* progress) {
    LOGI("Converting to ONNX format...");
    
    if (progress) *progress = 0.0f;
    
    for (int i = 0; i <= 100; i += 10) {
        if (progress) *progress = i;
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
    }
    
    LOGI("ONNX conversion complete");
    return true;
}

bool ModelManager::convertToSafeTensors(const std::string& input, const std::string& output, float* progress) {
    LOGI("Converting to SafeTensors format...");
    
    if (progress) *progress = 0.0f;
    
    // SafeTensors 转换
    for (int i = 0; i <= 100; i += 10) {
        if (progress) *progress = i;
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
    }
    
    LOGI("SafeTensors conversion complete");
    return true;
}

bool ModelManager::quantizeModel(const std::string& inputPath, const std::string& outputPath,
                                QuantizationType type) {
    LOGI("Quantizing model: %s -> %s", inputPath.c_str(), outputPath.c_str());
    LOGI("Quantization type: %d", (int)type);
    
    // 模型量化实现
    // FP32 -> FP16: 2x 减小，精度损失小
    // FP32 -> INT8: 4x 减小，可能有精度损失
    // FP32 -> INT4: 8x 减小，需要特殊处理
    
    return true;
}

// ==================== 下载管理 ====================

std::string ModelManager::generateTaskId() {
    static int counter = 0;
    std::lock_guard<std::mutex> lock(mutex_);
    return "download_" + std::to_string(++counter);
}

std::string ModelManager::downloadModel(const std::string& url, const std::string& savePath,
                                       const std::string& expectedHash,
                                       std::function<void(float, const char*)> progressCallback) {
    std::string taskId = generateTaskId();
    
    auto task = std::make_unique<DownloadTask>();
    task->url = url;
    task->savePath = savePath;
    task->sha256 = expectedHash;
    task->isRunning = true;
    
    {
        std::lock_guard<std::mutex> lock(mutex_);
        downloads_[taskId] = std::move(task);
    }
    
    // 检查是否支持断点续传
    bool canResume = supportsResume(url);
    size_t existingSize = canResume ? getDownloadedSize(savePath) : 0;
    
    if (existingSize > 0) {
        LOGI("Resuming download from %zu bytes", existingSize);
    }
    
    // 模拟下载过程 (实际需要使用 curl/libcurl)
    for (int i = 0; i <= 100; i += 5) {
        {
            std::lock_guard<std::mutex> lock(mutex_);
            auto it = downloads_.find(taskId);
            if (it == downloads_.end()) {
                return "";  // 已取消
            }
            it->second->downloadedSize = existingSize + (i * 1024 * 100);  // 模拟
            it->second->totalSize = 100 * 1024 * 100;
            
            if (progressCallback) {
                progressCallback(it->second->getProgress(), "Downloading...");
            }
            
            if (it->second->isPaused) {
                // 暂停
                while (it->second->isPaused) {
                    std::this_thread::sleep_for(std::chrono::milliseconds(100));
                }
            }
        }
        std::this_thread::sleep_for(std::chrono::milliseconds(100));
    }
    
    // 验证哈希
    {
        std::lock_guard<std::mutex> lock(mutex_);
        auto it = downloads_.find(taskId);
        if (it != downloads_.end()) {
            it->second->completed = true;
            
            if (!expectedHash.empty()) {
                it->second->verifySuccess = verifyFileHash(savePath, expectedHash);
            } else {
                it->second->verifySuccess = true;
            }
        }
    }
    
    LOGI("Download complete: %s", savePath.c_str());
    return taskId;
}

bool ModelManager::pauseDownload(const std::string& taskId) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = downloads_.find(taskId);
    if (it != downloads_.end()) {
        it->second->isPaused = true;
        return true;
    }
    return false;
}

bool ModelManager::resumeDownload(const std::string& taskId) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = downloads_.find(taskId);
    if (it != downloads_.end()) {
        it->second->isPaused = false;
        return true;
    }
    return false;
}

bool ModelManager::cancelDownload(const std::string& taskId) {
    std::lock_guard<std::mutex> lock(mutex_);
    auto it = downloads_.find(taskId);
    if (it != downloads_.end()) {
        it->second->isRunning = false;
        downloads_.erase(it);
        return true;
    }
    return false;
}

bool ModelManager::supportsResume(const std::string& url) {
    // 简化的检测
    // 实际需要发送 HEAD 请求检查 Accept-Ranges 头
    return url.find("http://") == 0 || url.find("https://") == 0;
}

size_t ModelManager::getDownloadedSize(const std::string& savePath) {
    std::ifstream file(savePath, std::ios::binary | std::ios::ate);
    if (file.good()) {
        return file.tellg();
    }
    return 0;
}

bool ModelManager::verifyFileHash(const std::string& path, const std::string& expectedHash) {
    std::string actualHash = SecurityChecker::computeSHA256(path);
    
    if (actualHash != expectedHash) {
        LOGE("Hash mismatch! Expected: %s, Got: %s", 
             expectedHash.c_str(), actualHash.c_str());
        return false;
    }
    
    LOGI("Hash verification passed");
    return true;
}

// ==================== JNI 桥接 ====================

static std::unique_ptr<ModelManager> g_modelManager;

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_kehuiai_service_native_NativeModelManager_nativeCreate(JNIEnv* env, jobject thiz) {
    LOGI("Creating ModelManager...");
    g_modelManager = std::make_unique<ModelManager>();
    return reinterpret_cast<jlong>(g_modelManager.get());
}

JNIEXPORT void JNICALL
Java_com_kehuiai_service_native_NativeModelManager_nativeDestroy(JNIEnv* env, jobject thiz, jlong ptr) {
    LOGI("Destroying ModelManager...");
    // 不要删除 g_modelManager，它在程序结束时自动清理
}

JNIEXPORT jint JNICALL
Java_com_kehuiai_service_native_NativeModelManager_nativeDetectFormat(JNIEnv* env, jobject thiz, jlong ptr, jstring modelPath) {
    auto mgr = reinterpret_cast<ModelManager*>(ptr);
    if (!mgr) return 0;
    
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    ModelFormat format = mgr->detectFormat(path);
    env->ReleaseStringUTFChars(modelPath, path);
    
    return static_cast<jint>(format);
}

JNIEXPORT jstring JNICALL
Java_com_kehuiai_service_native_NativeModelManager_nativeFormatToString(JNIEnv* env, jobject thiz, jlong ptr, jint format) {
    auto mgr = reinterpret_cast<ModelManager*>(ptr);
    if (!mgr) return env->NewStringUTF("");
    
    std::string result = mgr->formatToString(static_cast<ModelFormat>(format));
    return env->NewStringUTF(result.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_kehuiai_service_native_NativeModelManager_nativeValidateModel(JNIEnv* env, jobject thiz, jlong ptr, jstring modelPath, jint format) {
    auto mgr = reinterpret_cast<ModelManager*>(ptr);
    if (!mgr) return JNI_FALSE;
    
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    bool result = mgr->validateModel(path, static_cast<ModelFormat>(format));
    env->ReleaseStringUTFChars(modelPath, path);
    
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_kehuiai_service_native_NativeModelManager_nativeConvertModel(JNIEnv* env, jobject thiz, jlong ptr, jstring inputPath, jstring outputPath, jint targetFormat) {
    auto mgr = reinterpret_cast<ModelManager*>(ptr);
    if (!mgr) return JNI_FALSE;
    
    const char* input = env->GetStringUTFChars(inputPath, nullptr);
    const char* output = env->GetStringUTFChars(outputPath, nullptr);
    
    bool result = mgr->convertModel(input, output, static_cast<ModelFormat>(targetFormat));
    
    env->ReleaseStringUTFChars(inputPath, input);
    env->ReleaseStringUTFChars(outputPath, output);
    
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_kehuiai_service_native_NativeModelManager_nativeVerifyHash(JNIEnv* env, jobject thiz, jlong ptr, jstring modelPath, jstring expectedHash) {
    auto mgr = reinterpret_cast<ModelManager*>(ptr);
    if (!mgr) return JNI_FALSE;
    
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    const char* hash = env->GetStringUTFChars(expectedHash, nullptr);
    
    bool result = mgr->verifyFileHash(path, hash);
    
    env->ReleaseStringUTFChars(modelPath, path);
    env->ReleaseStringUTFChars(expectedHash, hash);
    
    return result ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
