# Gemma AI - Android 端侧大语言模型应用

在 Android 手机上运行 Google Gemma 3B 量化模型的 AI 助手应用。

## 项目概述

本项目实现了一个完整的 Android 应用，可加载和运行 GGUF 格式的 Gemma 3B 量化模型（Q4_K_M），通过 llama.cpp 推理引擎在端侧进行 LLM 推理。

```
技术栈:
  模型:     Google Gemma 3B Q4_K_M (GGUF)
  推理引擎: llama.cpp (Android JNI)
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
│   │   │   ├── InferenceEngine.kt       # llama.cpp JNI 封装（Mock + JNI 两种模式）
│   │   │   ├── ChatViewModel.kt         # MVVM ViewModel
│   │   │   └── ui/
│   │   │       ├── ChatScreen.kt        # Compose 聊天界面
│   │   │       └── theme/Theme.kt        # Compose 主题
│   │   ├── res/values/                   # 资源文件（strings, themes）
│   │   └── AndroidManifest.xml           # 权限声明
│   └── build.gradle.kts                  # App 模块构建配置
├── build.gradle.kts                      # Root 构建配置
├── settings.gradle.kts                   # 项目设置
├── gradle.properties                     # Gradle 配置（AndroidX 等）
└── README.md                             # 本文件
```

## build.gradle.kts 核心配置

```kotlin
// 关键配置：
// - compileSdk 34 / minSdk 26
// - Jetpack Compose BOM 2024.02.00
// - NDK 用于编译 native llama.cpp（可选）
// - Java 17 / Kotlin 1.9.22

android {
    compileSdk = 34
    defaultConfig {
        minSdk = 26
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.8"
    }
    buildFeatures.compose = true
}
```

## 模型下载

### 推荐模型
- **Gemma 3B Q4_K_M**（推荐，约 1.8GB）

### 下载地址（选一个）

**HuggingFace（推荐）：**
```bash
# gemma-3b-it-Q4_K_M.gguf（约 1.8GB）
https://huggingface.co/bartowski/gemma-3b-it-GGUF/resolve/main/gemma-3b-it-Q4_K_M.gguf
https://huggingface.co/ggml-org/gemma-3b-it-GGUF/resolve/main/gemma-3b-it-Q4_K_M.gguf
```

**国内镜像（如果 HuggingFace 访问困难）：**
```bash
# 可能需要手动下载
https://hf-mirror.com/bartowski/gemma-3b-it-GGUF/resolve/main/gemma-3b-it-Q4_K_M.gguf
```

下载后将文件放到手机存储中，通过 App 内置的文件选择器加载。

### Gemma 7B（如果手机内存充足）
```bash
# gemma-7b-it-Q4_K_M.gguf（约 4.4GB）
https://huggingface.co/bartowski/gemma-7b-it-GGUF/resolve/main/gemma-7b-it-Q4_K_M.gguf
```

## 如何编译

### 前置条件
1. **Android Studio Hedgehog (2023.1.1) 或更新版本**
2. **Android SDK 34**（可通过 Android Studio SDK Manager 安装）
3. **Gradle 8.5+**（项目已包含 wrapper，直接用 `gradlew` 即可）
4. **NDK**（可选，用于编译 native llama.cpp）

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
2. **打开 App**，点击右上角 📥 下载图标
3. **选择 GGUF 模型文件**（gemma-3b-it-Q4_K_M.gguf）
4. 等待模型加载完成（约 1.8GB，需一些时间复制到 app 目录）
5. **开始聊天！**

## Mock 模式说明

当前 `InferenceEngine.kt` 默认使用 **Mock 模式**（`USE_MOCK = true`）：
- 不需要 native .so 库
- 可以编译通过，验证 UI 和流程
- 回复是模拟的，用于开发和演示

要启用真实推理，需要集成 llama.cpp native 库（见下文）。

## 集成 llama.cpp Native 库（启用真实推理）

### 方案 A: 使用预编译 AAR（推荐）

推荐使用 **llm-bridge** 或类似预编译包：

1. 获取 llama.cpp Android AAR（如 [`llm-bridge`](https://github.com/...））
2. 放入 `app/libs/llm-bridge.aar`
3. 取消 `app/build.gradle.kts` 中的注释：
   ```kotlin
   implementation(files("libs/llm-bridge.aar"))
   ```
4. 设置 `InferenceEngine.kt` 中 `USE_MOCK = false`

### 方案 B: 从源码编译 llama.cpp

```bash
# 1. 克隆 llama.cpp
git clone https://github.com/ggerganov/llama.cpp.git
cd llama.cpp

# 2. 安装 Android NDK（Linux/macOS）
export ANDROID_NDK_HOME=~/Android/Sdk/ndk/25.2.9519653

# 3. 使用 CMake 构建 Android 版本
mkdir build-android && cd build-android
cmake .. \
  -DCMAKE_TOOLCHAIN_FILE=$ANDROID_NDK_HOME/build/cmake/android.toolchain.cmake \
  -DANDROID_ABI=arm64-v8a \
  -DANDROID_PLATFORM=android-24 \
  -DLLAMA_BUILD_EXAMPLES=OFF \
  -DLLAMA_BUILD_TESTS=OFF \
  -DBUILD_SHARED_LIBS=ON

# 4. 编译
cmake --build . --config Release

# 5. 产物 libllama.so 在 build/lib/
```

### 方案 C: 使用 lm Harness 风格的 Android 包

Google 官方 Gemma Android Demo 使用 `llm-bridge`：
```kotlin
// dependencies {
//     implementation("com.google.android.libraries.llmbridge:llmbridge-android:0.0.1")
// }
```
参考: https://github.com/google-developer-training/gemma-android-kotlin

## 当前状态与已知问题

### ✅ 已完成
- 完整项目结构（Gradle + Compose + MVVM）
- Mock 推理引擎（可编译运行）
- 聊天 UI（流式输出、光标动画、状态 Chip）
- 文件选择器和模型加载流程
- 对话历史管理

### ⚠️ Mock 模式限制
- 回复为预设文本，无真实 AI 推理
- 用于 UI 流程验证，不可用于生产

### 🔧 待集成
- llama.cpp JNI 绑定（需 native .so 或 AAR）
- GGUF 模型文件加载（需用户手动提供）
- GPU 加速（Vulkan / OpenCL）

## 性能预期

| 模型 | 量化 | 内存占用 | token/s (预估) | 手机要求 |
|------|------|----------|----------------|----------|
| Gemma 3B | Q4_K_M | ~2.5GB | 5-15 t/s | 6GB+ RAM, arm64 |
| Gemma 7B | Q4_K_M | ~5GB | 3-8 t/s | 8GB+ RAM, arm64 |
| Gemma 2B | Q4_K_M | ~1.5GB | 10-25 t/s | 4GB+ RAM |

> 注：实际性能取决于手机 CPU（推荐 arm64-v8a）、内存和是否启用 GPU 加速。

## 注意事项

1. **模型文件约 1.8GB**，首次加载需预留空间和耐心
2. **手机存储**：确保有足够空间（模型 + app 自身约 3GB+）
3. **内存**：Gemma 3B 至少 6GB RAM，7B 至少 8GB
4. **CPU**：arm64-v8a 架构（大多数 2019 年后手机）
5. **Android 版本**：minSdk 26 (Android 8.0)，推荐 Android 12+
6. **不要在模拟器上运行**：模拟器性能太差，无法正常推理
