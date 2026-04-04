# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /sdk/tools/proguard/proguard-android.txt

# Keep llama.cpp JNI exports
-keep class com.example.gemmaapp.InferenceEngine { *; }
-keep class org.json.** { *; }

# Compose
-dontwarn androidx.compose.**
