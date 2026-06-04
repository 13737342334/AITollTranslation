# ProGuard rules for Fangyi Translator

# ML Kit
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# Room
-keep class com.fangyi.translator.data.** { *; }
-keep class * extends androidx.room.RoomDatabase

# Keep entity classes
-keep class com.fangyi.translator.data.TranslationCacheEntity { *; }
