# Room generates implementations reflectively referenced by the generated database class.
-keep class nl.ramon96.medicijntracker.data.db.** { *; }

# Broadcast receivers and workers are instantiated by the framework from the manifest.
-keep class nl.ramon96.medicijntracker.notify.** { *; }
-keep class nl.ramon96.medicijntracker.widget.** { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
