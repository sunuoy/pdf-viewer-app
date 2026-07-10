# PDFBox-Android Keep Rules
-keep class com.tom_roush.pdfbox.** { *; }
-keep class org.apache.padaf.preflight.** { *; }
-dontwarn com.tom_roush.pdfbox.**

# Room Database Keep Rules
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# SLF4J (used by junrar) - suppress missing impl classes
-dontwarn org.slf4j.**

# General optimizations
-repackageclasses ''
-allowaccessmodification
