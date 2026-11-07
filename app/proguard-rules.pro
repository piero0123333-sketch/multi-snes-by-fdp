-keep class com.fdp.multisnes.core.SnesNativeBridge { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
-keepattributes *Annotation*
-keepclassmembers class ** {
    @kotlinx.serialization.Serializable <fields>;
}
