# MapLibre ships its own consumer rules; keep JNI-bound classes reachable.
-keep class org.maplibre.android.** { *; }
-dontwarn org.maplibre.android.**
# Strip debug/verbose logging from release builds (no PII in logs).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
