# App-side rules for the R8-minified release build.
#
# Everything else this app needs arrives with the libraries: Hilt, Room, Lifecycle
# and Compose ship consumer rules inside their AARs, and kotlinx-serialization and
# kotlinx-coroutines ship theirs inside their JARs (META-INF/com.android.tools/r8),
# all of which AGP hands to R8 on its own. The rule below covers the reflective
# lookup that reaches app-owned classes, so it is stated here rather than left to a
# dependency bump to re-supply.

# kotlinx.serialization, on behalf of navigation3. NavKeySerializer writes a
# destination's class name and resolves it on restore through
# Class.forName(name).kotlin().serializer(). App code never calls Main.serializer()
# itself - the keys only ever go onto the back stack - so INSTANCE and serializer()
# on the five @Serializable keys (Onboarding, Main, Targets, Stats, Settings) are
# reachable by reflection alone. Lose them and a rotation or process-death restore
# of the back stack throws SerializationException: Serializer for class 'Main' is
# not found.
-keepclassmembers @kotlinx.serialization.Serializable class com.example.sonder.* {
    public static ** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
