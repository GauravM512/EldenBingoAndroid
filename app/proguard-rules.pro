# MessagePack
-keep class org.msgpack.** { *; }
-dontwarn org.msgpack.**

# Kotlin Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# EldenBingo models
-keep class com.eldenbingo.android.data.model.** { *; }

# LZ4
-keep class net.jpountz.** { *; }
-dontwarn net.jpountz.**
