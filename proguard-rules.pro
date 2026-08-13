# Please add these rules to your existing keep rules in order to suppress warnings.
# This is generated automatically by the Android Gradle plugin.
-dontwarn org.ietf.jgss.GSSContext
-dontwarn org.ietf.jgss.GSSCredential
-dontwarn org.ietf.jgss.GSSException
-dontwarn org.ietf.jgss.GSSManager
-dontwarn org.ietf.jgss.GSSName
-dontwarn org.ietf.jgss.Oid

# ACRA rules
-keep class org.acra.** { *; }
-dontwarn org.acra.**

# Keep the application and its build config as they are used by ACRA
-keep class com.eleybourn.bookcatalogue.BookCatalogueApp { *; }
-keep class com.eleybourn.bookcatalogue.BuildConfig { *; }

# Keep manifest-referenced components (Activities, Providers, Services)
# (Actually getDefaultProguardFile('proguard-android-optimize.txt') handles this,
# but it doesn't hurt to be explicit if you have custom logic)

# If you use reflection for any of your data classes, keep them here:
-keep class com.eleybourn.bookcatalogue.data.** { *; }
-keep class com.eleybourn.bookcatalogue.database.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep line numbers for better stack traces in crash reports
-keepattributes SourceFile,LineNumberTable
