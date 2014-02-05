# The following line may be different
-libraryjars <java.home>/lib/rt.jar(java/**,javax/**)

-verbose
# (3)Not remove unused code
# -dontshrink

#-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*

-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.app.Service
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.backup.BackupAgentHelper
-keep public class * extends android.preference.Preference
-keep public class com.android.vending.licensing.ILicensingService

# (2)Simple XML
-keep public class org.simpleframework.**{ *; } 
-keep class org.simpleframework.xml.**{ *; } 
-keep class org.simpleframework.xml.core.**{ *; } 
-keep class org.simpleframework.xml.util.**{ *; }

-dontwarn org.openstreetmap.osmosis.osmbinary.**
-dontwarn sun.misc.Unsafe
-dontwarn com.google.protobuf.**
-dontwarn com.sun.management.OperatingSystemMXBean
-dontwarn org.apache.http.entity.mime.**

# (1)Annotations and signatures
-keepattributes *Annotation*
-keepattributes Signature

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet);
}

-keepclasseswithmembers class * {
    public <init>(android.content.Context, android.util.AttributeSet, int);
}

-keep @org.simpleframework.xml.Root class *

-keep public class * extends com.graphhopper.routing.util.AbstractFlagEncoder
-keep public class * extends com.graphhopper.storage.GraphStorage