# Add project specific ProGuard rules here.

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.hackerlauncher.chat.** { *; }
-keep class com.google.gson.** { *; }

# Retrofit
-keepattributes Signature, InnerClasses, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# Room
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *

# Firebase
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**

# Keep data classes
-keep class com.hackerlauncher.chat.Message { *; }
-keep class com.hackerlauncher.chat.ChatRequest { *; }
-keep class com.hackerlauncher.chat.ChatResponse { *; }
-keep class com.hackerlauncher.chat.ChatMessage { *; }
-keep class com.hackerlauncher.chat.Choice { *; }
