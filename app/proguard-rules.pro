# Don't obfuscate code
-dontobfuscate

# Our code
-keep class com.sour.lumaduo.binding.input.evdev.* {*;}

# Moonlight common
-keep class com.sour.lumaduo.nvstream.jni.* {*;}

# Okio
-keep class sun.misc.Unsafe {*;}
-dontwarn java.nio.file.*
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn okio.**

# BouncyCastle
-keep class org.bouncycastle.jcajce.provider.asymmetric.* {*;}
-keep class org.bouncycastle.jcajce.provider.asymmetric.util.* {*;}
-keep class org.bouncycastle.jcajce.provider.asymmetric.rsa.* {*;}
-keep class org.bouncycastle.jcajce.provider.digest.** {*;}
-keep class org.bouncycastle.jcajce.provider.symmetric.** {*;}
-keep class org.bouncycastle.jcajce.spec.* {*;}
-keep class org.bouncycastle.jce.** {*;}
-dontwarn javax.naming.**

# jMDNS
-dontwarn javax.jmdns.impl.DNSCache
-dontwarn org.slf4j.**

# Shizuku starts this service by class name in another process.
-keep class com.sour.lumaduo.services.ShellService { *; }
-keep class com.sour.lumaduo.services.IKeyboardAccessibilityShell { *; }
-keep class com.sour.lumaduo.services.IKeyboardAccessibilityShell$Stub { *; }
-keep class com.sour.lumaduo.services.IKeyboardAccessibilityShell$Stub$Proxy { *; }
