# Room generates implementations reflectively referenced by the generated database class.
-keep class nl.ramon96.medicijntracker.data.db.** { *; }

# Broadcast receivers and workers are instantiated by the framework from the manifest.
-keep class nl.ramon96.medicijntracker.notify.** { *; }
-keep class nl.ramon96.medicijntracker.widget.** { *; }
-keep class * extends androidx.work.ListenableWorker { *; }

# The Google code scanner is loaded out of Play services rather than linked from this app, so R8
# sees no references to the classes it looks up and strips them. The symptom is a null where the
# library expects an instance, which surfaces as a NullPointerException on getClass() rather than
# as anything mentioning ML Kit.
-keep class com.google.mlkit.** { *; }
-keep interface com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-dontwarn com.google.mlkit.**
