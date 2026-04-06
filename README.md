# Gemma AI - Android 端侧大语言模型应用

在 Android 手机上运行 Google Gemma 2B 量化模型的 AI 助手应用，使用 Google MediaPipe LLM Inference 进行端侧推理。

## 项目概述

本项目实现了一个完整的 Android 应用，可加载和运行 GGUF 格式的 Gemma 2B 量化模型（Q4_K_M），通过 Google MediaPipe LLM Inference 在端侧进行 LLM 推理。

```
技术栈:
  模型:     Google Gemma 2-2B Q4_K_M (GGUF)
  推理引擎: Google MediaPipe LLM Inference
  UI:       Kotlin + Jetpack Compose
  架构:     MVVM + StateFlow
```

## 项目结构

```
android-gemma-app/
├── app/
│   ├── src/main/
│   │   ├── java/com/example/gemmaapp/
│   │   │   ├── MainActivity.kt          # 入口 Activity
│   │   │   ├── InferenceEngine.kt       # MediaPipe LLM Inference 封装
│   │   │   ├── ChatViewModel.kt         # MVVM ViewModel
│   │   │   └── ui/
│   │   │       ├── ChatScreen.kt        # Compose 聊天界面
│   │   │       └── theme/Theme.kt        # Compose 主题
│   │   ├── res/values/                  # 资源文件
│   │   ├── assets/                      # GGUF 模型文件（可选，不含在 APK 中）
│   │   └── AndroidManifest.xml          # 权限声明
│   └── build.gradle.kts                 # App 模块构建配置
├── scripts/
│   └── download_model.sh                # 模型下载脚本
├── build.gradle.kts                      # Root 构建配置
└── README.md
```

## 模型下载

### 推荐模型
- **Gemma 2-2B Q4_K_M**（约 1.6GB）

### 下载地址

**HuggingFace（推荐）：**
```bash
# gemma-2-2b-it-Q4_K_M.gguf（约 1.6GB）
https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf
```

**国内镜像（如果 HuggingFace 访问困难）：**
```bash
https://hf-mirror.com/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf
```

或者使用项目自带的下载脚本：
```bash
cd ~/android-gemma-app
bash scripts/download_model.sh
```

> 注意：模型文件较大（~1.6GB），下载需要较长时间和稳定的网络连接。

## 如何编译

### 前置条件
1. **Android Studio Hedgehog (2023.1.1) 或更新版本**
2. **Android SDK 34**（可通过 Android Studio SDK Manager 安装）
3. **Gradle 8.5+**（项目已包含 wrapper，直接用 `gradlew` 即可）
4. **JDK 21**

### 编译步骤

```bash
cd ~/android-gemma-app

# 方式1: 使用 Gradle Wrapper（推荐）
chmod +x gradlew
./gradlew assembleDebug

# 方式2: 直接用系统 gradle（如果有）
gradle assembleDebug
```

### 安装到设备

```bash
# 通过 ADB 安装
adb install -r app/build/outputs/apk/debug/app-debug.apk

# 或直接用 Android Studio 运行
# Run > Run 'app' (Shift+F10)
```

### 构建产物

- **Debug APK**: `app/build/outputs/apk/debug/app-debug.apk`
- **Release APK**: `app/build/outputs/apk/release/app-release-unsigned.apk`

## 如何运行

1. **安装 APK** 到 Android 手机
2. **下载 GGUF 模型文件**（见上文"模型下载"）
3. **打开 App**，点击 📥 图标选择 GGUF 模型文件
4. 等待模型加载完成（约 1.6GB，需一些时间）
5. **开始聊天！**

## 技术说明

### 推理引擎
- 使用 **Google MediaPipe LLM Inference** (`com.google.mediapipe:tasks-genai`)
- 支持 GGUF 格式的量化模型（Q4_K_M 推荐）
- 模型通过文件选择器加载，无需内置到 APK

### 模型要求
- 格式：GGUF（量化版）
- 推荐：Gemma 2-2B Q4_K_M（约 1.6GB）
- 最低要求：4GB+ RAM，arm64-v8a 架构

## 性能预期

| 模型 | 量化 | 内存占用 | token/s (预估) | 手机要求 |
|------|------|----------|----------------|----------|
| Gemma 2B | Q4_K_M | ~1.8GB | 10-25 t/s | 4GB+ RAM, arm64 |
| Gemma 3B | Q4_K_M | ~2.5GB | 5-15 t/s | 6GB+ RAM, arm64 |

> 注：实际性能取决于手机 CPU（推荐 arm64-v8a）、内存和是否启用 GPU 加速。

## 注意事项

1. **模型文件约 1.6GB**，首次加载需预留空间和耐心
2. **手机存储**：确保有足够空间（模型 + app 自身约 2.5GB+）
3. **内存**：Gemma 2B 至少 4GB RAM，推荐 6GB+
4. **CPU**：arm64-v8a 架构（大多数 2018 年后手机）
5. **Android 版本**：minSdk 26 (Android 8.0)，推荐 Android 12+
6. **不要在模拟器上运行**：模拟器性能太差，无法正常推理
