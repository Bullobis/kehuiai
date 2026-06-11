# 🖼️ 可绘AI (KeHuiAI)

📱 本地 AI 图像与视频生成应用 - Android

[![Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)]()
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-purple.svg)]()

## ✨ 核心功能

| 功能 | 说明 |
|------|------|
| 🖼️ **图像生成** | Z-Image、SDXL、SD 1.5、Flux 等模型 |
| 🎬 **视频生成** | 文生视频、图生视频、视频超分 |
| 📦 **模型管理** | 一键下载管理 AI 模型 |
| ⚡ **性能优化** | NPU 加速 (骁龙芯片) + CPU 回退 |

## 🔧 技术栈

- **Kotlin** + Jetpack Compose
- **Material Design 3** 界面
- **Navigation Compose** 导航
- **ViewModel** + **StateFlow** 状态管理
- **NDK** 本地推理引擎

## 📋 系统要求

- Android 8.0 (API 26) 或更高版本
- 推荐：高通骁龙 865 / 870 / 888 / 8 Gen 系列
- 最低：6GB RAM，10GB 存储空间

## 🚀 快速开始

### 下载 APK

从 [Releases](https://github.com/Bullobi/kuaihuiai/releases) 页面下载：

| 版本 | 说明 |
|------|------|
| `*-safe.apk` | 安全版 - 默认启用安全过滤 |
| `*-unsafe.apk` | 非安全版 - 无限制生成 |

### 构建源码

```bash
# 克隆仓库
git clone https://github.com/Bullobi/kuaihuiai.git
cd kuaihuiai

# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK
./gradlew assembleRelease
```

## 🎨 内置模型

### Z-Image
阿里巴巴通义实验室 Tongyi-MAI 开源图像生成模型

| 版本 | 大小 | 说明 |
|------|------|------|
| 标准版 | ~4GB | 基础图像生成 |
| 专业版 | ~7GB | 高质量图像生成 |
| HD | ~8GB | 2K 高清图像生成 |

## 📁 项目结构

```
app/
├── src/main/
│   ├── java/comkuaihuiai/
│   │   ├── ui/          # Compose 界面
│   │   ├── service/    # 业务服务
│   │   ├── navigation/  # 导航配置
│   │   └── utils/       # 工具类
│   ├── cpp/            # NDK C++ 代码
│   └── res/            # 资源文件
└── build.gradle        # Gradle 配置
```

## 📄 许可证

本项目基于 **Apache License 2.0** 开源。

详细信息请参阅 [LICENSE](LICENSE) 文件。

---

**Star ⭐ 支持一下！**
