# 可绘AI v3.0 重构计划

## 一、重命名（"快绘AI" → "可绘AI"）
- [ ] 包名：`comkuaihuiai` → `com.kehui.ai`
- [ ] App名称：`快绘AI` → `可绘AI`
- [ ] 所有类名、变量名更新
- [ ] AndroidManifest 更新
- [ ] 资源文件更新

## 二、Z-Image 模型支持（阿里巴巴通义实验室）
- [ ] 添加 `BaseModelType.Z_IMAGE` 枚举
- [ ] 添加 Z-Image 模型下载器
- [ ] 添加 Z-Image 推理支持
- [ ] 更新 ModelRepository

## 三、视频生成功能
- [ ] 添加 `VideoGenerationParams` 数据类
- [ ] 创建 `VideoGenerationScreen` 界面
- [ ] 添加视频生成服务
- [ ] 添加 FFmpeg 视频处理
- [ ] 视频预览播放器

## 四、升级优化
- [ ] 线程安全强化
- [ ] 内存管理优化
- [ ] 错误恢复机制
- [ ] 新调度器支持
- [ ] 新版 ControlNet 支持
- [ ] LoRA+ 支持
- [ ] IP-Adapter 支持

## 五、架构升级
- [ ] MVVM 架构完善
- [ ] Repository 模式强化
- [ ] UseCase 层添加
- [ ] 状态管理优化

## 六、打包
- [ ] 安全版本（标准签名）
- [ ] 非安全版本（移除安全检查）

## Z-Image 模型信息
- 来源：Tongyi-MAI (阿里巴巴通义实验室)
- GitHub: https://github.com/Tongyi-MAI/Z-Image
- License: Apache-2.0
- Stars: 11500+
- 特点：开源图像生成模型
