# MULTI SNES BY FDP — Emulador SNES Android + Hotspot 2J Cero Lag

Aplicación móvil nativa para **Android (Kotlin/Java + Jetpack Compose + OpenGL ES 3.0 + C++17 NDK/JNI)** diseñada sobre la interfaz retro de 16 bits **MULTI SNES BY FDP**, con soporte universal de ROMs sin restricciones y modo multijugador local **Hotspot directo (<10 cm)** con Rollback determinista libre de lag.

---

## Características Principales

### 1. Compatibilidad Universal de ROMs y Carátulas Inteligentes (`CoverLoader.java`)
- Formatos aceptados directamente sin modificar el archivo original: `.sfc`, `.smc`, `.swc`, `.fig` y archivos comprimidos `.zip`.
- **Limpieza automática de cabeceras SMC de 512 bytes** y detección de LoROM, HiROM, ExHiROM y chips especiales (**CX4**, **SuperFX / GSU**, **SA-1**, **DSP-1..4**, **S-DD1**).
- **Sistema Inteligente de Carátulas (`CoverLoader.java`)**:
  - **Prioridad 1 (Caché Local Offline)**: verifica memoria (`LruCache`) y almacenamiento local (`covers/[cleanName].png`) sin volver a consultar internet si la portada ya existe.
  - **Prioridad 2 (Cascada de Regiones + Fuzzy Matching)**: descarga desde `libretro-thumbnails` probando sufijos de región y árbol GitHub con coincidencia difusa, preservando intacto el hash CRC32 del `.zip` para el modo Online.

### 2. Pipeline de Audio a 48000 Hz sin Estática ni Pérdida de Muestras
- **Resampling Estéreo Hermite de 4 puntos en C++ (`SnesCoreBridge::onAudioBatch`)**: convierte el flujo nativo del chip Sony SPC700 (`32040 Hz`) a `48000 Hz` antes de encolarlo en `audioFifo_`.
- **Crossfade Post-Rollback / Post-Rewind de 32 muestras (`<1 ms`)**: elimina cualquier chasquido (`pops/clicks`) tras re-simulaciones de red o rebobinado.
- **Reproductor `SnesAudioPlayer` con cola `pendingBuffer`**: buffer `AudioTrack` (`getMinBufferSize * 3`, `PERFORMANCE_MODE_LOW_LATENCY`) y drenaje de `4096` muestras en `WRITE_NON_BLOCKING` que conserva cualquier muestra parcial (`count - written`) para el siguiente ciclo sin descartar jamás audio.

### 3. Sistema de Guardado Rotativo (10 Slots FIFO), Auto-Guardado al Salir y Motor de Rewind en RAM
- **Cola Circular de 10 Guardados por Juego (`SaveStateManager.kt`)**:
  - Mantiene hasta 10 ranuras (`slot_1.state` a `slot_10.state`) con miniatura `.png` del framebuffer y `metadata.json`. Al superar 10 guardados, sobrescribe automáticamente el más antiguo (rotación FIFO).
  - **Doble modo de carga**: carga directa en 1 toque del último estado (`latest_slot`) desde el icono HUD en partida, desde `CARGAR PROGRESO` o desde mando físico; y selector visual retro (`SaveSlotPickerDialog.kt`) con las 10 ranuras desde `HERRAMIENTAS -> CARGAR R.`.
  - **Auto-Guardado al salir o cerrar la app (`onPause` / `onStop` / `exitGameToCatalog`)**: guarda automáticamente el progreso actual para reanudarlo de forma instantánea al volver a abrir la aplicación.
- **Motor de Rewind en Memoria RAM (`SnesCoreBridge`)**:
  - Ring buffer circular en C++ para **90 estados** capturados cada 6 frames en sesiones locales (desactivado automáticamente en sesiones Online). Permite retroceder el tiempo en vivo manteniendo presionado el botón `<<` en pantalla o el botón asignado del mando físico.

### 4. Editor de Controles Individuales, Motor de Macros (Turbo / Auto-Hold) y Mando Externo
- **Botones 100% Independientes (`HudLayoutManager.java`)**: `D-PAD`, `L`, `R`, `SELECT`, `START`, `X`, `Y`, `B`, `A` y `M1..M4` poseen coordenadas (`posX`, `posY`), escala (`0.5x` a `2.0x`) y opacidad (`20%` a `100%`) individuales en `SharedPreferences`, con cruceta/joystick dinámico flotante donde presiona el dedo.
- **Motor de Macros (`MacroEngine.java`)**: 4 ranuras (`M1..M4`) configurables en **Modo 1 (Repetición Automática / Turbo de 10 ms a 500 ms)** o **Modo 2 (Mantener Presionado / Auto-Hold)** conectadas al bitmask nativo C++.
- **Mapeo Total de Mando Bluetooth/USB (`SOURCE_GAMEPAD` / `SOURCE_JOYSTICK`)**: vincula cualquier botón físico, gatillo (`L2/R2`) o stick (`L3/R3`) a los 12 botones base de SNES, a las 4 acciones de sistema (**Guardar Partida**, **Cargar Partida Rápida**, **Retroceder Tiempo / Rewind**, **Alternar Velocidad / Fast-Forward**) y a los 4 Macros (`M1..M4`).

### 5. Multijugador Hotspot <10 cm Cero Lag (`HotspotRollbackCoordinator.kt`)
- **Hardware Wi-Fi Lock (`WIFI_MODE_FULL_LOW_LATENCY`) + UDP `DSCP EF (0xB8)`**: paquetes binarios de 40 bytes con ventana redundante de los últimos 8 frames de inputs en doble ráfaga por frame y motor C++ `RollbackRingBuffer`.

---

## Compilación

```bash
./gradlew assembleDebug
```

El APK compilado también se encuentra listo para instalar en:
- `apk/MultiSnesByFDP-debug.apk`
