# Keep rules for the minified release build. Broad `-keep class **` rules were removed
# deliberately: they are what took the shrunk APK back to its unshrunk size.

# EventBus dispatches by reflection, so a @Subscribe method with no other caller looks dead.
-keepattributes *Annotation*
-keepclassmembers class * {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }

# Room. The generated implementations extend the abstract database class; the entities and
# DAOs are reached through it and through their own generated code.
-keep class * extends androidx.room.RoomDatabase

# kotlinx.serialization looks the generated serializer up by name off the Companion, which
# no bytecode reference points at.
#
# This block used to name `org.nova.messages.models.**` -- the package the app carried
# before it was renamed to com.texto.sms -- so it had matched nothing for as long as the
# rename has been in place. The serializable models are the backup format: MessagesBackup,
# SmsBackup, MmsBackup, MmsPart, MmsAddress, BackupType and MessageCategory.
-keepattributes InnerClasses
-keepclassmembers class com.texto.sms.models.** {
    *** Companion;
}
-keepclasseswithmembers class com.texto.sms.models.** {
    *** serializer(...);
}
-keepclassmembers class com.texto.sms.helpers.MessageCategory {
    *** Companion;
}
-keepclasseswithmembers class com.texto.sms.helpers.MessageCategory {
    *** serializer(...);
}

# Gson keys off the field *names*, so anything it round-trips must not be renamed. These
# are not transient: `Converters` stores attachments and participants into Room as JSON, so
# a build that renamed their fields would write rows the next build cannot read back. That
# includes commons' SimpleContact, which is serialised into the conversations table.
-keep class com.texto.sms.models.** { *; }
-keep class org.fossify.commons.models.SimpleContact { *; }
