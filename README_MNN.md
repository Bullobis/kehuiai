# KuaiHuiAI - Stable Diffusion Android App

## 项目状态

**APK 已构建成功！** 
- 路径：`app/build/outputs/apk/debug/app-debug.apk` (34MB)
- 包含 MNN 3.5.0 库（arm64-v8a 和 armeabi-v7a）
- 包含 Stable Diffusion 模型文件

## 原生推理库

当前 APK 包含 MNN 库，但**不包含原生推理库** (`libkuaihui_native.so`)。这是因为当前构建环境没有 NDK/CMake。

### 编译原生库

要在 Android 上运行真正的 MNN 推理，需要编译原生库：

1. **使用 Android Studio 打开项目**
2. **确保已安装 NDK** (从 Android Studio SDK Manager 安装)
3. **CMake 将自动编译** `app/src/main/cpp/src/kuaihui_native_engine.cpp`

### 备选方案

如果无法编译原生库，APK 仍可工作，但会使用 Java 实现的简化推理（性能较低）。

## 项目结构

```
KuaiHuiAI/
├── app/
│   ├── src/main/
│   │   ├── cpp/src/
│   │   │   └── kuaihui_native_engine.cpp  # MNN JNI 推理实现
│   │   ├── java/comkuaihuiai/service/native/
│   │   │   ├── 可绘推理引擎.kt        # Kotlin 推理引擎
│   │   │   ├── NativeMNNEngine.kt         # MNN 原生调用
│   │   │   └── NativeUtils.kt            # JNI 声明
│   │   ├── jniLibs/
│   │   │   ├── arm64-v8a/                 # MNN 64位库
│   │   │   └── armeabi-v7a/               # MNN 32位库
│   │   └── assets/models/mnn/             # SD 模型文件
│   └── CMakeLists.txt                      # CMake 构建配置
└── build.gradle
```

## 包含的组件

### MNN 模型
- `clip_skip_1.mnn` - CLIP 文本编码器
- `unet.mnn` - 扩散模型
- `vae_decoder.mnn` - VAE 解码器
- `vae_encoder.mnn` - VAE 编码器
- `tokenizer.json` - 分词器

### MNN 库
- `libMNN.so` - MNN 核心
- `libMNN_Express.so` - 表达式引擎
- `libMNN_CL.so` - OpenCL 支持
- `libMNN_Vulkan.so` - Vulkan 支持
- `libMNNOpenCV.so` - OpenCV 支持
- `libc++_shared.so` - C++ 运行时

## 功能

- ✅ 文生图 (txt2img)
- ✅ 图生图 (img2img) 
- ✅ 局部重绘 (inpainting)
- ✅ 多种采样器 (Euler, DPM, LCM 等)
- ✅ CFG 引导
- ✅ 模型管理

## 编译

```bash
# Debug 构建
./gradlew assembleDebug

# Release 构建
./gradlew assembleRelease
```

## 下一步

1. 在 Android Studio 中打开项目
2. 安装 NDK (如果未安装)
3. 编译原生库
4. 重新打包 APK
