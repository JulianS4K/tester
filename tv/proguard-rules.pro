# Retrofit / OkHttp / Moshi keep rules
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-dontwarn javax.annotation.**

# Retrofit does reflection on generic parameters and service method signatures.
-keepattributes Signature, InnerClasses, EnclosingMethod, RuntimeVisibleAnnotations, AnnotationDefault
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response

# Moshi reflective adapters need the model classes and their members.
-keep class com.trakt.tv.data.model.** { *; }
-keepclassmembers class com.trakt.tv.data.model.** { *; }
-keep class kotlin.Metadata { *; }
