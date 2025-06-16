# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /home/tiefensuche/Android/Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

-keep class androidx.datastore.** { *; }
-keep class * implements androidx.datastore.core.Serializer { *; }

-dontobfuscate
-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*

# Keep data classes used for serialization
-keep class com.nvllz.stepsy.ui.AchievementsActivity$MilestoneAchievement { *; }
-keep class com.nvllz.stepsy.ui.AchievementsActivity$ComputedResults { *; }

# Keep Gson related classes
-keepattributes Signature
-keepattributes *Annotation*
-keep class com.google.gson.** { *; }
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer