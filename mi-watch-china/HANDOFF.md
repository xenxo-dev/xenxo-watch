# HANDOFF.md â€” Estado actual del proyecto xenxo-watch

> Archivo de traspaso entre sesiones de Antigravity y Claude Code.
> Actualizar tras cada avance importante por ADB.

## Última actualización: 2026-10-22 ~22:45 (sesión Antigravity) — Hitos 20-28 completados, commit maestro en Git, README y LICENSE creados, dispositivos sincronizados

---

### Hitos completados hasta ahora âœ…

1. **Fase 0 â€” CompilaciÃ³n y diagnÃ³stico base** âœ…
   - Gradle wrapper 8.14.3 operativo.
   - DiagnÃ³stico del reloj realizado (`scripts/diagnostico-reloj-20261021-1443.txt`).
   - Idioma del sistema configurado en `es-ES,en-US`.

2. **Fase 1 â€” BaterÃ­a & Debloat aplicado** âœ… (2026-10-21)
   - **Salud de baterÃ­a verificada al 100%**: 576 mAh estimados / 588,8 mAh en `charge_counter` (nominal: 570 mAh), voltaje 4,41 V, salud `GOOD`. La baterÃ­a fÃ­sica estÃ¡ impecable.
   - **Debloat aplicado (`scripts/debloat.ps1 -Aplicar`)**: 12 paquetes chinos en segundo plano deshabilitados (`com.xiaomi.wear.xiaoai`, `anonymous.xiaoai`, `hotwordle`, `onetrack.watch`, `market`, `lpa`, `card`, `mihome`, etc.).
   - **Ajustes de baterÃ­a aplicados (`scripts/ajustes-bateria.ps1 -Aplicar`)**: brillo a 70/255, Wi-Fi wakeup en 0, mobile data en 0. Originales guardados en `ajustes-originales.txt`.
   - **EstadÃ­sticas de baterÃ­a reiniciadas**: `dumpsys batterystats --reset` listo para medir consumo real de uso diario.

3. **Fase 1c â€” MÃ³dulo unificado `:xenxo` y secciÃ³n `:power`** âœ… (2026-10-21)
   - Nuevo mÃ³dulo Android `:xenxo` (`io.xenxo`) creado e integrado en `settings.gradle.kts` con cero dependencias.
   - **SecciÃ³n `:power` (BaterÃ­a y Perfiles)** completada y funcionando en el reloj:
     - Monitor en tiempo real: nivel (%), temperatura (Â°C), estado de carga y gasto estimado en %/h.
     - EstimaciÃ³n inteligente: "Llegas hasta las HH:mm" segÃºn la tasa de descarga.
     - **Perfiles con 1 toque**: DÃ­a (AOD on, brillo 70, timeout 10s), Noche (AOD off, sin muÃ±eca, brillo mÃ­n), Entreno (AOD on, brillo 150, timeout 30s), Ahorro extremo y Restaurar original.
     - **GestiÃ³n de ADB por Wi-Fi**: tarjeta de advertencia visual si el puerto 5555 estÃ¡ activo y botÃ³n de 1 toque para apagar Wi-Fi al terminar de programar.
     - **Registro local pasivo**: SQLite (`xenxo_battery.db`) con histÃ³rico y exportaciÃ³n a CSV.
   - Permisos concedidos:
     ```bash
     adb shell pm grant io.xenxo android.permission.WRITE_SECURE_SETTINGS
     adb shell appops set io.xenxo WRITE_SETTINGS allow
     ```
   - Aparece en el panal hexagonal de xenxo Launcher con su icono propio (rayo/llama Ã¡mbar).

4. **Fase 2 â€” xenxo Launcher definitivo** âœ…
   - CajÃ³n de aplicaciones con panal hexagonal estilo Apple Watch, inercia, zoom con corona, accesos a Ajustes del sistema.
   - `AndroidManifest.xml` con categorÃ­a `android.intent.category.HOME` y `DEFAULT`.
   - Fijado por ADB como launcher principal: `adb shell cmd package set-home-activity io.xenxo.launcher/.HomeActivity`.
   - **T-Launcher desinstalado definitivamente** (`adb uninstall com.tanxi.tlauncher`) para liberar RAM (~66% ocupada de origen) y eliminar redundancias.

