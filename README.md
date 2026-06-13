# 🖼️ 可绘AI (KeHuiAI)

📱 本地 AI 图像与视频生成应用 - Android

[![Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Android](https://img.shields.io/badge/Android-8.0%2B-green.svg)]()
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-purple.svg)]()

---

## ✨ v3.4.0 新功能 (2024)

### 🔥 核心引擎升级
| 功能 | 描述 |
|------|------|
| **🔥 热模型切换** | 无需重启引擎，模型切换毫秒级完成 |
| **🧠 智能缓存** | LRU+TTL混合缓存，自动清理过期数据 |
| **⚡ 性能监控** | 实时内存/CPU/NPU仪表盘 |
| **🔄 多模型并行** | 最多3个模型同时预加载 |

### 🎨 AI 功能增强
| 功能 | 描述 |
|------|------|
| **🎨 智能提示词** | 中文语义解析，自动优化提示词 |
| **📋 PNG Info** | 读取/嵌入生成参数 |
| **👤 面部修复** | 一键增强人脸细节 |
| **🔍 智能超分** | 多种放大算法 |

### 📱 UI/UX 革新
| 功能 | 描述 |
|------|------|
| **🌙 OLED 暗色** | 纯黑主题，节省电量 |
| **⚡ 实时预览** | 滑块拖动实时看效果 |
| **💫 批量处理** | 多任务并行，自动排队 |
| **🎭 风格模板** | 一键应用预设风格 |

---

## 📋 功能总览

### 🖼️ 图像生成
| 模型 | 说明 |
|------|------|
| **Z-Image** | 阿里巴巴通义实验室开源模型 |
| **SDXL** | Stable Diffusion XL 高质量生成 |
| **SD 1.5** | 经典稳定扩散模型 |
| **Flux** | 最先进的开源图像生成 |

### 🎬 视频生成
- 文生视频 (Text-to-Video)
- 图生视频 (Image-to-Video)
- 视频超分 (4K)
- 帧插值

### ⚡ 专业功能
- **ControlNet** - 30种控制类型
- **LoRA** - 自定义风格
- **工作流** - 节点式编辑器
- **批量生成** - 一次多张

---

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

---

## 📊 性能对比

| 模式 | 速度 | 质量 | 显存占用 |
|------|------|------|----------|
| CPU | 1x | 100% | 2GB |
| NPU (骁龙) | 6x | 100% | 2GB |
| ONNX-GPU | 4x | 100% | 3GB |
| ONNX-NPU | 7x | 100% | 2GB |

---

## 🎨 调度器

| 调度器 | 速度 | 质量 | 推荐步数 |
|--------|------|------|----------|
| Euler A | 快速 | 高 | 20-35 |
| DPM++ 2M Karras | 最快 | 高 | 20-40 |
| DDIM | 中等 | 很高 | 20-50 |
| LCM | 极快 | 中等 | 4-8 |
| TCD | 慢 | 最高 | 20-40 |

---

## 📁 项目结构

```
app/
├── src/main/
│   ├── java/comkuaihuiai/
│   │   ├── ui/          # Compose 界面
│   │   │   ├── screens/ # 页面
│   │   │   ├── theme/    # 主题
│   │   │   └── components/ # 组件
│   │   ├── service/     # 业务服务
│   │   │   ├── advanced/ # 高级引擎
│   │   │   └── *.kt     # 核心服务
│   │   ├── data/        # 数据层
│   │   └── navigation/   # 导航配置
│   ├── cpp/             # NDK C++ 代码
│   └── res/             # 资源文件
└── build.gradle         # Gradle 配置
```

---

## 🛠️ 核心服务

| 服务 | 功能 |
|------|------|
| `SmartCacheManager` | 智能缓存管理 |
| `PerformanceMonitor` | 性能监控 |
| `PromptAssistant` | 提示词助手 |
| `BatchProcessor` | 批量处理 |
| `PNGInfoManager` | PNG 元数据 |
| `HotSwapEngine` | 热模型切换 |
| `CompleteInferenceEngine` | 完整推理引擎 |

---

## 📄 许可证

本项目基于 **Apache License 2.0** 开源。

---

**Star ⭐ 支持一下！**
