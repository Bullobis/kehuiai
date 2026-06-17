/**
 * 工具函数
 */

#include <jni.h>
#include <android/log.h>
#include <string>
#include <vector>
#include <ctime>
#include <cstring>
#include <sstream>
#include <iomanip>

#define LOG_TAG "KuaiHuiUtils"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

// ==================== 工具函数 ====================

extern "C" {

// 获取当前时间戳
JNIEXPORT jlong JNICALL
Java_com_kehuiai_service_NativeUtils_nativeGetTimestamp(JNIEnv* env, jobject thiz) {
    return static_cast<jlong>(std::time(nullptr));
}

// 生成随机种子
JNIEXPORT jlong JNICALL
Java_com_kehuiai_service_NativeUtils_nativeGenerateSeed(JNIEnv* env, jobject thiz) {
    std::srand(std::time(nullptr));
    return static_cast<jlong>(std::rand());
}

// 字符串哈希
JNIEXPORT jint JNICALL
Java_com_kehuiai_service_NativeUtils_nativeHashString(JNIEnv* env, jobject thiz, jstring str) {
    const char* s = env->GetStringUTFChars(str, nullptr);
    int hash = 0;
    while (*s) {
        hash = hash * 31 + *s++;
    }
    env->ReleaseStringUTFChars(str, s);
    return hash;
}

// 拷贝像素数据
JNIEXPORT void JNICALL
Java_com_kehuiai_service_NativeUtils_nativeCopyPixels(JNIEnv* env, jobject thiz,
    jintArray srcPixels, jintArray dstPixels, jint count) {
    
    jint* src = env->GetIntArrayElements(srcPixels, nullptr);
    jint* dst = env->GetIntArrayElements(dstPixels, nullptr);
    
    std::memcpy(dst, src, count * sizeof(jint));
    
    env->ReleaseIntArrayElements(srcPixels, src, JNI_ABORT);
    env->ReleaseIntArrayElements(dstPixels, dst, 0);
}

// 获取设备信息
JNIEXPORT jstring JNICALL
Java_com_kehuiai_service_NativeUtils_nativeGetDeviceModel(JNIEnv* env, jobject thiz) {
    // 读取系统属性
    FILE* fp = fopen("/system/build.prop", "r");
    std::string model = "Unknown";
    
    if (fp) {
        char line[256];
        while (fgets(line, sizeof(line), fp)) {
            if (strncmp(line, "ro.product.model=", 17) == 0) {
                model = line + 17;
                model = model.substr(0, model.find('\n'));
                break;
            }
        }
        fclose(fp);
    }
    
    return env->NewStringUTF(model.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_kehuiai_service_NativeUtils_nativeGetDeviceManufacturer(JNIEnv* env, jobject thiz) {
    FILE* fp = fopen("/system/build.prop", "r");
    std::string manufacturer = "Unknown";
    
    if (fp) {
        char line[256];
        while (fgets(line, sizeof(line), fp)) {
            if (strncmp(line, "ro.product.manufacturer=", 23) == 0) {
                manufacturer = line + 23;
                manufacturer = manufacturer.substr(0, manufacturer.find('\n'));
                break;
            }
        }
        fclose(fp);
    }
    
    return env->NewStringUTF(manufacturer.c_str());
}

// 内存操作
JNIEXPORT jlong JNICALL
Java_com_kehuiai_service_NativeUtils_nativeGetAvailableMemory(JNIEnv* env, jobject thiz) {
    FILE* fp = fopen("/proc/meminfo", "r");
    long available = 0;
    
    if (fp) {
        char line[256];
        while (fgets(line, sizeof(line), fp)) {
            if (strncmp(line, "MemAvailable:", 13) == 0) {
                std::stringstream ss(line + 13);
                ss >> available;
                available *= 1024;  // KB to bytes
                break;
            }
        }
        fclose(fp);
    }
    
    return available;
}

} // extern "C"
