# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /usr/local/google/home/mangini/tools/android-studio/sdk/tools/proguard/proguard-android.txt
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

-keepattributes *Annotation*
-keepattributes SourceFile,LineNumberTable

-assumenosideeffects class net.volcanomobile.vgmplayer.utils.LogHelper {
    public static void v(...);
    public static void d(...);
}

# Glide
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep public enum com.bumptech.glide.load.resource.bitmap.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}

# Gson uses generic type information stored in a class file when working with fields. Proguard
# removes such information by default, so configure it to keep all of it.
-keepattributes Signature

# Gson specific classes
-keep class sun.misc.Unsafe { *; }
#-keep class com.google.gson.stream.** { *; }

# Prevent proguard from stripping interface information from TypeAdapterFactory,
# JsonSerializer, JsonDeserializer instances (so they can be used in @JsonAdapter)
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer

# Easypermissions
-keepclassmembers class * {
    @pub.devrel.easypermissions.AfterPermissionGranted <methods>;
}

# Room alpha-5 issues
-dontwarn android.arch.util.paging.CountedDataSource
-dontwarn android.arch.persistence.room.paging.LimitOffsetDataSource