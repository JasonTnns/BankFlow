-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.example.bankflow.**$$serializer { *; }
-keepclassmembers class com.example.bankflow.** { *** Companion; }
-keepclasseswithmembers class com.example.bankflow.** { kotlinx.serialization.KSerializer serializer(...); }

-dontwarn org.slf4j.**
-dontwarn org.slf4j.impl.StaticLoggerBinder
-dontwarn java.lang.management.**
-dontwarn java.lang.instrument.ClassFileTransformer
-dontwarn java.lang.instrument.Instrumentation
-dontwarn sun.misc.Signal
-dontwarn sun.misc.SignalHandler