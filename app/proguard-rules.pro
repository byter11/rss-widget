# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# ROME (RSS/Atom library) - Uses heavy reflection and dynamic module loading
-keep class com.rometools.rome.** { *; }
-keep interface com.rometools.rome.** { *; }
-dontwarn com.rometools.rome.**
# ROME modules
-keep class com.rometools.modules.** { *; }
-dontwarn com.rometools.modules.**
# JDOM is used by ROME
-keep class org.jdom2.** { *; }
-dontwarn org.jdom2.**

# PrettyTime
-keep class org.ocpsoft.prettytime.** { *; }
-dontwarn org.ocpsoft.prettytime.**

# Room
-keepclassmembers class * {
  @androidx.room.Database *;
  @androidx.room.Dao *;
  @androidx.room.Entity *;
}
-keep class androidx.room.RoomDatabase
-dontwarn androidx.room.**

# WorkManager
-keep class * extends androidx.work.ListenableWorker {
    <init>(android.content.Context, androidx.work.WorkerParameters);
}

# Kotlin Parcelize
-keepnames class * implements android.os.Parcelable
-keepclassmembers class * implements android.os.Parcelable {
    public static final ** CREATOR;
}

# Keep the RssItem data class in RssRemoteViewsFactory as it might be used in Intents/RemoteViews
-keep class com.byterdevs.rsswidget.RssRemoteViewsFactory$RssItem { *; }

# General optimization settings
-optimizationpasses 5
-allowaccessmodification
-dontpreverify
