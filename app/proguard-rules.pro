# kotlinx.serialization ships its own consumer rules; the app's @Serializable classes need nothing extra.

# Anthropic SDK: request/response models are (de)serialised by Jackson through reflection on
# Kotlin classes, so the SDK, Jackson and Kotlin metadata must survive shrinking untouched.
-keep class com.anthropic.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-keep class kotlin.Metadata { *; }
-keep class kotlin.reflect.** { *; }
-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault

# Optional dependencies of the libraries above that do not exist on Android.
-dontwarn java.beans.**
-dontwarn javax.**
-dontwarn org.w3c.dom.bootstrap.**
-dontwarn com.fasterxml.jackson.**
-dontwarn com.github.victools.**
-dontwarn org.slf4j.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn io.swagger.**
-dontwarn org.apache.hc.**
-dontwarn com.google.errorprone.annotations.**
