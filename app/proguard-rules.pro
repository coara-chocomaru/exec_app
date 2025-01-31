-keep class android.** { *; }
-keep interface android.** { *; }

-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
    public static *** e(...);
}

-keep class com.coara.execapp.MainActivity { *; }

-adaptresourcefilecontents **.xml
-adaptresourcefilenames **.png

-keepnames class * implements java.io.Serializable {
    static final long serialVersionUID;
}

-keep class ** { *; }

-keepattributes *Annotation*
-keepattributes Exceptions, InnerClasses, Signature, Deprecated, EnclosingMethod

-classobfuscationdictionary obfuscation-dictionary.txt



-allowaccessmodification

-repackageclasses ''

-adaptclassstrings

-keepclassmembers class * {
    *;
    public void set*(...);
    public void get*(...);
    void lambda*(...);
}

-dontpreverify
