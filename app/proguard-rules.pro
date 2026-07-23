# ProGuard rules for Ethred 360° Capture App
# These rules preserve classes required by Hilt, Room, Retrofit, and Gson at runtime.

# ─── Hilt / Dagger ─────────────────────────────────────────────
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }
-keepclassmembers class * {
    @dagger.hilt.android.lifecycle.HiltViewModel <init>(...);
}

# ─── Room Database ──────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep @androidx.room.Dao class *
-keep @androidx.room.Database class *
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public static ** INSTANCE;
}

# ─── Retrofit / OkHttp ─────────────────────────────────────────
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
-keep interface com.ethred.panorama.data.remote.EthredApiService { *; }

# ─── Gson / JSON serialization ─────────────────────────────────
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class com.ethred.panorama.data.remote.dto.** { *; }

# ─── WorkManager ───────────────────────────────────────────────
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class com.ethred.panorama.worker.** { *; }

# ─── Native JNI ────────────────────────────────────────────────
-keep class com.ethred.panorama.stitching.** { *; }
-keepclassmembers class com.ethred.panorama.stitching.NativeStitcher {
    native <methods>;
}

# ─── Kotlin coroutines ─────────────────────────────────────────
-keepclassmembers class kotlinx.coroutines.** { *; }
-keep class kotlin.coroutines.** { *; }

# ─── General ───────────────────────────────────────────────────
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
