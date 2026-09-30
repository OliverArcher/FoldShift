# FoldShift ProGuard rules.
# Phase 1 leaves minification off for both buildTypes; this file exists
# so that enabling R8 later doesn't pick up the default rules. We do
# not yet obfuscate anything in the application module.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Shizuku / Stellar: keep the provider and binder classes so R8 doesn't
# strip them. They are referenced only by the Manifest and AIDL surface.
-keep class rikka.shizuku.ShizukuProvider { *; }
-keep class rikka.shizuku.Shizuku { *; }
-keep class moe.shizuku.api.** { *; }