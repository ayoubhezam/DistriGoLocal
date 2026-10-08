# R8 rules for DistriGo's release (and benchmark) builds.
#
# Room, Hilt, WorkManager, Compose, CameraX, ML Kit and Maps ship their own rules. What follows covers
# what only this app knows: the classes it reads by name at run time.

# ── Shrink and optimize, but do not rename ──
# Renaming buys almost no speed. Kept off so that:
#  - crash and ANR reports written on the phone (Paramètres → Diagnostic) name real classes and lines;
#  - nothing stored by an earlier build — a draft's JSON, WorkManager's worker names — ever meets a
#    renamed class.
-dontobfuscate
-keepattributes SourceFile,LineNumberTable

# ── Gson, by reflection ──
# The four drafts keep their lines as JSON in the database (items_json), written and read by field
# name: a field renamed or removed would make every saved draft unreadable. The wilaya file in assets
# is read the same way. Everything else in the app builds its JSON by hand (JsonObject), by key.
-keep class com.distrigo.app.data.model.VenteDraftLine { <fields>; <init>(...); }
-keep class com.distrigo.app.data.model.TourneeVenteDraftLine { <fields>; <init>(...); }
-keep class com.distrigo.app.data.model.DraftLine { <fields>; <init>(...); }
-keep class com.distrigo.app.data.model.ChargementDraftLine { <fields>; <init>(...); }
-keep class com.distrigo.app.data.model.Wilaya { <fields>; <init>(...); }
-keep class com.distrigo.app.data.model.Commune { <fields>; <init>(...); }

# Gson 2.8.5 has no rules of its own. A TypeToken's list type is read from its generic signature.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-dontwarn sun.misc.**
