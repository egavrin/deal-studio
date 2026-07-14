# JNI methods and callbacks are resolved by symbol/name rather than Kotlin references.
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
-keep class com.offlineassistant.app.llm.JniLlamaNativeEngine$TokenCallback {
    public void onToken(java.lang.String);
}

# These runtimes use JNI and reflection internally.
-keep class ai.onnxruntime.** { *; }
-keep class com.k2fsa.sherpa.onnx.** { *; }
-dontwarn ai.onnxruntime.**
-dontwarn com.k2fsa.sherpa.onnx.**
