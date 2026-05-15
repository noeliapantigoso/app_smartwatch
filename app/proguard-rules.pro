# Conservar números de línea en stack traces de producción
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ── Samsung Health Tracking SDK ──────────────────────────────────────────────
# Toda la API pública del SDK se carga por reflexión en tiempo de ejecución;
# sin estas reglas, R8 la elimina y el servicio falla al conectar.
-keep class com.samsung.android.service.health.tracking.** { *; }
-keep interface com.samsung.android.service.health.tracking.** { *; }
-dontwarn com.samsung.android.service.health.tracking.**

# ── Google Play Services Wearable ────────────────────────────────────────────
-keep class com.google.android.gms.wearable.** { *; }
-dontwarn com.google.android.gms.wearable.**

# ── Mantener listeners del SDK (callbacks invocados por reflexión) ────────────
-keep class * implements com.samsung.android.service.health.tracking.HealthTracker$TrackerEventListener { *; }
-keep class * implements com.samsung.android.service.health.tracking.ConnectionListener { *; }

# ── ForegroundService y Binder (sistema lo resuelve por nombre) ───────────────
-keep class com.signals.smartwatch.** extends android.app.Service { *; }
-keep class com.signals.smartwatch.** extends android.os.Binder { *; }

# ── Room ──────────────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-dontwarn androidx.room.**
