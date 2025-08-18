# This file is used ONLY when building the library itself.
# It prevents R8 from renaming your public API before it's packaged.

# Public SDK surface callable by the app
-keep class so.kontext.ads.AdsProvider { *; }
-keep class so.kontext.ads.AdsBuilder { public *; }
-keep class so.kontext.ads.AdsProvider$Builder { *; }
-keep class so.kontext.ads.ui.InlineAdKt { *; }
-keep class so.kontext.ads.domain.** { *; }

# Ktorfit runtime
-keep class de.jensklingenberg.ktorfit.** { *; }

# Ktorfit service interfaces (move to a public pkg when you can)
-keep interface so.kontext.ads.internal.data.api.** { *; }

# Kotlinx serialization (if used in app process)
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class **$$serializer { *; }
-keepattributes *Annotation*,Signature,Exceptions,InnerClasses,EnclosingMethod,MethodParameters