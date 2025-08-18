# This file is packaged with your AAR and used by the app that includes your library.

############ Public API that must be exported in the AAR
-keep class so.kontext.ads.AdsProvider { *; }
-keep class so.kontext.ads.AdsBuilder { *; }
-keep class so.kontext.ads.AdsProvider$Builder { *; }
-keep class so.kontext.ads.ui.InlineAdKt { *; }


############ Domain models (you said they disappeared)
-keep class so.kontext.ads.domain.** { *; }

############ Ktorfit service interfaces (currently under internal)
-keep interface so.kontext.ads.internal.data.api.** { *; }

############ WebView JS bridge (reflective)
-keepclassmembers class so.kontext.ads.internal.ui.IFrameBridge {
    @android.webkit.JavascriptInterface <methods>;
}

############ Kotlinx serialization (if your DTOs/domain use @Serializable)
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class **$$serializer { *; }
-keepclassmembers class so.kontext.ads.** {
    *** Companion;        # keep companion (used by serializers)
}
-keepattributes *Annotation*

############ Useful metadata
-keepattributes Signature,Exceptions,InnerClasses,EnclosingMethod,MethodParameters