#!/bin/bash
# 下载 Gemma 2B Q4 GGUF 模型
# 使用: bash download_model.sh
set -e

ASSETS_DIR="app/src/main/assets"
MODEL_FILE="gemma-2-2b-it-Q4_K_M.gguf"
MODEL_URL="https://huggingface.co/bartowski/gemma-2-2b-it-GGUF/resolve/main/gemma-2-2b-it-Q4_K_M.gguf"

# 如果模型已存在，跳过
if [ -f "$ASSETS_DIR/$MODEL_FILE" ]; then
    SIZE=$(du -h "$ASSETS_DIR/$MODEL_FILE" | cut -f1)
    echo "模型已存在: $ASSETS_DIR/$MODEL_FILE ($SIZE)"
    exit 0
fi

mkdir -p "$ASSETS_DIR"

echo "下载 Gemma 2-2B Q4_K_M 模型 (~1.6GB)..."
echo "URL: $MODEL_URL"
echo ""

# 尝试 HuggingFace
if command -v curl &> /dev/null; then
    curl -L -o "$ASSETS_DIR/$MODEL_FILE" "$MODEL_URL" \
        --max-time 600 \
        -w "进度: %{percent}% | 速度: %{speed_download} bytes/s | 已下载: %{size_download} bytes\n"
else
    echo "错误: curl 未安装"
    exit 1
fi

SIZE=$(du -h "$ASSETS_DIR/$MODEL_FILE" | cut -f1)
echo ""
echo "下载完成! 模型文件: $ASSETS_DIR/$MODEL_FILE ($SIZE)"
echo "请重新构建 APK: ./gradlew assembleDebug"
