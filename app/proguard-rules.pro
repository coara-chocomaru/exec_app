-keep class android.** { *; }
-keep interface android.** { *; }
-dontwarn java.lang.**
-dontusemixedcaseclassnames
-keep class com.coara.execapp.MainActivity { *; }
-keep public class com.coara.securetest.SecurityMonitor { public static void main(java.lang.String[]); }


-keepattributes *Annotation*

-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

-adaptresourcefilecontents **.xml
-adaptresourcefilenames **.png

-classobfuscationdictionary obfuscation-dictionary.txt
-renamesourcefileattribute SourceFile
-keepattributes Exceptions, InnerClasses, Signature, Deprecated, EnclosingMethod
-optimizationpasses 3
-mergeinterfacesaggressively

-adaptclassstrings
-repackageclasses ''
-keep class dummy.** { *; }
-keep class dummy.symboliclink.** { *; }
-keep class dummy.files.** { *; }

-keepclassmembers class * {
    *;
    public void set*(...);
    public void get*(...);
    void lambda*(...);
}

-keepnames class * implements java.io.Serializable {
    static final long serialVersionUID;
}

-renamesourcefileattribute SourceFile

-dontshrink
-dontoptimize
-keep class ** { *; }

-allowaccessmodification
-dontpreverify
