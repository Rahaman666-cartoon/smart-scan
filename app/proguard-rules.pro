# Optimization and Obfuscation configuration for Scan to PDF

# Keep line numbers and annotations for debugging and stack traces
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses,EnclosingMethod

# Android Architecture & Jetpack Compose
-dontwarn androidx.compose.**
-keep class androidx.compose.** { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# Hilt & Dependency Injection
-dontwarn dagger.hilt.**
-keep class dagger.hilt.** { *; }
-keep class com.example.di.** { *; }
-keep class * extends android.app.Application { *; }
-keep class * extends android.app.Activity { *; }
-keep class com.example.MainActivity { *; }
-keep class com.example.SmartScanApplication { *; }

# Room Database & Data Layer
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
-keep class com.example.data.** { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public void clearAllTables();
    <init>();
}

# PDFBox Android (Crucial for PDF compilation, font loading and rasterizing)
-keep class com.tom_roush.pdfbox.** { *; }
-dontwarn com.tom_roush.pdfbox.**
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Google Play Services & ML Kit
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Image Loading (Coil)
-dontwarn coil.**
-keep class coil.** { *; }

# Network & Serialization (Moshi, Retrofit, OkHttp)
-dontwarn com.squareup.moshi.**
-keep class com.squareup.moshi.** { *; }
-keepclassmembers class * {
    @com.squareup.moshi.* <fields>;
    @com.squareup.moshi.* <methods>;
}
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-dontwarn okhttp3.**
-dontwarn okio.**

# Kotlin Coroutines
-dontwarn kotlinx.coroutines.**
-keep class kotlinx.coroutines.** { *; }
