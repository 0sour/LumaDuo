# 保留 JNI 入口与原生方法声明，避免 R8 把 native 方法所在类裁掉导致 UnsatisfiedLinkError。
-keep class com.sour.lumaduo.framegen.FramegenInterceptor { *; }
-keepclasseswithmembernames class com.sour.lumaduo.framegen.** {
    native <methods>;
}
