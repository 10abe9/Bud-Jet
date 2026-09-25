# Bud-Jet R8 rules. Libraries (Room, Billing, AndroidX, Material) ship their own rules;
# only what the app itself relies on by name is listed here.

# Readable crash stack traces in Play Console (the R8 mapping file goes into the AAB).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Enum names are persisted: TransactionType in the database (AppTypeConverters),
# Tier/Plan in preferences, ThemeManager modes etc. Renaming them would make existing
# data unreadable after an update.
-keepclassmembers enum com.abe.bud_jet.** {
    <fields>;
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Fragments are created by class name from the navigation graph and on process restore.
-keep class com.abe.bud_jet.** extends androidx.fragment.app.Fragment {
    public <init>();
}

# ViewModels created by the default factory (AndroidViewModel(Application) constructor).
-keep class com.abe.bud_jet.** extends androidx.lifecycle.AndroidViewModel {
    public <init>(android.app.Application);
}
