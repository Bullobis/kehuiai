# 可绘AI (KehuiAI)

一款功能强大的 Android AI 生成应用，支持图像生成、视频生成和高效的本地模型推理。

## 核心功能

### 1️⃣ 图像生成

支持多种先进的图像生成模型：

| 模型 | 类型 |
|------|------|
| Z-Image | 全新概念模型 |
| SDXL | 高质量细节 |
| SD 1.5 | 经典稳定 |
| Flux | 新兴强力模型 |

### 2️⃣ 视频生成

- 📹 **文生视频**：文字描述 → 动态视频
- 🖼️ **图生视频**：静态图片 → 视频动画
- 🔍 **视频超分**：提升视频分辨率

### 3️⃣ 模型管理

内置模型管理界面，支持：
- ⬇️ 下载模型
- 🔄 切换模型
- 🗑️ 删除模型

## 技术特性

| 特性 | 说明 |
|------|------|
| ⚡ NPU加速 | 骁龙芯片专用加速，效率提升显著 |
| 🔄 CPU回退 | 无NPU设备自动切换CPU |
| 🛡️ 安全/非安全 | safe版 = 签名校验，unsafe版 = 免校验 |
| 🎨 Material 3 | 现代化设计语言 |

## 技术栈

- **语言**：Kotlin + Jetpack Compose
- **UI框架**：Material Design 3
- **推理引擎**：NativeInferenceEngine（本地推理）
- **底层加速**：NDK（C++）
- **架构**：模块化多层结构

## 项目结构

```
KehuiAI/
├── app/                          # 主应用模块
├── core/
│   ├── ui/                       # UI组件库（Material 3）
│   └── models/                   # 数据模型定义
├── feature/
│   ├── image-generation/         # 图像生成功能
│   ├── video-generation/         # 视频生成功能
│   └── model-management/         # 模型管理功能
├── inference/
│   └── npu-engine/               # NPU推理引擎（C++/NDK）
├── build.gradle.kts              # 根项目配置
└── settings.gradle.kts           # 模块配置
```

## 快速开始

### 前置要求

- Android Studio Hedgehog 或更高版本
- Android SDK 34+
- NDK 26+
- Kotlin 1.9.20+

### 构建项目

```bash
# 克隆项目
git clone https://github.com/Bullobis/KehuiAI.git
cd KehuiAI

# 切换到开发分支
git checkout develop

# 使用 Gradle 构建
./gradlew build

# 安装到设备
./gradlew installDebug
```

## 开发路线图

- [ ] Phase 1: 核心UI框架 & 图像生成基础
- [ ] Phase 2: 视频生成功能集成
- [ ] Phase 3: NPU推理引擎优化
- [ ] Phase 4: 模型管理系统完善
- [ ] Phase 5: 性能优化 & 测试完成

## 许可证

MIT License - 详见 [LICENSE](LICENSE) 文件

## 贡献指南

欢迎提交 Issue 和 Pull Request！

---

**开发者**：Bullobis  
**最后更新**：2026-06-12