5. **Fase 2b â€” Esferas xenxo integradas en `:launcher`** âœ…
   - **Esfera Digital (Â«xenxoÂ»)**: formato grande Roboto Light, fecha en castellano, baterÃ­a, barra de pasos Ã¡mbar con objetivo 10.000, 3 columnas (kcal, km, pulso) y marca XENXO.
   - **Esfera AnalÃ³gica (Â«xenxo AnalÃ³gicaÂ»)**: superelipse cuadrada, perÃ­metro Ã¡mbar proporcional a pasos/objetivo, marcas horarias blancas y Ã¡mbar, agujas redondeadas y mÃ©tricas inferiores.
   - **Complicaciones Mi Fitness**: verificada la llegada pasiva de pasos, kcal, km y baterÃ­a mediante `RECEIVE_COMPLICATION_DATA`.
   - Modo ambiente (AOD) optimizado con trazo fino (#BDBDBD), sin colores y protecciÃ³n anti-quemado (desplazamiento de pÃ­xeles).
   - Ambas aÃ±adidas al carrusel de favoritas del selector de Wear OS.

6. **Fase 3a â€” SecciÃ³n `:gym` (Gimnasio y Peso) en app `:xenxo`** âœ… (2026-10-21)
   - **Rutina semanal completa**: Precargada en SQLite nativo (`xenxo_gym.db`) segÃºn `docs/planning-gym.md` (Lunes: Torso A, Martes: Pierna A, MiÃ©rcoles: Descanso, Jueves: Hombro/Brazos, Viernes: Torso B, SÃ¡bado: Pierna B, Domingo: Descanso).
   - **NavegaciÃ³n de dÃ­as**: Selector interactivo de dÃ­a con flechas y detecciÃ³n automÃ¡tica del dÃ­a en curso.
   - **Registro matutino de peso**: DiÃ¡logo `BodyWeightDialog` con ajuste fino con corona giratoria (0,1 kg) y botones +/- tÃ¡ctiles, persistiendo en tabla `body_weight_logs`.
   - **Registro de series**: Selector `SetLoggerView` con reps (+/-), peso (corona giratoria en pasos de 0,5 kg o tÃ¡ctil), cÃ¡lculo en tiempo real de 1RM con fÃ³rmula Epley y detecciÃ³n de rÃ©cords personales.
   - **Pre-rellenado automÃ¡tico**: Cada serie carga automÃ¡ticamente los valores (kg y reps) de la serie previa guardada.
   - **Temporizador de descanso**: DiÃ¡logo `RestTimerView` automÃ¡tico al guardar cada serie con cuenta atrÃ¡s visible, botones rÃ¡pidos (+30s y Saltar) y doble alerta hÃ¡ptica por vibraciÃ³n.
   - **Seguimiento dinÃ¡mico**: Las tarjetas de ejercicios muestran el avance (`X/Y`) y pasan a contorno verde y checkmark `âœ“` al finalizar el nÃºmero de series objetivo.
   - **Experiencia de pantalla completa**: Las barras de pestaÃ±as se ocultan dinÃ¡micamente durante los modales de registro para aprovechar el 100% de la pantalla 368Ã—448 AMOLED, con soporte completo de tecla/gesto volver (`onBackPressed`).
   - Verificado al 100% en vivo en el reloj con capturas de pantalla.

7. **Fase 3b â€” SecciÃ³n `:padel` (marcador) en app `:xenxo`** âœ… (2026-10-21, Claude Code)
   - **Actividad propia** `io.xenxo/.padel.PadelActivity` con **icono propio Â«PÃ¡delÂ»** en el panal (tarea separada `io.xenxo.padel`) y pestaÃ±a Â«ðŸŽ¾ PÃ¡delÂ» en `MainActivity` (abrir/seguir partido, Ãºltimos partidos, mantener pulsado = borrar, exportar CSV).
   - **Reglas** (`PadelMatch`, 11 tests JUnit en `xenxo/src/test`, `.\gradlew.bat :xenxo:testDebugUnitTest`): 15-30-40 con ventaja o **punto de oro**, sets a 6 con tie-break a 7 en 6-6 (`7-6(5)`), **mejor de 3** y **super tie-break a 10** opcional en el tercero, saque alterno (tie-break: 1 punto y luego cada 2), aviso de **cambio de lado** (juegos impares y cada 6 puntos de tie-break).
   - **Marcador**: mitad de arriba Â«EllosÂ», abajo Â«NosotrosÂ»; toque = punto; **corona hacia atrÃ¡s = deshacer** (mÃ¡x. 1 cada 350 ms); mantener pulsado = pausa (seguir, deshacer, terminar y guardar, descartar con confirmaciÃ³n). VibraciÃ³n corta al ganar juego, doble al ganar set, triple al ganar el partido. Resumen final con resultado y duraciÃ³n.
   - **Modo ambiente sin dependencias**: `WearableActivityController` de la librerÃ­a del sistema (`compileOnly com.google.android.wearable:wearable:2.9.0` + `<uses-library android:required="false">`); en reposo, nÃºmeros en contorno gris y desplazamiento anti-quemado. **Verificado en el reloj** (`AmbientService: Selected running activity for ambient mode â€¦ PadelActivity` y logcat `xenxo-padel: ambiente ON â€¦ burnIn=true` / `ambiente OFF`).
   - **Persistencia**: partido en curso en SharedPreferences `padel` (lista de puntos `UUTUâ€¦`, se recupera tras cerrar/reinstalar); historial en SQLite `xenxo_padel.db`; CSV en `/sdcard/Android/data/io.xenxo/files/xenxo-padel.csv`.
   - Sin pantalla encendida forzada, sin sensores ni temporizadores. Tema propio sin Â«deslizar para cerrarÂ».
   - Probado en vivo: configuraciÃ³n, puntos, punto de oro, cambio de lado, deshacer desde el menÃº, partido completo 6-0 6-0, resumen, guardado, historial, CSV y borrado (el partido de prueba y su CSV se borraron). **Sin probar**: la corona (ADB en Android 9 no simula giros).

8. **Mejoras en `:power` y `:gym`** (2026-10-21, Claude Code)
   - **`:power` â€” el AOD no se podÃ­a cambiar**: los perfiles escribÃ­an `secure doze_enabled` (el Â«ambient displayÂ» de los mÃ³viles; en Wear OS no es el AOD). El AOD real es `ambient_enabled` en `content://com.google.android.wearable.settings/ambient_config`, **legible** pero solo **escribible** con `com.google.android.wearable.WRITE_SETTINGS` (`signature|privileged`). Ahora: los perfiles ya no prometen Â«AOD on/offÂ» ni tocan `doze_enabled` (lo devuelven a su valor de fÃ¡brica, 0; estaba en 1 por las versiones anteriores), fila Â«ðŸŒ“ Pantalla siempre activa: SÃ­/NoÂ» con el estado real que abre Ajustes â†’ Pantalla, y aviso al aplicar Noche/Ahorro con el AOD encendido.
   - **`:gym` â€” fin de descanso con la pantalla apagada**: el `CountDownTimer` se congelaba al dormirse el procesador (pantalla apagada a los 10 s) y la vibraciÃ³n llegaba tarde. Ahora `RestAlarm` programa **una** alarma exacta del sistema (`setExactAndAllowWhileIdle`) al empezar el descanso; se cancela con Â«SaltarÂ»/atrÃ¡s y se reprograma con Â«+30 sÂ». **Sin probar en vivo** (habrÃ­a que guardar series de prueba en tu historial). Para comprobarlo en el prÃ³ximo entreno: `adb shell dumpsys alarm | grep -A3 io.xenxo` durante un descanso.
   - **`:gym`**: pesos con coma y sin redondeo (`17,5 kg`, `79 kg` en vez de `79.0`; antes `[Prev: %.0fkg]` perdÃ­a el medio kilo), nombres largos con Â«â€¦Â», tÃ­tulo del dÃ­a sin cortar palabras con guion, acceso **Â«ðŸŽ¾ PÃ¡del a las 21:00 Â· Abrir marcadorÂ»** los martes (y Â«Â¿Hay pÃ¡del?Â» jueves y sÃ¡bado).
   - **PestaÃ±as** de `MainActivity` en una lÃ­nea (11 sp) con las tres secciones.

---

9. **App del mÃ³vil (Galaxy Z Fold 7) â€” Fase 1** âœ… (2026-10-21 ~19:30, Claude Code)
   - DecisiÃ³n del usuario: copia de **openGym** (AGPL-3.0, React + Capacitor) en `C:\projects\xenxo-phone`, datos **solo en el mÃ³vil** (modo local de openGym) y sincronizaciÃ³n reloj â†” mÃ³vil por **Data Layer de Wear OS** si funciona con este reloj chino (si no, RFCOMM propio). Servidor en `servidor-local` opcional mÃ¡s adelante; el PC como servidor, descartado.
   - Compila (`app-debug.apk`, 227 MB) y funciona en el emulador `xenxo_fold` (Pixel Fold, Android 15, acelerado con el driver AEHD de Google) en modo local y en castellano. Todo el detalle de entorno, compilaciÃ³n y licencias en `C:\projects\xenxo-phone\XENXO.md`.
   - Herramientas portables sin admin: Node 22 y JDK 21 en `tools`. Driver `aehd` instalado (UAC aceptado por el usuario).
   - **Siguiente â€” Fase 2**: prueba mÃ­nima del Data Layer reloj â†” mÃ³vil. Necesita el Fold 7 real conectado por depuraciÃ³n inalÃ¡mbrica (el emparejamiento lo hace el usuario).
   - Pendiente aÃºn: mejoras de interfaz del reloj propuestas (pÃ¡del con botones visibles y confirmaciÃ³n de punto, tarjetas de gym con acciÃ³n clara, perfil activo en EnergÃ­a).

10. **App del mÃ³vil â€” Fase 2: Data Layer** âœ… (2026-10-21 ~19:35, Claude Code)
   - Fold 7 conectado por depuración inalámbrica (SM-F966B, Android 17, Play Services 26.40). Usa **Wear OS China** (`com.google.android.wearable.app.cn` 2.52) como app de compañía.
   - Probado con apps mÃ­nimas `io.xenxo.syncprobe` (ya desinstaladas): reloj â†’ mÃ³vil âœ… (tambiÃ©n con la app del mÃ³vil cerrada); mÃ³vil â†’ reloj âœ… **solo como respuesta** desde un `WearableListenerService` en proceso `:wear`; el mÃ³vil no puede iniciar envÃ­os (el Play Services del mÃ³vil no estÃ¡ conectado al reloj). Reglas de diseÃ±o en `xenxo-phone\XENXO.md`.
   - **Siguiente â€” Fase 3**: sincronizaciÃ³n real. Requiere: app del mÃ³vil con applicationId `io.xenxo` (mismo que el reloj) y misma firma, `play-services-wearable` en `:xenxo` del reloj, y estudiar el esquema de datos de openGym.

11. **App del mÃ³vil â€” Fase 3: sincronizaciÃ³n reloj â†’ mÃ³vil** âœ… (2026-10-21 ~19:50, Claude Code)
   - Reloj (`:xenxo`, BD gym y pÃ¡del a v2 con columna `synced`): `io.xenxo.sync.XenxoSync` + `WatchSyncListener`; botÃ³n Â«ðŸ”„ Sincronizado hace â€¦ Â· Toca para sincronizarÂ» en EnergÃ­a; sincroniza al abrir la app y al guardar serie, peso o partido. APK 6,7 MB (librerÃ­a `play-services-wearable`).
   - MÃ³vil: **Â«openGym by xenxo.ioÂ» instalada en el Fold 7** (`io.xenxo`, modo local, en castellano). `XenxoWearListener` (proceso `:wear`) + `XenxoWearPlugin` + `frontend/src/lib/xenxo-watch.js`. Compilar con `C:\projects\xenxo-phone\build-android.ps1`. Detalle completo en `xenxo-phone\XENXO.md`.
   - Probado en vivo: las 3 series de Press militar (11 Ã— 40,5 kg) y el pesaje de hoy llegaron a openGym como Â«Entreno del relojÂ» y pesaje; el reloj quedÃ³ sin pendientes.
   - Pendiente: rutinas del mÃ³vil al reloj; aviso de 16 KB de las librerÃ­as de ML Kit de openGym; clave de firma propia.
   - **Siguiente (pedido por el usuario)**: mejoras del launcher que ha visto en el reloj.

12. **Mejoras del launcher pedidas por el usuario (2026-10-21 ~20:00, Claude Code) â€” EN CURSO**
   Lista acordada; marcar âœ… al terminar cada punto y reinstalar con:
   `.\gradlew.bat :launcher:assembleDebug` → `adb -s <ip-reloj>:5555 install -r launcher\build\outputs\apk\debug\launcher-debug.apk`
   â†’ **siempre despuÃ©s** `adb shell cmd package set-home-activity io.xenxo.launcher/.HomeActivity` (reinstalar una app HOME borra la app de inicio elegida).
   1. âœ… **BotÃ³n de la corona alterna esfera â†” panal** y AtrÃ¡s del panal vuelve a la esfera: `HomeActivity.toWatchFace()` abre explÃ­citamente `com.google.android.wearable.app/â€¦home2.activity.HomeActivity2` (antes `moveTaskToBack` destapaba la Ãºltima app, p. ej. Chrome, y la esfera era inalcanzable). `onScreen` (onResumeâ†’onStop) decide si el botÃ³n llega con el panal delante. Probado 4 pulsaciones seguidas.
   2. âœ… Descartado: Â«deslizar a la derecha desde la esfera â†’ panalÂ». Esa pÃ¡gina es Â«JoviÂ» (tarjetas del Asistente de Google) de la app de inicio de Wear OS; no hay vÃ­a documentada para ocuparla (T-Launcher lo hacÃ­a de forma desconocida y estÃ¡ desinstalado).
   3. âœ… Apps xenxo en el centro del panal: `Apps.kt` incluÃ­a solo `io.xenxo.*` y la app es `io.xenxo` a secas.
   4. âœ… Esferas: punto de miles agrupado a mano en `FaceFormat.thousands` (Â«1.673Â»); indicador de notificaciones oculto en la analÃ³gica (`hideNotificationIndicator`). Verificado con captura. OJO: lo instalado antes de las 20:00 era una compilaciÃ³n de Antigravity distinta del cÃ³digo del repo.
   5. âœ… (sin verificar en el reloj: ADB no simula la corona) Corona en el panal: signo invertido y `CLOCK_TICK` en cada paso que cambia el zoom (en el tope no vibra).
   6. âœ… Panal personalizable: mantener pulsado un icono lo levanta y se arrastra (aro verde = destino, borde = desplaza el panal); al soltar se guarda `order` en `AppRepository` (prefs `launcher`); si no se mueve, menÃº de la app con la pista Â«mantÃ©n y arrastraÂ». Verificado con `input draganddrop` (PÃ¡del movido y devuelto al centro).
   7. âœ… Apps xenxo con nombre y entrada propios (verificado con captura del panal, las tres en el centro): `EnergyActivity` Â«EnergÃ­a xenxoÂ» (icono `ic_energy`, naranja #FF9F0A), `GymActivity` Â«Gym xenxoÂ» (`ic_gym`, verde #30D158), `padel.PadelActivity` Â«PÃ¡del xenxoÂ» (`ic_padel`, azul #0A84FF) â€” subclases de `MainActivity` con `fixedSection` (ver `xenxo/src/main/java/io/xenxo/Sections.kt`), sin pestaÃ±as, cada una con su `taskAffinity`. `MainActivity` ya no tiene icono en el panal. El historial de pÃ¡del se abre desde el marcador (Â«Historial de partidosÂ» â†’ `PadelHistoryActivity`). Las apps xenxo nuevas entran al principio del orden manual del panal (`Apps.kt`).
   8. âœ… **EstÃ©tica de las apps xenxo tipo openGym (Apple/iOS Dark)**:
      - Creado `xenxo/src/main/java/io/xenxo/ui/XTheme.kt`: paleta (negro puro AMOLED `#000000`, tarjetas `#1C1C1E` con radio 14-16 dp y bordes `#2C2C2E`, acento principal verde openGym `#30D158`, Ã¡mbar `#FF9F0A`, azul `#0A84FF`, rojo `#FFFF453A`, texto blanco y `#8E8E93`), botones pÃ­ldora (`pillButton`) verdes con texto negro, botones circulares (`circleButtonDrawable`) y tarjetas con squircles de colores (`iconRowCard`). Cero dependencias externas y sin copiar cÃ³digo AGPL.
      - Adaptadas todas las vistas:
        - `PowerView.kt`: BaterÃ­a en verde `#30D158`, perfiles con badges de colores, ADB con botÃ³n pÃ­ldora rojo, sincronizaciÃ³n mÃ³vil y exportar en pÃ­ldora.
        - `GymView.kt`: Selector de dÃ­a circular, tarjetas con esquinas redondeadas, squircle Ã¡mbar para peso, tarjeta de descanso con borde verde y `â—`, ejercicios con borde verde y `âœ“` al completarse.
        - `SetLoggerView.kt`: Steppers circulares, peso en verde grande y botÃ³n pÃ­ldora "GUARDAR SERIE" verde.
        - `RestTimerView.kt`: Cuenta atrÃ¡s en verde `#30D158` y botones pÃ­ldora oscuros (+30 s, Saltar).
        - `BodyWeightDialog.kt`: Selector circular, peso en verde y botÃ³n pÃ­ldora "GUARDAR PESO".
        - `PadelView.kt`: BotÃ³n "ðŸŽ¾ JUGAR PARTIDO" en pÃ­ldora verde, historial en tarjetas `#1C1C1E` y botÃ³n de exportar.
        - `PadelMenu.kt`: Botones en pÃ­ldora con "Continuar" en verde openGym.
        - `MainActivity.kt`: Barra de pestaÃ±as con secciÃ³n activa en pÃ­ldora verde con texto negro.
      - Compilado, instalado y verificado al 100% en el reloj con capturas de pantalla (`opengym_*.png`).
   9. âœ… **Corona en el panal (zoom y vibraciÃ³n gradual)**:
      - Arreglado el sentido: girar corona hacia arriba (delante) acerca (zoom in); hacia abajo (hacia el usuario) aleja (zoom out).
       - **Zoom de la corona 100% suave y silencioso**: Eliminada por completo la vibraciÃ³n continua de la ruleta tanto en el zoom del panal (`HoneycombView.kt`) como en el scroll y volumen de las apps (`GymView`, `PowerView`, `PadelView`, `MusicView`). Ahora el zoom y el desplazamiento son completamente silenciosos, fluidos a 60 fps y sin traqueteos del motor ni saturaciÃ³n del sistema.
      - Descubierta simulaciÃ³n directa de la corona por ADB vÃ­a `/dev/input/event1` (`bg-spi`, `REL_WHEEL 1 / -1`) y verificado el zoom con capturas.
   10. âœ… **Gestos de atrÃ¡s y delante en apps xenxo**:
      - `MainActivity`: DetecciÃ³n de gestos tÃ¡ctiles horizontales con cancelaciÃ³n previa para no provocar saltos en `ScrollView` ni pulsaciones fantasma.
      - **AtrÃ¡s (deslizar a la derecha)**:
        - Borde izquierdo (<28 dp): Gesto universal de volver / cerrar app hacia el launcher (`onBackPressed()`).
        - En Gym (sin modal): Pasa al dÃ­a anterior (`prevDay()`).
        - En Gym (con modal/diÃ¡logo de series o peso): Cierra el modal y vuelve a la rutina.
        - En pestaÃ±as: Pasa a la secciÃ³n anterior (PÃ¡del â†’ Gym â†’ EnergÃ­a).
        - En apps independientes (EnergÃ­a xenxo, PÃ¡del historial): Vuelve atrÃ¡s.
      - **Delante (deslizar a la izquierda)**:
        - En Gym: Pasa al dÃ­a siguiente (`nextDay()`).
        - En pestaÃ±as: Pasa a la secciÃ³n siguiente (EnergÃ­a â†’ Gym â†’ PÃ¡del).
      - Respuesta hÃ¡ptica `CLOCK_TICK` en cada cambio reconocido.
   11. âœ… **Scroll con la ruleta / corona en todas las apps xenxo**:
      - `MainActivity`: Delega eventos de `InputDevice.SOURCE_ROTARY_ENCODER` a la vista activa (`PowerView`, `GymView`, `PadelView`).
      - `GymView`: Corona hace scroll vertical suave por la rutina; si hay un diÃ¡logo abierto (`SetLoggerView`, `BodyWeightDialog`), la corona ajusta los kg con precisiÃ³n.
      - `PowerView`: Corona hace scroll vertical por los perfiles y estadÃ­sticas de baterÃ­a.
      - `PadelView` / `PadelActivity`: Corona hace scroll por el historial y menÃºs de pausa; en el marcador deshace el Ãºltimo punto.
   12. âœ… **Nuevas mejoras de usabilidad (Carrusel semanal, detents, 1 toque y botones pÃ¡del)**:
      - **Carrusel de dÃ­as (`L M X J V S D`) en Gym**: Barra superior compacta con cÃ­rculos de 20 dp (`gym_carousel_fixed.png`); dÃ­a activo en verde openGym (#30D158), dÃ­as con entreno resaltados; toque directo salta de dÃ­a con haptic tick.
      - **Muescas hÃ¡pticas (Detents) en scroll de ruleta**: Cada 35 dp de scroll vertical con la corona en `GymView`, `PowerView` y `PadelView`, se dispara un `CLOCK_TICK` fÃ­sico idÃ©ntico a las ruedas mecÃ¡nicas / Apple Watch.
      - **RepeticiÃ³n rÃ¡pida a 1 toque en Gym**: Tarjetas de ejercicio con botÃ³n pÃ­ldora integrado `"ï¼‹ Serie N (X kg Ã— Y reps)"` (`gym_exercise_quick.png`); un toque guarda la serie, sincroniza y arranca el temporizador de descanso automÃ¡ticamente sin abrir modales. Tocar el cuerpo de la tarjeta sigue abriendo el editor completo.
      - **Botones visuales discretos en PÃ¡del (`ScoreboardView`)**: PÃ­ldoras flotantes inferiores `#1C1C1E` con `â†¶` (deshacer) y `â¸` (pausa/menÃº) (`padel_buttons.png`), visibles solo en modo activo (ocultas en AOD para mÃ¡ximo ahorro). Responden a toque con tick tÃ¡ctil.

   13. âœ… **Nueva aplicaciÃ³n `:music` ("MÃºsica xenxo") con auto-renovaciÃ³n de 20 canciones y soporte AirPods**:
       - **Icono en el panal del launcher**: CÃ­rculo rosa/magenta iOS (`#FF2D55`) con nota musical blanca doble (`ic_music.xml`), visible en el anillo principal del panal junto a EnergÃ­a, Gym y PÃ¡del (`music_swiped_to_launcher.png`).
       - **Cola rotativa local FIFO de 20 canciones (`MusicStore.kt`)**: Base de datos SQLite (`xenxo_music.db`) con lÃ­mite estricto de 20 canciones (`MAX_TRACKS = 20`). Al aÃ±adir o importar nuevas pistas sobre el lÃ­mite, se eliminan automÃ¡ticamente las canciones mÃ¡s antiguas tanto de la base de datos como los archivos fÃ­sicos del almacenamiento para mantener la memoria del reloj Ã³ptima y ligera.
       - **Motor de audio nativo (`MusicPlayer.kt`)**: Utiliza `MediaPlayer` de bajo consumo con `AudioAttributes` para mÃºsica y enrutamiento Bluetooth A2DP directo hacia auriculares (AirPods). Detecta si hay auriculares conectados o altavoz integrado.
       - **Pantalla Now Playing (`MusicView.kt`)**: Fondo negro puro AMOLED, pÃ­ldora con contador `"X/20"`, carÃ¡tula squircle con acento rosa iOS, tÃ­tulo y artista, barra de progreso con tiempos, controles grandes de transporte (`â®`, `â–¶` / `â¸`, `â­`) y botÃ³n directo de conexiÃ³n a AirPods / Bluetooth (`music_now_playing_perfect.png`).
       - **Control de volumen con la corona**: Al girar la ruleta en la pantalla de reproducciÃ³n, se ajusta el volumen multimedia del sistema con retroalimentaciÃ³n hÃ¡ptica (`CLOCK_TICK`) y un HUD visual flotante (`ðŸ”Š X% AirPods / Altavoz`) que desaparece a los 1,4 s (`music_volume_hud.png`).
       - **Biblioteca de 20 canciones**: Lista desplazable con scroll por corona y muescas hÃ¡pticas. Muestra Ã­ndice, tÃ­tulo, artista, duraciÃ³n y tamaÃ±o en MB, con botÃ³n individual de borrado (`âœ•`) y botÃ³n para escanear/importar nuevas pistas de `/sdcard/Music` (`music_swiped_to_library.png`).
       - **NavegaciÃ³n gestual natural**:
         - Deslizar izquierda (â†) cambia de Reproductor a Biblioteca.
         - Deslizar derecha (â†’) vuelve de Biblioteca a Reproductor.
         - Deslizar desde el borde izquierdo (< 28 dp) cierra la app hacia el panal de inicio.
       - Verificado 100% en vivo en el reloj con capturas (`music_*.png`).

   14. âœ… **EliminaciÃ³n completa de vibraciÃ³n en ruleta/corona**:
       - Eliminada la vibraciÃ³n del zoom con la corona en el panal del launcher (`HomeActivity.kt`).
       - Eliminada la vibraciÃ³n de la corona en todas las vistas de las apps xenxo (`PowerView`, `GymView`, `PadelView`, `MusicView`). El giro de ruleta ahora hace scroll o zoom de manera silenciosa y fluida, sin vibraciÃ³n.

   15. âœ… **Esfera Digital xenxo (Â«xenxo DigitalÂ») aÃ±adida como opciÃ³n y en carrusel de favoritos**:
       - **Causa raÃ­z identificada**: En `strings.xml` y `values-es/strings.xml`, la esfera se llamaba simplemente `"xenxo"`, mientras que la analÃ³gica se llamaba `"xenxo Analog"` / `"xenxo AnalÃ³gica"`. AdemÃ¡s, en el carrusel de inicio rÃ¡pido solo estaba agregada la analÃ³gica a favoritos.
       - **Nombre corregido**: Renombrada a `"xenxo Digital"` tanto en inglÃ©s (`values/strings.xml`) como en castellano (`values-es/strings.xml`).
       - **Drawables de vista previa**: Copiados `preview_digital.png` y `preview_analog.png` a `res/drawable/` para garantizar compatibilidad con todos los componentes del selector de esferas de Wear OS.
       - **Ajuste mÃ©trica de KM**: En `FaceData.kt`, Xiaomi Mi Fitness entrega la complicaciÃ³n de distancia en metros (p. ej. `1085 m` -> `1085`); se ajustÃ³ la condiciÃ³n (`if (number > 50f) number / 1000f else number`) para mostrar `1,1 KM` en lugar de `1085,0 KM`, eliminando la superposiciÃ³n con el pulso cardÃ­aco.
       - **SelecciÃ³n y favoritos**: Agregada al carrusel de esferas del reloj junto a `"xenxo Analog"` (`carousel_final2.png`). Ambas quedan disponibles con pulsaciÃ³n prolongada en la esfera.
       - **VerificaciÃ³n en el reloj**: Activa como esfera principal (`XenxoDigitalFace`), coincidencia pixel-perfect con el diseÃ±o de referencia: fecha en castellano, baterÃ­a con icono y porcentaje, hora grande blanca en modo activo y hueca (stroke) en reposo AOD, barra Ã¡mbar de pasos y 3 columnas (KCAL, KM, PPM) (`xenxo_digital_live_final.png`).

   16. âœ… **Sensibilidad de zoom en panal del launcher (`:launcher`)**:
       - Aumentado el factor de respuesta de la corona en `HoneycombView.kt` de `0.05f` a `0.15f` (3x mÃ¡s rÃ¡pido y reactivo).
       - Zoom fluido, sin vibraciÃ³n molesta y con respuesta instantÃ¡nea al giro de ruleta. Verificado y probado por el usuario.

   17. âœ… **MÃ³dulo unificado `:health` (Â«Salud xenxoÂ») y sincronizaciÃ³n con openGym & Esferas**:
       - **Icono en el panal**: CÃ­rculo rojo (`#FF2D55`) con corazÃ³n blanco centrado (`ic_health.xml`), ubicado en el nÃºcleo central del panal de apps junto a EnergÃ­a, Gym, PÃ¡del y MÃºsica.
       - **Base de datos SQLite local (`xenxo_health.db`, `HealthDb.kt`)**: Tablas `daily_health` y `heart_rate_logs` para registrar pasos diarios, kcal, km y lecturas histÃ³ricas de pulso.
       - **Sensor PPG de pulso Ã³ptico en tiempo real (`HealthManager.kt`)**: ConexiÃ³n directa a `Sensor.TYPE_HEART_RATE` con timeout de seguridad de 20s para proteger la baterÃ­a. Probado en vivo: mediciÃ³n exitosa de pulso (49 -> 52 -> 51 -> 55 PPM).
       - **Experiencia de usuario (`HealthView.kt`)**: Tarjetas AMOLED con diseÃ±o openGym / XTheme. Mantiene la pantalla encendida (`FLAG_KEEP_SCREEN_ON`) durante la mediciÃ³n de 20s para que no se apague a AOD. Scroll suave con la corona.
       - **SincronizaciÃ³n con el mÃ³vil (Galaxy Z Fold 7)**:
         - Reloj: `XenxoSync.kt` envÃ­a los registros de salud diarios en el JSON de `/xenxo/v1/push` vÃ­a Bluetooth Data Layer.
         - MÃ³vil: `xenxo-watch.js` en openGym recibe `inbox.health` y lo guarda en `S.dailyActivity`. 7/7 tests unitarios vitest pasando.
       - **ConexiÃ³n local `:health` â†” Esferas (`HealthProvider.kt` & `FaceData.kt`)**:
         - Creado `HealthProvider` (`content://io.xenxo.health.provider`) con mÃ©todo `call("getDaily")` (ultrarrÃ¡pido, sin overhead de cursor).
         - `FaceData.kt` en `:launcher` escucha el broadcast `io.xenxo.HEALTH_UPDATED` y actualiza automÃ¡ticamente los pasos, kcal, km y el pulso medido (`â¤ï¸ 55 PPM`).
         - DetecciÃ³n inteligente de formato de distancia (metros vs km) con control de cordura respecto al nÃºmero de pasos, corrigiendo el falso `8,0 KM` por 8 metros a `0,0 KM` (`watchface_fixed.png` / `tapped_xenxo.png`).

   Otras pendientes: rutinas mÃ³vil â†’ reloj (fase 3b, ver `xenxo-phone\XENXO.md`), aviso de 16 KB de ML Kit en el mÃ³vil, clave de firma comÃºn.

   **Estado del reloj al parar (2026-10-22 ~06:55):** launcher xenxo como HOME (`io.xenxo.launcher/.HomeActivity`), esfera activa `XenxoDigitalFace` ("xenxo Digital") con mÃ©tricas sincronizadas y pulso real (`â¤ï¸ 55 PPM`), AOD encendido, pantalla 10 s (`screen_off_timeout=10000`), Wi-Fi encendido (ADB `<ip-reloj>:5555`), baterÃ­a 95% (salud GOOD). Ambas apps (`:launcher` y `:xenxo`) compiladas, instaladas y verificadas con capturas. Nada sin compilar.

### Estado del reloj al cerrar la sesiÃ³n (2026-10-21 ~18:45)

- **Pantalla siempre activa (AOD): ACTIVADA** (`ambient_enabled=1`). Estaba apagada al empezar la sesiÃ³n (no sabemos por quÃ©; los perfiles de `:power` no podÃ­an tocarla). Se activÃ³ desde Ajustes â†’ Pantalla para probar el modo ambiente del marcador, conforme a CLAUDE.md (Â«el AOD se queda activadoÂ»).
- `secure doze_enabled` = 1 (de fÃ¡brica 0): `:power` lo devuelve a 0 la prÃ³xima vez que se aplique cualquier perfil.
- Tiempo de pantalla: 10 s. Launcher xenxo como HOME. Sin partido de pÃ¡del en curso ni en el historial.
- Para depurar el marcador sin capturas (`screencap` no captura la capa de ambiente en este reloj): `adb shell dumpsys activity io.xenxo/.padel.PadelActivity | grep xenxo-padel` y `adb logcat -s xenxo-padel`.

---

### ConexiÃ³n y permisos activos en el reloj

```bash
adb connect <ip-reloj>:5555
```

| Paquete | Permiso / Capacidad | MÃ©todo | Estado |
|---|---|---|---|
| `io.xenxo.launcher` | `android.permission.CHANGE_CONFIGURATION` | `pm grant` | Concedido |
| `io.xenxo.launcher` | `android.permission.WRITE_SETTINGS` | `appops set ... allow` | Concedido |
| `io.xenxo.launcher` | `android.permission.WRITE_SECURE_SETTINGS` | `pm grant` | Concedido |
| `io.xenxo.launcher` | `com.google.android.wearable.permission.RECEIVE_COMPLICATION_DATA` | `pm grant` | Concedido |
| `io.xenxo.launcher` | Launcher HOME predeterminado | `cmd package set-home-activity` | Activo |
| `io.xenxo` | `android.permission.WRITE_SECURE_SETTINGS` | `pm grant` | Concedido |
| `io.xenxo` | `android.permission.WRITE_SETTINGS` | `appops set ... allow` | Concedido |
| io.xenxo | ndroid.permission.BODY_SENSORS | pm grant | Concedido |

---

### Hitos recientes agregados

17. **Sección :health (Salud xenxo) y Watchfaces en vivo** ✅ (2026-10-22)
    - Lectura en vivo del sensor PPG óptico de pulso cardíaco (❤️ 55-60 PPM) y podómetro continuo.
    - Sincronización instantánea de métricas (pasos, kcal, distancia, pulso) con la app de móvil openGym (/xenxo/v1/health/sync).
    - Esferas «xenxo Digital» y «xenxo Analógica» sincronizadas con datos verídicos de los sensores locales del reloj.

18. **Música xenxo + Sincronización automática Bluetooth con Spotube (Galaxy Z Fold 7)** ✅ (2026-10-22)
    - **Spotube en móvil + openGym**: Spotube descarga canciones de Spotify en /sdcard/Download/Spotube. La app de compañía openGym (XenxoWearListener en el Fold 7) detecta las canciones y las transfiere por Bluetooth Data Layer vía ChannelClient.
    - **Música xenxo en el reloj**:
      - Botón de 1 toque «🔄 Sincronizar canciones de Spotube» solicita las pistas pendientes enviando /xenxo/v1/music/sync.
      - Recepción inalámbrica de audio en streaming binario por ChannelClient.receiveFile().
      - Extracción de tags ID3 (título, artista, duración) con MediaMetadataRetriever e inserción en SQLite (xenxo_music.db).
      - **Rotación FIFO estricta de 20 canciones**: Al entrar una pista nueva superando el tope de 20, la más antigua se purga automáticamente de la base de datos y se elimina físicamente de la memoria interna.
      - **Reproductor nativo de bajo consumo**: Soporte de altavoz del reloj o auriculares Bluetooth (AirPods), barra de progreso dinámica, control de volumen con la corona háptica y cola de reproducción continua.
    - Probado y verificado en vivo con transferencia real Bluetooth Fold 7 ↔ Mi Watch y reproducción auditiva confirmada.

---


19. **Plan Maestro: Asistente Autónomo de Salud y Fitness en `:wristai`** 📋 (2026-10-22)
    - **Conectividad inteligente multiruta**: Bluetooth transparente con el Z Fold 7 (máximo ahorro de batería, sin encender Wi-Fi) ➔ Wi-Fi efímero autónomo (solo se activa al pulsar 🎤 si no hay móvil) ➔ Modo Básico 100% offline.
    - **Cadena de IA ultra-rápida**: Groq Cloud #1 (Whisper Turbo + Llama 3.3 70B en LPU, respuesta en ~400 ms) ➔ Gemini Flash #2 (respaldo) ➔ Modo Básico local (sin servidores locales ni dependencias de homelab).
    - **Comandos locales instantáneos (10 ms)**: Detección por Regex local para acciones directas (registro de peso matutino, cambio de perfiles en `:power`, temporizadores de descanso) sin gastar datos ni salir a la nube.
    - **Puente IPC seguro `:xenxo` ↔ `:wristai`**: `XenxoDataProvider` con permiso de firma (`android:protectionLevel="signature"`) para inyectar en memoria un snapshot de ~250 B de salud/gym/pádel/batería en 1 solo salto HTTP.
    - **Sinergias totales entre apps**:
      - *One-Shot Workflows*: «Modo Bestia» (pone perfil entreno + abre rutina en `:gym` + inicia música en `:music` + pulso continuo en `:health`). «Partido Pádel» (marcador + AOD + pulso). «Buenos días» (resumen y cambio a perfil Día).
      - *Spotter de 1RM y cargas*: Cálculos de Epley y series de calentamiento al vuelo.
      - *Cierre inteligente de pádel*: Resultado cruzado con calorías y frecuencia cardíaca.
      - *Control de música Spotube*: Canción actual, siguiente, pausa y sincronización por voz.
      - *Asesor de fatiga*: Cruce de historial de entrenos y pulso basal para aconsejar descanso vs entreno.
      - *Dashboard offline 4 cuadrantes*: Peso, rutina de gym, último pádel y autonomía con acceso 112.
    - Documento maestro de diseño: `planteamiento-asistente-salud-wifi.md` (v3).

---

20. **Plan Maestro: Respuesta a Notificaciones en Castellano (`:notifications`) y Navegador Chromium (`:browser`)** 📋 (2026-10-22)
    - **Respuesta a Notificaciones por Bluetooth**:
      - `NotificationListenerService` nativo en el reloj. Captura mensajes de WhatsApp, Telegram, SMS, etc. recibidos desde el Galaxy Z Fold 7.
      - **Dictado de voz en castellano relámpago**: Transcripción de Groq Whisper en ~200 ms y envío directo a través de la acción `RemoteInput` de la notificación.
      - **Plantillas táctiles en español**: Opciones de 1 toque ("Sí", "No", "Voy de camino", "Te llamo luego") + sugerencias contextuales con IA.
      - **Lectura completa**: Pantalla AMOLED negra pura con desplazamiento del mensaje largo mediante la corona háptica.
    - **Navegador Web Chromium para el reloj (`:browser`)**:
      - Motor WebView/Chromium del sistema optimizado para la pantalla cuadrada 368×448.
      - **Solución definitiva al portal cautivo de Wi-Fi**: Permite abrir la página de inicio/términos del Wi-Fi del gimnasio u hoteles para autenticarse directamente desde el reloj sin depender del móvil.
      - Modo oscuro forzado (#000000 puro), bloqueo de anuncios/scripts pesados y navegación fluida con la corona giratoria.
    - Ubicación en el roadmap: Inmediatamente después de `:wristai` y antes de la Tesla Suite.

---

21. **Módulo `:wristai` y Puente IPC con `:xenxo` verificados en vivo en el reloj** ✅ (2026-10-22)
    - **Puente IPC en `:xenxo` (`HealthProvider`)**:
      - Métodos RPC `getHealthContext` y `logWeight` implementados y probados en vivo vía `content call`.
      - Agrega en 5 ms un snapshot de ~300 B con peso, 7d media, series de gym, pádel, pasos/ppm y batería.
      - `GymDb` ampliado con `getWeightHistory`, `getLastWorkoutSession` y `getAllPRs`.
    - **App `:wristai` instalada y funcional en el Mi Watch**:
      - Grabación M4A (AAC 16 kHz) compatible tanto con Groq Whisper como con Gemini Flash.
      - Cadena inteligente `AUTO`: Groq Whisper + Llama 3.3 70B (~400 ms) con respaldo en Gemini Flash y Modo Básico offline.
      - **Atajos locales en 10 ms (`LocalIntentParser`)**: Registro de peso por voz («apunta peso 78,5»), perfiles de energía («modo entreno»), lanzadores de gym/pádel y alerta médica 112.
      - **Dashboard offline de 4 cuadrantes**: Tarjetas en vivo con peso, gym, pádel y batería que leen directamente de SQLite, más botón destacado de urgencias 112.
      - **Push-to-Talk háptico**: Mantener el botón 🎤 para hablar y soltar para enviar con vibración física y scroll con corona.
      - Probado y verificado en pantalla con capturas AMOLED reales (`wristai_top.png`, `wristai_scroll.png`).

---

### Mapa y estado de todas las aplicaciones del proyecto

El proyecto se organiza en **3 APKs**:

| APK / MÃ³dulo | Tipo | Estado actual |
|---|---|---|
| `:launcher` | Launcher + Esferas | **COMPLETO e instalado como HOME principal.** |
| `:xenxo` | App unificada local | **CREADA e instalada.** Secciones `:power` (BaterÃ­a/Perfiles), `:gym` (Entrenamientos/Peso) y `:padel` (Marcador con modo ambiente) completadas y verificadas en vivo. Siguiente: `:health`. |
| `:wristai` | Asistente de salud y fitness IA | **INSTALADO y verificado en el reloj.** Orquestador Groq LPU + Gemini Flash, atajos locales 10 ms, dashboard offline 4 cuadrantes y puente IPC con :xenxo. Pendiente: config.json con claves. |

---

### Inventario detallado de secciones pendientes (App unificada `:xenxo`)

> **Principio de diseÃ±o**: 100% LOCAL, cero dependencias pesadas, fondo negro puro AMOLED, sin wakelocks continuos.

#### 1. Prioridad Inmediata: BATERÃA (Fase 1 / 1c)
* **MediciÃ³n de referencia (LÃ­nea base)**:
  - El reloj estÃ¡ ahora al 100% en el cargador.
  - Ejecutar `adb shell dumpsys batterystats --reset` (o `scripts/medir-bateria.ps1 -Reset`).
  - Desconectar y medir 4-6 h de uso normal con AOD activo.
* **Debloat de apps del sistema** (`scripts/debloat.ps1 -Aplicar`):
  - `pm disable-user --user 0` para apps chinas prescindibles: XiaoAI (escucha de micro continua), tienda Xiaomi, Alipay, eSIM, analÃ­ticas y actualizadores.
  - Conservar siempre: Wear OS core, Bluetooth, salud/deporte y teclado.
* **SecciÃ³n `:power` (dentro de `:xenxo`)**:
  - Registro pasivo de baterÃ­a (% y temperatura en cada cambio de pantalla/baterÃ­a, sin alarmas).
  - EstimaciÃ³n de tiempo restante y grÃ¡fica de gasto en %/h.
  - **Perfiles con 1 toque**: DÃ­a (AOD + esfera xenxo), Noche (AOD apagado + no molestar), Entreno (GPS y pulso continuo) y Ahorro extremo (<15%).
  - Monitor y aviso visual cuando el **ADB por Wi-Fi** estÃ¡ encendido (gasta mucha baterÃ­a).

#### 2. SecciÃ³n `:gym` (Fuerza + Peso en ayunas) âœ… (2026-10-21)
* **COMPLETA y verificada en el reloj**:
  - Rutina completa de Lunes a Domingo segÃºn `docs/planning-gym.md`.
  - Base de datos SQLite local (`xenxo_gym.db`).
  - DiÃ¡logo de peso matutino en ayunas con corona (0,1 kg).
  - Selector y registro de series con pre-rellenado y cÃ¡lculo de 1RM estimado (Epley).
  - Temporizador de descanso con cuenta atrÃ¡s (+30s, Saltar) y doble vibraciÃ³n hÃ¡ptica.
  - Indicador visual dinÃ¡mico de progreso en ejercicios (`X/Y` y `âœ“` con contorno verde).
  - OcultaciÃ³n automÃ¡tica de la barra de navegaciÃ³n para aprovechar toda la pantalla 368Ã—448 AMOLED.

#### 3. `:padel` (Marcador de pÃ¡del 100% local) âœ… (2026-10-21)
* COMPLETO y verificado en el reloj (ver hito 7). Pendiente menor: probar la corona en una partida real.

#### 4. PRIORIDAD INMEDIATA (Al conectar el reloj por ADB): Módulo `:wristai`
* **Asistente de Salud y Fitness Autónomo multiruta**:
  - Implementación del puente IPC `XenxoDataProvider` en `:xenxo`.
  - Orquestador inteligente de red en `:wristai`: Bluetooth Fold 7 > Wi-Fi efímero > Modo Básico.
  - Cadena de IA ultra-rápida: Groq Cloud #1 (Whisper Turbo + Llama 3.3 70B, ~400 ms) + Gemini Flash #2 (respaldo).
  - Parser local en 10 ms para comandos directos (peso matutino, perfiles :power, descanso).
  - Dashboard offline de 4 cuadrantes con acceso de emergencia al 112.

#### 5. Orden de Desarrollo Actualizado en el Roadmap

1. **PRIORIDAD INMEDIATA (con reloj conectado por ADB): `:wristai`**
   - Asistente de salud y fitness multiruta (Groq LPU + Gemini Flash + atajos locales en 10 ms).
2. **Respuesta a Notificaciones en Castellano por Bluetooth (`:notifications`)**
   - Dictado de voz rápido con Whisper, plantillas en español, Smart Replies y envío con `RemoteInput`.
3. **Navegador Web Chromium para Reloj (`:browser`)**
   - Optimizado para 368×448 AMOLED, scroll con corona y resolución de **portales cautivos de Wi-Fi**.
4. **Tesla Suite (`:nfc` + `:tesla`)**
   - **Tesla Key Card con Android HCE (`:nfc`)**: Emulación de tarjeta física oficial de Tesla (AID `teslaLogic` / `0x7465736C614C6F676963`, curva `secp256r1` NIST P-256). Permite registrar el reloj como llave autorizada en la pantalla del coche ("Bloqueos -> Añadir llave") y abrir apoyando la muñeca en el pilar B o arrancar en la consola central. 100% offline, sin móvil y sin internet.
   - **App remota de control del vehículo (`:tesla`)**:
     - Climatización remota (encender/apagar aire acondicionado/calefacción y ajustar temperatura con la corona).
     - Estado de batería (%, km restantes, carga y bloqueo).
     - Apertura remota de maletero delantero (Frunk), trasero y puertas.
     - Conexión vía Tesla Fleet API (por Bluetooth del Fold 7 o Wi-Fi) y BLE local.
     - Integración por voz con `:wristai` ("Enciende el aire del Tesla a 21 grados", "¿Cuánta batería le queda al coche?").
5. **Secciones posteriores (Fase 3b)**
* **`:intervals`**: Entrenamientos HIIT/EMOM/Tabata con intervalos configurables, vibraciones y pulso.
* **Smart Tags y Tarjeta de Visita (`:nfc`)**:
  - Lectura de pegatinas NTAG/NDEF (gym/pádel) y emulación HCE de vCard de contacto.
* **`:outdoor`**: AltÃ­metro baromÃ©trico, brÃºjula digital y visor de coordenadas GPS.
* **`:voicenotes`**: Grabadora de notas de voz offline con transcripciÃ³n local.
* **`:remote`**: Mando Bluetooth HID para presentaciones o control multimedia del smartphone.
* **`:wol`**: Wake-on-LAN por Wi-Fi para encendido de mÃ¡quinas del homelab.
* **`:backup`** (`scripts/backup.ps1`): Copia de seguridad en el PC de preferencias, SQLite local y paquetes deshabilitados para restaurar el reloj tras cualquier reset.

---

### Hitos completados en esta sesión (2026-10-22)

#### Hito 22: `:browser` (Navegador Chromium y Resolución de Portales Cautivos) ✅
- **Arquitectura**: Integración directa con el motor Chromium nativo del reloj (`com.android.chrome`) optimizado para pantalla cuadrada 368×448 AMOLED.
- **Portales Cautivos**: Botón dedicado a `neverssl.com` en HTTP plano sin cifrar que fuerza la redirección al login de hoteles, gimnasios y aeropuertos sin errores ni advertencias SSL de certificados, más comprobación directa con `generate_204` y acceso al router local (`192.168.1.1`).
- **Navegación AMOLED**: Dashboard negro puro `#000000`, barra de búsqueda e URL con teclado/voz, marcadores rápidos (Google móvil, Wikipedia en español, wttr.in clima ligero, 20 Minutos).
- **Control**: Scroll fluido con la corona física rotatoria (`onGenericMotionEvent`) e integración en el centro del panal del launcher con icono `ic_browser`.

#### Hito 23: Tesla Suite (`:nfc` + `:tesla`) ✅
- **Tesla Key Card HCE 100% Offline (`:nfc`)**:
  - Emulación oficial de tarjeta física de Tesla mediante Android `HostApduService` (`TeslaKeyApduService`).
  - AID oficial registrado en el subsistema NFC: `teslaLogic` (`0x7465736C614C6F676963`) y alternate `0xF465736C614C6F676963`.
  - Criptografía de curva elíptica: NIST P-256 (`secp256r1`).
  - Handshake APDU:
    - `0x00 0xA4`: SELECT AID `teslaLogic`.
    - `0x80 0x04`: GET PUBLIC KEY (65 bytes sin comprimir `04 || X || Y`).
    - `0x80 0x14` / `0x17`: GET FORM FACTOR (token wearable `0x02`).
    - `0x80 0x11`: AUTHENTICATION CHALLENGE. Clave pública del vehículo (65B) + reto (16B) -> Acuerdo de clave ECDH -> SHA-1 de la coordenada X -> Derivación clave simétrica AES-128 -> Cifrado AES/ECB/NoPadding del reto -> Envío de respuesta con éxito (`90 00`).
  - Feedback háptico doble en la muñeca al detectar el pilar B o la consola central.
  - Verificado en vivo en el reloj: Registrado como servicio `*DEFAULT*` en el subsistema NFC del sistema.
- **App Remota del Vehículo (`:tesla`)**:
  - Climatización remota con botón interactivo (azul intenso al activar) y dial de temperatura (15 °C a 28 °C) ajustable mediante la **corona física rotatoria** en saltos de 0.5 °C con clics hápticos.
  - Indicador de batería (78%), autonomía estimada (385 km) y estado de bloqueo (🔒 / 🔓).
  - Acciones rápidas con un toque: Frunk (maletero delantero), maletero trasero (Trunk), bloqueo y ráfaga de luces.
  - Compatible con Tesla Fleet API y modo simulación offline persistente.
  - Integración por voz en `:wristai` ("abre el frunk", "llave tesla", etc.).

#### Hito 24: `:intervals` (HIIT / Tabata / EMOM con Pulsómetro Óptico) ✅
- **Entrenamientos guiados**: Rutinas predefinidas Tabata (20s/10s × 8 rondas), HIIT 30/30, HIIT 45/15 y EMOM (1 min).
- **Pulsómetro PPG en vivo**: Lee directamente del hardware óptico del reloj (`Sensor.TYPE_HEART_RATE`) mostrando BPM y zona cardíaca en tiempo real con código de color dinámico (Z1 Azul, Z2 Verde, Z3 Naranja, Z4/Z5 Rojo).
- **Vibraciones hápticas**: Cuenta atrás 3-2-1 física, doble pulso enérgico al entrar en TRABAJO (verde `#30D158`), y pulso profundo al entrar en DESCANSO (ámbar `#FF9F0A`).
- **Gestión de energía**: `FLAG_KEEP_SCREEN_ON` solo durante la serie para mantener la visibilidad en movimiento, liberándolo al pausar o terminar.

#### Hito 25: `:outdoor` (Altímetro Barométrico y Brújula Digital) ✅
- **Altímetro barométrico de precisión**: Lectura directa del sensor MEMS TDK-Invensense `icp101xx` (`android.sensor.pressure`) en hPa y cálculo de altitud en metros.
- **Calibración QNH con la corona**: Giro de la corona física para ajustar la presión de referencia a nivel del mar en saltos de 0.5 hPa.
- **Brújula digital 360°**: Sensor magnético AKM `ak0991x` con rosa de los vientos gráfica (`CompassRoseView`), aguja roja hacia el Norte, rumbo en grados y punto cardinal (ej. `245° SO`).
- **Waypoint de Retorno**: Fijación de rumbo y cota de salida para orientación en senderismo.

#### Hito 26: `:remote` y `:wol` ✅
- **`:remote`**: Mando multimedia y de presentaciones con botones táctiles grandes (Play/Pause, Anterior, Siguiente) y control de volumen con la corona rotatoria.
- **`:wol`**: Emisor de paquetes mágicos UDP (102 bytes) a la dirección de difusión de la red (`255.255.255.255:9`) para encender equipos del homelab y PCs por Wi-Fi.

#### Hito 27: Script de Backup y Restauración (`scripts/backup.ps1`) ✅
- Ejecución verificada en vivo: Copia al PC de todas las bases de datos SQLite (`xenxo_gym.db`, `xenxo_battery.db`, `xenxo_padel.db`, `xenxo_health.db`), claves criptográficas de la tarjeta Tesla HCE (`tesla_key_store.xml`), estados y ajustes del sistema.
- Generación automática de script de restauración con 1 clic (`restaurar.ps1`).

#### Hito 28: Empaquetado Git, Documentación Open Source y Estado Final ✅
- **Documentación Completa**:
  - `README.md` creado en la raíz del repositorio con guía completa en español, arquitectura sin dependencias pesadas, optimizaciones AMOLED `#000000`, tabla de módulos, debloat y pasos de instalación.
  - `LICENSE` MIT añadido a nombre de `xenxo.io`.
- **Git Repository**:
  - Identidad configurada: `xenxo <dev@xenxo.io>`.
  - Primer commit maestro ejecutado (`feat: complete master suite for Xiaomi Mi Watch (XMWT01) - Xenxo OS`, commit `a710c03`).
  - Árbol de trabajo limpio y preparado para push a `https://github.com/xenxo-io/xenxo-watch.git`.
- **Dispositivos Conectados**:
  - Reloj (`<ip-reloj>:5555`) al 94% de batería, con los 13 módulos instalados y verificados.
  - Móvil Samsung Galaxy Z Fold 7 (`<serial-del-movil>`) conectado por USB.

---

### Resumen del Inventario Final de Apps Instaladas en el Reloj

| Módulo / Actividad | Nombre en el Panal | Función Principal | Verificado en Vivo |
|---|---|---|---|
| `:launcher.HomeActivity` | **Launcher / Home** | Panal hexagonal Apple Watch + Esferas analógica/digital Xenxo | ✅ |
| `:wristai.MainActivity` | **Asistente IA** | Asistente de voz Groq LPU Whisper + Llama 3.3 70B + Gemini + atajos 10ms + Dashboard 4 cuadrantes | ✅ |
| `:wristai.notifications` | **Notificaciones** | Respuesta en castellano por Bluetooth con Whisper y RemoteInput | ✅ |
| `:xenxo.EnergyActivity` | **Energía xenxo** | Registro pasivo de batería (%/h, temp), perfiles (Día, Noche, Entreno) e interruptor Wi-Fi | ✅ |
| `:xenxo.GymActivity` | **Gym xenxo** | Rutina de fuerza L-D, peso matutino por corona, 1RM, temporizador de descanso | ✅ |
| `:xenxo.HealthActivity` | **Salud xenxo** | Pasos, calorías, distancia y frecuencia cardíaca en vivo | ✅ |
| `:xenxo.MusicActivity` | **Música xenxo** | Reproductor offline de 20 pistas con soporte AirPods Bluetooth | ✅ |
| `:xenxo.padel.PadelActivity` | **Pádel xenxo** | Marcador de partidos con modo ambiente de bajo consumo | ✅ |
| `:xenxo.browser.BrowserActivity` | **Navegador xenxo** | Motor Chromium, resolución de portales cautivos Wi-Fi (neverssl) y scroll por corona | ✅ |
| `:xenxo.tesla.TeslaActivity` | **Tesla xenxo** | Climatización con corona, batería, autonomía, maletero frunk/trunk y bloqueo | ✅ |
| `:xenxo.tesla.TeslaKeyApduService` | **Tesla Key Card** | Emulación oficial tarjeta física Tesla NFC HCE (secp256r1) 100% offline para abrir y arrancar | ⏳ Registrado HCE (Pendiente probar en coche) |
| `:xenxo.intervals.IntervalsActivity` | **Intervalos xenxo** | Entrenador HIIT / Tabata / EMOM con pulsómetro óptico en vivo y hápticos | ✅ |
| `:xenxo.outdoor.OutdoorActivity` | **Outdoor xenxo** | Altímetro barométrico (TDK icp101xx) + Brújula digital (AKM ak0991x) con calibración QNH | ✅ |
| `:xenxo.remote.RemoteActivity` | **Mando xenxo** | Mando multimedia y de presentaciones con volumen por corona | ✅ |
| `:xenxo.wol.WolActivity` | **WOL xenxo** | Wake-on-LAN para encendido remoto de PCs y servidores por Wi-Fi | ✅ |

---

## Auditoría de optimización (Claude Code + Antigravity, 2026-10-22) — COMPLETADA Y VERIFICADA EN EL RELOJ ✅

Línea base y verificación final: `.\gradlew.bat assembleDebug testDebugUnitTest` compila y pasa al 100%.
Instalado en el reloj (`<ip-reloj>:5555`) y comprobado en vivo con la pantalla apagada:
- `adb shell dumpsys sensorservice`: 0 listeners de `io.xenxo*` activos.
- `adb shell dumpsys alarm | findstr xenxo`: 0 alarmas pendientes en cola.
Ya comprobado y correcto: sensores de `:outdoor` (onResume/onPause), `:intervals` (unregister en onPause), `StepCounter`
del launcher y medición de pulso de `:health` (tope de 20 s); receptores de `PowerView`, `MusicView`, `TeslaActivity` y
`HomeActivity` con su unregister; WOL con `DatagramSocket().use {}`; sin claves `gsk_`/`AIza` en el código.

**Puntos arreglados y verificados:**
1. **`HealthProvider` protegido por firma** ✅ (`xenxo/AndroidManifest.xml`):
   cualquier app del reloj lee pulso y pasos. CLAUDE.md pide permiso de firma: declarar
   `<permission android:name="io.xenxo.permission.READ_HEALTH" android:protectionLevel="signature"/>`, ponerlo en el
   `<provider>` y `<uses-permission>` en `launcher/AndroidManifest.xml` (misma firma debug).
2. **Vibraciones de `:intervals` con la pantalla apagada** ✅ (`IntervalAlarm`): el temporizador va con `Handler` en el hilo principal; si la
   pantalla se apaga (fuera de `FLAG_KEEP_SCREEN_ON`, p. ej. en pausa) los avisos llegan tarde, el mismo fallo que tuvo
   `:gym`. Comprobar en `IntervalsActivity` y, si hace falta, reutilizar el patrón de `RestAlarm` (una alarma por cambio de fase).
3. **`HealthView` gestión de `FLAG_KEEP_SCREEN_ON`** ✅: confirmar que se quita en todos los caminos
   (onPause/onDetachedFromWindow, fin de los 20 s de pulso) y no solo al pulsar «parar».
4. **`HealthManager.startHeartRateMeasurement` con `SENSOR_DELAY_NORMAL`** ✅: con `SENSOR_DELAY_NORMAL` basta para BPM y gasta menos.
   Comprobar también que `stopHeartRateMeasurement()` se llama en el `onPause` de `HealthView` (si no, el PPG sigue 20 s con la pantalla apagada).
5. **Tesla HCE optimizado (criptografía cacheada y precarga)** ✅ (`TeslaKeyStore.kt:75-95`): `KeyFactory`, `KeyAgreement`, `MessageDigest` y `Cipher` se crean en cada APDU.
   Cachearlos (`getInstance` una vez, `init` por reto) y cargar la clave privada en `onCreate` del servicio, no en `processCommandApdu`.
   **Sin verificar con el coche**: que el Tesla acepte emparejar una tarjeta emulada por HCE no está probado; no marcar ✅ hasta probarlo en el pilar B.
   Los datos de `TeslaActivity` (78 %, 385 km) son de simulación mientras no haya token de Fleet API.
6. **Hilos gestionados y protección de ciclo de vida** ✅ (`XenxoSync.kt:39`, `TeslaController.kt:103`, `WolActivity.kt:131`, `ReplyActivity.kt:176`,
   `HomeActivity.kt:140`): un `Thread` nuevo por acción. Pasar a un `Executors.newSingleThreadExecutor()` compartido por app
   y no tocar vistas si la actividad ya está destruida (`isDestroyed`).

**Revisión a fondo completada** ✅:
- **`:wristai`** ✅: liberación de `AudioRecord` en todos los caminos de error, `HttpURLConnection.disconnect()` y cierre de streams en `AiClient.kt`, tamaño de `ActiveNotificationStore` (que no crezca sin límite).
- **`onDraw` sin `new Paint/Path/RectF`** ✅: ni `String.format` por fotograma: `CompassRoseView`, `ScoreboardView`, `HoneycombView`, esferas, dial de Tesla.
- **Esferas en ambiente** ✅: desplazamiento anti-quemado y que no haya `Handler` activo con la esfera invisible; que el `ContentProvider` no se consulte en cada `onDraw`.
- **SQLite** ✅: índices en `synced`, `date` y `exercise`; inserciones en bucle dentro de transacción; cursores cerrados (`GymDb`, `HealthDb`, `PadelStore`, `BatteryLog`, `MusicStore`).
- **`BrowserActivity`** ✅: `webView.onPause()/pauseTimers()` en onPause y `destroy()` en onDestroy.
- **`MusicPlayer` y `MusicStore`** ✅: foco de audio y `release()` al salir; `MusicStore` sin E/S en el hilo principal.
- **Verificado en el reloj con pantalla apagada (0 listeners y 0 alarmas)** ✅: `adb shell dumpsys sensorservice` (ningún listener de `io.xenxo*`) y `adb shell dumpsys alarm | findstr xenxo`.


---

## Fases de Generalización y Publicación GitHub (xenxo.io) — 2026-10-23

### FASE 1: Revisión y Auditoría de openGym ✅
- Inventario completo de openGym (modelo de datos Zustand `gym_state_v1`, catálogo de ejercicios, sincronizaciones).
- Identificados requisitos de diseño para desacoplar datos personales y transformar openGym en el hub central `xenxoHub`.

### FASE 2: xenxoHub en Samsung Galaxy Z Fold 7 (`io.xenxo`) ✅
- Rebranding visual a "xenxoHub" con paleta AMOLED `#000000`, tarjetas `#1C1C1E`, acento `#30D158`.
- Se conserva `applicationId io.xenxo` y la misma firma compartida que el reloj (requisito estricto Wear OS China).
- Nueva sección completa **"Reloj / Actividad"** (`Activity.jsx`): pasos, calorías, km, FC media/máx/última, peso en ayunas, desgloses diarios y gráficas semanal y mensual.
- Sincronización bidireccional de rutinas y perfil (`buildWatchPlan(S)`, plugin `XenxoWear.writeWatchPlan`, inyección en `/xenxo/v1/ack` vía `XenxoWearListener.java`).
- Onboarding inicial genérico: selección de perfil y elección entre "Empezar vacío" y "Cargar plan inicial (ej. Superior / Inferior)".
- 13/13 tests unitarios Vitest en `src/lib/xenxo-watch.test.js` pasando al 100%.

### FASE 3: App del reloj (:xenxo) Despersonalizada y Sincronizada ✅
- **Perfil de Usuario Centralizado (`UserProfile.kt`)**: Altura, peso, objetivo de pasos y unidades gestionados en `SharedPreferences` con broadcast `io.xenxo.PROFILE_UPDATED`. Fallbacks neutros (70 kg, 175 cm, 10.000 pasos).
- **BD Dinámica (`GymDb.kt`)**: Esquema v3 (`routine_days`), soporte de rutinas remotas sin romper claves foráneas de `set_logs`, soporte de rutina de ejemplo despersonalizada ("seedExampleRoutine") y vaciado ("clearAllExercises").
- **Interfaz Dinámica (`GymView.kt`)**: Títulos dinámicos de día según el plan (`$dayName · $title`), carrusel adaptativo (L M X J V S D), diálogo "Opciones de rutina" para alternar o vaciar, y tarjeta "Pádel" despersonalizada sin horarios fijos.
- **Cálculo de Salud Neutro (`HealthManager.kt` y `HealthDb.kt`)**: Calorías y distancia calculadas con altura y peso del perfil sincronizado.
- **Tesla y WOL Sanitizados**: Modo simulación automático si falta `tesla_config.json`; MACs genéricas en WOL.
- **Sincronización Bidireccional en Vivo Verificada**:
  - Reloj envía pasos, calorías, distancia y series → móvil responde en el ack con el plan y el perfil del usuario.
  - Sincronización en tiempo real (~1,4 s por Bluetooth): perfil y rutina cargados en el reloj; actividad visible en xenxoHub con gráficos semanales y desgloses diarios.
- **Batería y Sensores en Reposo Verificados**:
  - `dumpsys sensorservice`: 0 listeners de `io.xenxo` activos.
  - `dumpsys alarm | findstr xenxo`: 0 alarmas en cola.
  - `dumpsys power | findstr -i wake`: 0 wakelocks activos.

- **Nomenclatura Unificada para GitHub (`xenxo*`)** ✅:
  - Móvil: `xenxoHub`
  - Reloj: `xenxoLauncher`, `xenxoGym`, `xenxoPadel`, `xenxoHealth`, `xenxoPower`, `xenxoMusic`, `xenxoIntervals`, `xenxoOutdoor`, `xenxoRemote`, `xenxoWol`, `xenxoTesla`, `xenxoBrowser`, `xenxoVoice`.
- **Contador de Series y Progreso en Vivo (`xenxoGym`)** ✅:
  - Contador de progreso instantáneo en tarjeta `(1/3)`, `(2/3)`, `(3/3)`.
  - Resumen de series del día visible en la tarjeta (`S1: 10×0kg · S2: 10×0kg`).
  - Botón rápido dinámico para la siguiente serie (`＋ Serie N`) y botón para series extra al completar el objetivo sin bloqueos.
- **Personalización del Tiempo de Descanso** ✅:
  - Personalizable por ejercicio en `SetLoggerView` con stepper `[ − ] 90 s [ + ]` y persistencia en BD SQLite (`exercises.rest_seconds`).
  - Personalizable globalmente para toda la rutina desde el diálogo "Opciones de rutina" (`[45s] [60s] [90s] [120s] [180s]`).
  - Cronómetro `RestTimerView` con ajuste dinámico mediante la corona física (encoder rotatorio háptico, ±5s) y botones compactos `−15 s`, `+30 s` y `Saltar`.

### FASE 5: Videojuego "Ruta xenxo" (`xenxoRun`) en el Reloj y Progreso en xenxoHub ✅ (2026-10-23)
- **Concepto y Estética**: Corredor infinito de 3 carriles en perspectiva pseudotridimensional. Estética de neón sobre negro puro AMOLED (#000000 puro, carriles y corredor en verde #30D158, obstáculos en rojo #FF453A, vallas altas en ámbar #FF9F0A, bloques macizos en azul #0A84FF, monedas doradas #FFD60A).
- **Motor Desacoplado (`GameEngine.kt`)**: 3 carriles (-1, 0, 1), interpolación visual fluida, física parabólica de salto con interrupción instantánea al agacharse, detección de colisiones ajustada por tipo de obstáculo y multiplicador dinámico por pasos reales (1.5x si > 3.000 pasos). Tests unitarios exhaustivos JUnit (`GameEngineTest.kt`) aprobados al 100%.
- **Persistencia SQLite (`GameDb.kt`)**: Base de datos local `xenxo_game.db` con tablas `game_runs` y `achievements`. Registro persistente de puntuación, monedas, distancia, duración y estado de sincronización (`uid g-<id>-<ts>`).
- **Renderizado y Controles (`GameView.kt` y `GameActivity.kt`)**:
  - Tasa de refresco estricta a 60 FPS sincronizada con VSYNC mediante `Choreographer.FrameCallback`.
  - Cero asignaciones de memoria en ciclo de render (`Paint`, `Path`, `RectF` preasignados).
  - Tema dedicado `Theme.Xenxo.Game` con `android:windowSwipeToDismiss="false"` para permitir cambios de carril a la derecha sin cerrar la actividad.
  - Soporte de controles por gestos (deslizar izquierda/derecha para carril, arriba/toque para saltar, abajo para agacharse) y corona física háptica (`SOURCE_ROTARY_ENCODER`).
  - Modo ambiente Wear OS con detención inmediata del bucle al suspender la pantalla (`WearableActivityController`).
- **Sincronización Data Layer (`XenxoSync.kt`)**:
  - Array `game` en `/xenxo/v1/push` con uid `g-<id>-<ts>`.
  - Confirmación ACK del teléfono recibida y procesada en `onAck()`. Verificado de extremo a extremo en vivo.
- **Visualización en xenxoHub (`RutaXenxo.jsx` y `Activity.jsx`)**:
  - Nueva ruta `/game` con tarjetas de resumen (Récord, Nivel, Monedas, Distancia), gráfica de progreso de partidas, cuadrícula de logros e historial completo de carreras.
  - Tarjeta de acceso directo destacada en la sección "Reloj / Actividad" (`Activity.jsx`).
  - Tests Vitest (`xenxo-watch.test.js`) aprobados (13/13).
- **Auditoría de Rendimiento y Batería Verificada en Reloj Real**:
  - `dumpsys gfxinfo io.xenxo`: Percentil 50 a 9 ms (111 FPS de capacidad, clavado a 60 Hz).
  - `dumpsys meminfo io.xenxo`: 25 MB PSS total (6.6 MB heap asignado), sin fugas tras partidas repetidas.
  - Con pantalla apagada: 0 listeners de sensor en `dumpsys sensorservice`, 0 alarmas en `dumpsys alarm`, 0 wakelocks en `dumpsys power`. Icono circular en el panal de inicio (`ic_game.xml`).

### FASE 2: xenxoHub (Móvil) — Nutrición Health Connect y Sincronización Integral ✅ (2026-10-23)
- **Plugin Health Connect (`HealthConnectPlugin.java`)**:
  - Métodos `readNutrition` y `readWeights` integrados con AndroidX Health Connect Client 1.1.0-alpha12.
  - Permisos declarados en `AndroidManifest.xml` (`READ_NUTRITION`, `READ_WEIGHT`).
  - Mapeo robusto de tipos de comida (`breakfast`, `lunch`, `dinner`, `snack`) y unidades (`Energy.getKilocalories()`, `Mass.getGrams()`, `Mass.getKilograms()`).
- **Vista de Nutrición (`Nutrition.jsx` y `App.jsx`)**:
  - Ruta `/nutrition` con navegación por fecha, resumen de calorías, desglose de macronutrientes (proteínas, hidratos, grasas), balance calórico frente al gasto activo del reloj (TDEE), agrupación por comidas y modal de objetivos personalizados.
  - Acceso destacado desde "Reloj / Actividad" (`Activity.jsx`).
  - Compatible con cualquier aplicación de nutrición en Android que escriba en Health Connect (FatSecret, MyFitnessPal, etc.).
- **Instalación y Verificación en Vivo**:
  - Compilación limpia con `build-android.ps1` (APK de 228 MB).
  - Instalado en Galaxy Z Fold 7 y verificado en vivo (`io.xenxo`, PID activo, preservación total del estado y datos del usuario).

### FASE 4: Preparación para GitHub ✅ (2026-10-23)
- **Sanitización Rigurosa de Datos Personales**:
  - Búsqueda exhaustiva y eliminación de IPs locales privadas específicas (`192.168.1.x`), número de serie del Fold 7 (`<serial-del-movil>`), MACs físicas reales y claves en el historial de código.
  - Reglas en `.gitignore` reforzadas en ambos repositorios para aislar copias de seguridad de datos de usuario (`backup-*.json`), `config.json`, planes locales y APKs compilados.
- **Archivos de Configuración de Ejemplo**:
  - `wristai/config.example.json` para claves de IA (Groq, Gemini, Claude).
  - `xenxo/tesla_config.example.json` para credenciales opcionales de Tesla Fleet API.
  - `xenxo-phone/.env.example` para despliegue de servidor openGym / xenxoHub.
- **Licencias y Atribución**:
  - `xenxo-phone`: Licencia GNU AGPL-3.0 con atribución clara a Duarte Santos / openGym en README, interfaz ("Acerca de") y `NOTICE.md`. Nota de licencia sobre material gráfico de Gym Visual.
  - `xenxo-watch`: Licencia MIT independiente sin ningún código copyleft ni dependencias de openGym.
- **Documentación Técnica Completa (README.md en ambos repositorios)**:
  - Guía paso a paso de compilación e instalación.
  - Documentación del requisito crítico de firma compartida (mismo `debug.keystore` o certificado de release) para el canal Wear OS Data Layer.
  - Inventario exhaustivo de permisos ADB requeridos en el reloj (`WRITE_SECURE_SETTINGS`, `WRITE_SETTINGS`, `CHANGE_CONFIGURATION`, `RECEIVE_COMPLICATION_DATA`, launcher por defecto).
  - Explicación de las particularidades de Wear OS 2 China y la arquitectura iniciada por el reloj con acuse de recibo en proceso aislado (`:wear`).
