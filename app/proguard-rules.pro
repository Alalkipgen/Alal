# Alal — R8 rules. Library consumer rules (Room, Hilt, Compose, WorkManager)
# are pulled in automatically; these are only the app-specific extras.

# Keep line numbers for readable crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Kotlinx Serialization (type-safe navigation routes)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.alal.notes.**$$serializer { *; }
-keepclassmembers class com.alal.notes.** { *** Companion; }
-keepclasseswithmembers class com.alal.notes.** { kotlinx.serialization.KSerializer serializer(...); }

# Room entities / enums used in TypeConverters
-keep class com.alal.notes.data.entity.** { *; }
-keepclassmembers enum com.alal.notes.** { *; }

# Hilt workers
-keep class * extends androidx.work.ListenableWorker { <init>(...); }

# Backup JSON models (kotlinx.serialization)
-keep class com.alal.notes.data.backup.** { *; }

# Biometric prompt uses FragmentActivity via reflection-free path, but keep for safety
-keep class androidx.biometric.** { *; }
