# These runtimes use JNI and reflection internally.
-keep class ai.onnxruntime.** { *; }
-keep class com.k2fsa.sherpa.onnx.** { *; }
-dontwarn ai.onnxruntime.**
-dontwarn com.k2fsa.sherpa.onnx.**
