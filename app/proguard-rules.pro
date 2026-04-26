# Proguard rules for HackerLauncher
-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception

# Retrofit
-keepattributes Signature, Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
