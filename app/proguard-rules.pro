# BouncyCastle is used through its lightweight JCA bridge for self-signed
# certificate generation. Its provider machinery is reflection heavy.
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# protobuf-javalite generated messages
-keep class com.qoder.atvremote.proto.** { *; }
-keep class com.google.polo.wire.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# Hilt / Dagger
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault
-dontwarn com.google.errorprone.annotations.**
