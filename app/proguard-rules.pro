# Stark Browser ProGuard rules

# Keep Room entities
-keep class com.starkbrowser.app.data.** { *; }

# Keep ViewModel
-keep class * extends androidx.lifecycle.ViewModel { *; }

# WebView JavaScript interfaces
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# DataStore
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite* {
   <fields>;
}
