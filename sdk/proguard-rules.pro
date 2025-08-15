# This file is used ONLY when building the library itself.
# It prevents R8 from renaming your public API before it's packaged.

# Public SDK surface callable by the app
-keep class com.kontext.ads.AdsProvider { *; }
-keep class com.kontext.ads.AdsBuilder { public *; }
-keep class com.kontext.ads.AdsProvider$Builder { *; }
-keep class com.kontext.ads.ui.InlineAdKt { *; }
-keep class com.kontext.ads.domain.** { *; }

# Ktorfit runtime
-keep class de.jensklingenberg.ktorfit.** { *; }

# Ktorfit service interfaces (move to a public pkg when you can)
-keep interface com.kontext.ads.internal.data.api.** { *; }

# Kotlinx serialization (if used in app process)
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class **$$serializer { *; }
-keepattributes *Annotation*,Signature,Exceptions,InnerClasses,EnclosingMethod,MethodParameters