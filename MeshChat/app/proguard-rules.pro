# Правила ProGuard для MeshChat

# Сохранить модели данных для Gson
-keep class com.meshchat.data.** { *; }
-keep class com.meshchat.mesh.** { *; }
-keep class com.meshchat.crypto.EncryptedPayload { *; }

# Gson
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**
