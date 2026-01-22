# ProGuard rules for UnaMentis Android
# These rules configure code shrinking and obfuscation for release builds

# ===== GENERAL =====
-keepattributes *Annotation*, InnerClasses, Signature, SourceFile, LineNumberTable, EnclosingMethod
-renamesourcefileattribute SourceFile
-allowaccessmodification
-repackageclasses ''

# ===== OPTIMIZATION =====
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*
-optimizationpasses 5

# ===== KOTLIN =====
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# ===== KOTLINX COROUTINES =====
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ===== KOTLINX SERIALIZATION =====
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep @kotlinx.serialization.Serializable class com.unamentis.data.** { *; }

# ===== JETPACK COMPOSE =====
-dontwarn androidx.compose.**
-keep class androidx.compose.runtime.** { *; }
-keep class androidx.compose.ui.** { *; }

# Keep all Composable functions
-keep @androidx.compose.runtime.Composable class * { *; }
-keep @androidx.compose.runtime.Composable interface * { *; }

# ===== HILT/DAGGER =====
-dontwarn com.google.dagger.**
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }
-keep class com.google.dagger.** { *; }
-keep class dagger.** { *; }

# Keep injected classes
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @javax.inject.Inject class * { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# ===== ROOM DATABASE =====
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-dontwarn androidx.room.paging.**

# Keep Room DAOs
-keep interface * extends androidx.room.Dao { *; }

# ===== RETROFIT =====
-keepattributes Signature, Exceptions
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-dontwarn org.codehaus.mojo.animal_sniffer.IgnoreJRERequirement
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit

# ===== OKHTTP =====
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# OkHttp platform used only on JVM
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# ===== GSON (if used for JSON) =====
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# ===== DATA MODELS =====
-keep class com.unamentis.data.model.** { *; }
-keep class com.unamentis.data.remote.dto.** { *; }
-keep class com.unamentis.data.local.entity.** { *; }

# ===== TENSORFLOW LITE =====
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }
-dontwarn org.tensorflow.lite.**

# ===== NATIVE CODE (JNI) =====
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# Keep audio engine native interface
-keep class com.unamentis.core.audio.AudioEngine { *; }
-keep class com.unamentis.core.audio.AudioEngine$** { *; }

# Keep llama.cpp JNI interface for on-device LLM
-keep class com.unamentis.services.llm.OnDeviceLLMService { *; }
-keep class com.unamentis.services.llm.OnDeviceLLMService$** { *; }

# Keep ModelDownloadManager for model downloads
-keep class com.unamentis.services.llm.ModelDownloadManager { *; }
-keep class com.unamentis.services.llm.ModelDownloadManager$** { *; }

# Keep DeviceCapabilityDetector and its enums
-keep class com.unamentis.core.device.DeviceCapabilityDetector { *; }
-keep class com.unamentis.core.device.DeviceCapabilityDetector$** { *; }

# ===== SERVICES =====
# Keep all service interfaces
-keep interface com.unamentis.services.** { *; }
-keep class * implements com.unamentis.services.stt.STTService { *; }
-keep class * implements com.unamentis.services.tts.TTSService { *; }
-keep class * implements com.unamentis.services.llm.LLMService { *; }
-keep class * implements com.unamentis.services.vad.VADService { *; }

# ===== ANDROID COMPONENTS =====
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider

# ===== PARCELABLE =====
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# ===== SERIALIZABLE =====
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# ===== ENUMS =====
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ===== CORE CLASSES =====
-keep class com.unamentis.UnaMentisApp { *; }
-keep class com.unamentis.core.config.ApiKeyManager { *; }
-keep class com.unamentis.core.config.AppConfig { *; }
-keep class com.unamentis.core.session.SessionManager { *; }
-keep class com.unamentis.core.curriculum.CurriculumEngine { *; }
-keep class com.unamentis.core.routing.PatchPanelService { *; }
-keep class com.unamentis.core.telemetry.RemoteLogger { *; }
-keep class com.unamentis.core.accessibility.** { *; }

# ===== VIEWMODELS =====
-keep class * extends androidx.lifecycle.ViewModel { *; }
-keep class * extends androidx.lifecycle.AndroidViewModel { *; }
-keepclassmembers class * extends androidx.lifecycle.ViewModel {
    <init>(...);
}

# ===== LIFECYCLE =====
-keep class * implements androidx.lifecycle.LifecycleObserver {
    <init>(...);
}
-keep class * implements androidx.lifecycle.GeneratedAdapter {
    <init>(...);
}

# ===== NAVIGATION =====
-keep class androidx.navigation.** { *; }
-keep class * extends androidx.navigation.Navigator
-keepnames class androidx.navigation.fragment.NavHostFragment

# ===== ENCRYPTED SHARED PREFERENCES =====
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# ===== DATASTORE =====
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# ===== WORK MANAGER =====
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(...);
}
-keep class androidx.work.** { *; }

# ===== CUSTOM VIEWS =====
-keep public class * extends android.view.View {
    public <init>(android.content.Context);
    public <init>(android.content.Context, android.util.AttributeSet);
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

# ===== WEBVIEW (if used) =====
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# ===== TELEMETRY =====
-keep class com.unamentis.core.telemetry.** { *; }
-keep class com.unamentis.data.model.SessionMetrics { *; }
-keep class com.unamentis.data.model.LatencyMetrics { *; }

# ===== LOGGING =====
# Remove verbose logging in release builds (keep warnings and errors)
-assumenosideeffects class android.util.Log {
    public static *** v(...);
    public static *** d(...);
    public static *** i(...);
}

# Keep RemoteLogger for production logging
-keep class com.unamentis.core.telemetry.RemoteLogger { *; }

# ===== SECURITY =====
# Obfuscate sensitive class names (but keep functionality)
-keep,allowobfuscation class com.unamentis.core.config.ApiKey
-keep,allowobfuscation class com.unamentis.core.config.ServerConfig

# ===== CERTIFICATE PINNING (if implemented) =====
-keep class com.unamentis.data.remote.CertificatePinner { *; }

# ===== GOOGLE TINK / ERRORPRONE ANNOTATIONS =====
# Google Tink references errorprone annotations for static analysis.
# These annotations are not required at runtime - safe to suppress warnings.
-dontwarn com.google.errorprone.annotations.**
-keep class com.google.errorprone.annotations.** { *; }

# ===== END OF PROGUARD RULES =====
