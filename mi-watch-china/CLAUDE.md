# CLAUDE.md — xenxo-watch

Plan y contexto para Claude Code. Responde y comenta siempre en **castellano**; nombres de código en inglés.
Marca: **xenxo.io**. Paquetes: `io.xenxo.*`.

## Objetivo
Personalizar por completo un **Xiaomi Mi Watch XMWT01** (versión china, 2019) emparejado con smartphone Android:
launcher propio estilo Apple Watch, sistema en castellano, menos consumo y una colección de apps propias.

## El dispositivo (restricciones duras)
- Android 9 (API 28), **Wear OS 2 China** («Local Edition»). Trae una versión china de Google Play Services (`com.google.android.gms`, `gsf`, `vending`), pero **no contamos con ella**: nada de Firebase, GMS, Google Fit, Health Services, Watch Face Format ni Compose que dependa de GMS.
- Snapdragon Wear 3100 (`msm8909`, armv7 32 bits), **~900 MB de RAM (~66 % ya ocupada)**, 4 GB de almacenamiento.
- Pantalla **cuadrada 368×448** AMOLED, corona (`SOURCE_ROTARY_ENCODER`), micro, altavoz, pulso, podómetro hardware, barómetro, brújula, GPS.
- **NFC activo y accesible para apps** (comprobado con NFC Check). **HCE confirmado** (`nfc.hce`, `nfc.hcef`). Con pantalla apagada el NFC no sondea: no gasta en reposo. eSIM y pagos NFC: **imposibles** (no tocar; hay un pago por defecto de EVO Banco residual, se deja).
- Red: por Bluetooth del móvil o Wi-Fi propio. Google TTS instalado. Emparejado con **Wear OS de Google y Xiaomi Wear** a la vez: `com.xiaomi.wearable` y `com.xiaomi.account` **no se tocan**.
- Opciones de desarrollador y ADB inalámbrico activos (`<ip-del-reloj>:5555`). Sin root ni bootloader desbloqueado. **No apagar nunca el Wi-Fi durante una sesión de ADB**.
- Densidad 320 (2,0×). Idiomas: de fábrica `en-US` (`ro.product.locale` = `zh-CN`). **Desde el 2026-10-21 la lista es `es-ES,en-US`**, puesta con «Idioma del sistema» de `:launcher` (permisos `CHANGE_CONFIGURATION` por `pm grant` + `WRITE_SETTINGS` por `appops`; sin el segundo el sistema lo rechaza en silencio). La ROM no trae castellano en el framework ni en Ajustes (`locales: en-GB zh-CN`) ni en Mi Fitness: siguen en inglés; `persist.sys.locale` se queda en `en-US`. Sin comprobar aún que la lista sobreviva a un reinicio.
- Recursos de nuestras apps: castellano en `values-es/` y respaldo inglés en `values/`. **No** poner el castellano en `values/` sin etiqueta: con la lista `es-ES,en-US` Android elegiría `values-en`.
- **Pantalla de inicio**: la base es la de Wear OS (`com.google.android.wearable.app/…home2.activity.HomeActivity2`), que pone esfera, notificaciones (deslizar abajo), ajustes rápidos (deslizar arriba) y tarjetas (deslizar a la izquierda). El intent HOME (deslizar a la derecha o botón de la corona) abre **xenxo Launcher** (`io.xenxo.launcher/.HomeActivity`), configurado como predeterminado con categoría `HOME`. **T-Launcher ha sido desinstalado definitivamente** por ADB para liberar RAM y evitar conflictos.
- El usuario usa levantar la muñeca, el tiempo (`com.xiaomi.wear.weather`, usa ubicación) y las llamadas por el reloj: no se desactivan.

## Reglas de diseño para todo el repo
- **Cero dependencias** salvo motivo muy justificado: solo framework Android (`HttpURLConnection`, `org.json`, `SensorManager`, `MediaRecorder`, `TextToSpeech`, `NfcAdapter`…). Nada de AndroidX/Compose/OkHttp/coroutines.
- Views clásicas o Views pintadas a mano. Fondo **negro puro** (AMOLED), sin animaciones en bucle, sin wakelocks; sensores y receivers solo mientras la pantalla está visible.
- `minSdk 28`, `targetSdk 30`, `compileSdk 35`, Kotlin, JDK 17. Un módulo `:app` por APK.
- **Modo ambiente en actividades** (marcador de pádel, futuros intervalos): `com.google.android.wearable.compat.WearableActivityController`, que trae el reloj como librería del sistema → `compileOnly("com.google.android.wearable:wearable:2.9.0")` + `<uses-library android:name="com.google.android.wearable" android:required="false" />`. No va en el APK: no cuenta como dependencia. Solo entra en ambiente si la **pantalla siempre activa** de Wear OS está encendida; si no, el sistema pausa la actividad.
- **`screencap` no captura la capa de ambiente** en este reloj (sale negra). Para comprobar el ambiente: logcat del propio tag (`AmbientService: Selected running activity for ambient mode …`) y `dumpsys activity <actividad>`.
- Tests de lógica pura (sin Android) con JUnit en `src/test` (`testImplementation`, no entra en el APK).
- **Excepción a «sin AlarmManager exactos»**: `io.xenxo.gym.RestAlarm`, una sola alarma por descanso que pide el usuario al guardar una serie (sin ella la vibración de fin de descanso llegaba tarde con la pantalla apagada). Ninguna otra.
- Textos de UI en castellano de España. Fechas con `Locale("es","ES")`.
- Nada destructivo en el reloj sin vuelta atrás: `pm disable-user` (reversible), nunca `pm uninstall` de apps del sistema.
- Ninguna clave/token en el repo (`config.json` en `.gitignore`).

## Estado actual del repo
| Módulo | Estado |
|---|---|
| `:launcher` | Cajón de apps estilo Apple Watch (panal con ojo de pez, inercia, zoom con corona, fijar/ordenar/ocultar) con menús propios, **Ajustes** y las **esferas xenxo** (Digital y Analógica). **Launcher principal definitivo del reloj** (categoría HOME en Manifest, `cmd package set-home-activity io.xenxo.launcher/.HomeActivity`). T-Launcher desinstalado. Permisos concedidos: `WRITE_SETTINGS` (appops), `CHANGE_CONFIGURATION` (pm grant), `WRITE_SECURE_SETTINGS` (pm grant), `RECEIVE_COMPLICATION_DATA` (pm grant). |
| `:xenxo` | **Instalado y verificado al 100% en el reloj.** App unificada local (`io.xenxo`). Secciones operativas: **`:power`** (perfiles 1-toque, toggle Wi-Fi ADB, historial), **`:gym`** (rutina semanal, peso matutino por corona, 1RM, descansos), **`:padel`** (marcador con modo AOD, deshacer por corona), **`:health`** (pasos, pulso PPG en vivo, kcal), **`:browser`** (Chromium, resolución de portales cautivos `neverssl.com`), **`:tesla`** (Key Card HCE NFC offline `secp256r1` AID `teslaLogic` para abrir y conducir + mando remoto con dial de corona), **`:intervals`** (HIIT/Tabata/EMOM con pulsómetro PPG en vivo y zonas de color), **`:outdoor`** (altímetro TDK `icp101xx` con QNH por corona + brújula 360° AKM `ak0991x`), **`:remote`** (mando multimedia con volumen por corona), **`:wol`** (Wake-on-LAN UDP broadcast). |
| `:wristai` | **Completado y verificado en vivo.** Asistente por voz Groq LPU Whisper (<500 ms) + Llama 3.3 70B / Gemini Flash + atajos locales (<10 ms) + dashboard 4 cuadrantes. Incluye subsistema de **Notificaciones Bluetooth** para contestar mensajes del móvil en castellano mediante Groq Whisper y `RemoteInput`. |
| Gradle wrapper | 8.14.3. Entorno: JDK 17 Temurin, SDK en `C:\Android\Sdk` (`local.properties`). |
| `scripts/diagnostico-reloj.ps1` | Solo lee: informe `.txt` + copia de `settings` (`ajustes-diagnostico-*.txt`). |
| `scripts/medir-bateria.ps1` | `-Reset` al 100 % en el cargador; sin parámetros vuelca y resume. Resultados en `docs/bateria.md`. |
| `scripts/debloat.ps1`, `ajustes-bateria.ps1`, `restaurar.ps1` | Fase 1. Simulacro por defecto, `-Aplicar` para hacerlo. |

Los `.ps1` se guardan en **UTF-8 con BOM**: PowerShell 5.1 lee sin BOM como ANSI y la «—» rompe las cadenas.

## App del móvil: `C:\projects\xenxo-phone`
Copia de **openGym** (AGPL-3.0) aparte de este repositorio, con sus propias notas en `xenxo-phone\XENXO.md` (entorno, compilación en Windows, emulador, licencias). Datos solo en el móvil; sincronización con `:xenxo` por **Data Layer de Wear OS**. **No copiar código de openGym al reloj** (protocolo propio) y **no pasar a ninguna IA las imágenes/animaciones de ejercicios** (licencia de Gym visual).
- Si el Data Layer funciona, se acepta en `:xenxo` la librería de Google Play Services necesaria (excepción a «cero dependencias» y a «no contamos con GMS», decidida por el usuario el 2026-10-21 al elegir esta vía).
- **Sincronización reloj → móvil funcionando (2026-10-21)**: `io.xenxo.sync` en `:xenxo` ↔ «openGym by xenxo.io» (`io.xenxo`) en el smartphone; protocolo `/xenxo/v1/push` → `/xenxo/v1/ack` por el Bluetooth de Wear OS China. Pendiente: rutinas móvil → reloj. Al leer BD del reloj por ADB, copiar también `*.db-wal` (SQLite en WAL).
- **Data Layer validado el 2026-10-21** (detalle en `xenxo-phone\XENXO.md`): el móvil usa **Wear OS China**, cuyo WearableService propio es el conectado al reloj; el de Play Services del móvil no. Por eso: **siempre inicia el reloj** (mensajes/DataItems del reloj llegan aunque la app del móvil esté cerrada), y el móvil solo puede **responder** desde su `WearableListenerService` en **proceso aparte** (`:wear`). Reloj y móvil con el **mismo applicationId (`io.xenxo`) y la misma firma**.

## Organización: 3 APK
1. **xenxo Launcher** (`:launcher`): cajón de apps + esferas (`CanvasWatchFaceService`) en el mismo APK.
2. **xenxo** (`:xenxo`): pádel, gimnasio, salud, batería (`:power`), música, Tesla (`:tesla` / `:nfc`), intervalos, outdoor, NFC y notas de voz como **secciones de una sola app**, con base de datos compartida. Expone pulso, pasos y perfil activo a las esferas con un `ContentProvider` protegido por permiso de firma.
3. **Wrist AI** (`:wristai`): la única app con permiso de Internet directo (o comunicación con APIs de IA y Tesla Fleet).

Los apartados de cada app más abajo (`:padel`, `:health`, `:gym`, `:power`, `:tesla`…) describen **secciones** de `:xenxo`, no módulos separados.

**Prioridad** (decidida por el usuario el 2026-10-21, paso a paso y puliendo cada cosa): launcher (2: estética, idiomas) ✅ → esferas (2b) ✅ → batería (Fase 1, 1c) ✅ → gimnasio ✅ → pádel ✅ → salud (`:health`) ✅.
1. **PRIORIDAD INMEDIATA CON RELOJ CONECTADO: `:wristai`** (Asistente de salud y fitness autónomo Groq LPU + Gemini Flash + atajos locales en 10 ms).
2. **Respuesta a Notificaciones en castellano por Bluetooth (`:notifications`)**: Responder WhatsApp, Telegram, SMS, etc. desde el reloj con dictado por voz en castellano (Groq Whisper ~200 ms), plantillas rápidas y sugerencias inteligentes.
3. **Navegador Web Chromium para el reloj (`:browser`)**: WebView/Chromium optimizado para pantalla cuadrada 368×448 AMOLED (modo oscuro puro, scroll con la corona háptica, Reader Mode) y solución definitiva a los **portales cautivos de Wi-Fi** (gimnasio/hoteles).
4. **Tesla Suite**: `:nfc` (Tesla Key Card con HCE offline para abrir en pilar B y arrancar) + `:tesla` (app remota de climatización con corona, batería, frunk/trunk y voz).
5. **Secciones posteriores**: intervalos (`:intervals`), outdoor (`:outdoor`), mando Bluetooth (`:remote`) y Wake-on-LAN (`:wol`).

## ⚡ Prioridad nº 1: BATERÍA
El usuario nota que el reloj gasta mucha batería. Cada decisión del repo se mide contra esto.

**Medir antes de tocar (línea base):**
1. Carga al 100 %, `adb shell dumpsys batterystats --reset`, desconecta y uso normal 4-6 h.
2. `adb shell dumpsys batterystats --charged > base.txt` y `adb shell dumpsys battery`. Anotar %/hora, apps y wakelocks que más gastan, tiempo de pantalla, radio/BT/Wi-Fi/GPS, CPU sin pantalla (`Partial wakelock`).
3. Salud de la batería (5 años): comparar `charge_counter`/capacidad real con los 570 mAh nominales si el kernel lo expone. Si está muy degradada, decírselo claro: el software no lo arregla y la solución es cambiar la batería.
4. Repetir la medición tras cada fase y guardar resultados en `docs/bateria.md` (tabla antes/después).

**Palancas, por impacto esperado:**
- **XiaoAI / palabra de activación**: micro escuchando siempre → desactivar el paquete (Fase 1).
- **Pulso continuo de Xiaomi**: muestreo cada minuto = gran consumo → bajarlo a 10-30 min o desactivarlo (ajustes de salud del reloj o Xiaomi Wear).
- **Apps chinas en segundo plano** (tienda, Alipay, eSIM, analíticas, actualizadores, sincronización con servidores de Xiaomi) → `pm disable-user`.
- **Pantalla**: brillo fijo bajo en interior en vez de automático, timeout 5-10 s, levantar muñeca solo si se usa; **la pantalla en reposo (AOD / modo ambiente) SE QUEDA ACTIVADA: al usuario le gusta.** Se optimiza en vez de quitarla: solo nuestras esferas en modo ambiente (≥95 % píxeles negros, trazos finos grises, sin colores, refresco 1/min, desplazamiento anti-quemado).
- **Radios**: Wi-Fi apagado mientras hay Bluetooth (corta el ADB por Wi-Fi: solo fuera de las sesiones de desarrollo), GPS solo en entrenos, NFC se deja encendido (comprobado: no sondea con la pantalla apagada), sin escaneos BLE/Wi-Fi de ubicación (`settings put global ble_scan_always_enabled 0`, `wifi_scan_always_enabled 0`).
- **Notificaciones**: que solo lleguen al reloj las apps importantes (cada notificación despierta pantalla y radio).
- **Nuestras apps**: nada de servicios en segundo plano, wakelocks ni `AlarmManager` exactos; sensores con `maxReportLatency` alta (batching); repintar como máximo una vez por minuto; en la esfera analógica cachear en un `Bitmap` lo estático (marcas, pista) y repintar solo agujas y textos; colores sobre negro y nada de fondos claros.
- Guardar todos los valores originales de `settings` en `scripts/ajustes-originales.txt` y tener `restaurar.ps1`.

## Plan por fases (seguir en orden, confirmar con el usuario al acabar cada fase)

### Fase 0 — Compilar y diagnosticar ✅ (2026-10-21)
1. Generar wrapper y dejar `.\gradlew.bat assembleDebug` compilando **ambos** módulos sin añadir dependencias.
2. Ejecutar `scripts/diagnostico-reloj.ps1` (Windows) y leer el informe: launcher HOME actual, paquetes del sistema, features (`nfc`, `hce`, `bluetooth`), idioma, consumo, RAM por proceso.

### Fase 1 — Optimizar sin programar (`scripts/`)
3. `debloat.ps1`: lista revisada de paquetes chinos a `pm disable-user --user 0` (XiaoAI, Alipay, tienda Xiaomi, eSIM/operadoras, analíticas). **Nunca** desactivar salud/deporte/pasos/pulso, Wear OS core, Bluetooth, launcher activo ni teclado. Generar también `restaurar.ps1` con `pm enable`.
4. Ajustes de batería por ADB: brillo, timeout de pantalla, AOD, Wi-Fi solo cuando no hay BT… (con valores originales guardados para revertir).
5. Castellano: app mínima `:locale` (o paso en el launcher) que, con `pm grant io.xenxo.locale android.permission.CHANGE_CONFIGURATION`, aplica `es-ES` al sistema.

### Fase 1b — Traducir lo que queda en chino (castellano, con inglés de respaldo)
Sin root no se pueden editar los APK del sistema (firmados por Xiaomi). Tres capas, en orden:
1. **Lista de idiomas `es-ES` → `en-US`** con la app `:locale` (Android 9 admite lista: cada app usa castellano si lo trae y, si no, inglés antes que chino). Comprobar con `adb shell settings get system system_locales` y capturas de Ajustes y apps de Xiaomi.
2. **Inventario de lo que sigue en chino**: por cada app, `aapt dump badging <apk>` / `aapt dump resources` sobre el APK sacado con `adb pull` para ver qué idiomas trae (`locales:`). Pedir al usuario capturas de las pantallas que sigan en chino.
3. **Overlays de recursos (RRO) — experimental**: módulo `:overlays` con un overlay por app de Xiaomi que solo tenga `zh`, aportando `values-es/strings.xml` y `values-en/strings.xml` (traducción de los `strings` extraídos con apktool). Instalar y probar `adb shell cmd overlay list` / `cmd overlay enable --user 0 <paquete.overlay>`. **En Android 9 es posible que el sistema rechace overlays no firmados con la clave de plataforma o no preinstalados**: si `cmd overlay` no lo lista o no lo activa, abandonar esta vía y decírselo al usuario (no intentar root ni firmas falsas).
4. Si una app china sigue sin traducir y no aporta nada: ocultarla en `:launcher` o `pm disable-user`; si aporta algo (salud, deporte), su sustituto es nuestra app (`:health`, `:watchface`).
Nuestras apps: todos los textos en castellano (`values-es/`) con inglés de respaldo (`values/`).

### Fase 1c — `:power` (gestor de batería y perfiles) ✅ (2026-10-21)
Implementada como la primera sección de la app unificada `:xenxo` (`io.xenxo`). Ligera, sin servicios permanentes ni wakelocks. Permisos concedidos: `pm grant io.xenxo android.permission.WRITE_SECURE_SETTINGS` y `appops set io.xenxo WRITE_SETTINGS allow`.
- **Registro de batería**: Base de datos SQLite (`xenxo_battery.db`) pasiva que almacena nivel, temperatura y estado de carga. Cálculo de gasto en %/h, estimación de fin de batería ("Llegas hasta las HH:mm") y exportación CSV.
- **Perfiles** con un toque y punto de color activo:
  - *Día*: brillo 70, timeout 10s, levantar muñeca activo.
  - *Noche*: levantar muñeca desactivado, brillo mínimo (10/255), timeout 5s.
  - *Entreno*: brillo alto (150/255), pantalla 30s encendida.
  - *Ahorro extremo*: muñeca fuera, brillo mínimo, timeout 5s.
  - *Restaurar*: regresa a los ajustes originales guardados.
- **El AOD NO lo puede cambiar ninguna app nuestra** (corregido el 2026-10-21): es `ambient_enabled` en `content://com.google.android.wearable.settings/ambient_config`, que se puede **leer** pero escribir exige `com.google.android.wearable.WRITE_SETTINGS` (`signature|privileged`). Las primeras versiones de los perfiles tocaban `secure doze_enabled` creyendo que era el AOD (es el «ambient display» de los móviles): ya no; se devuelve a su valor de fábrica (0). `:power` muestra el estado real del AOD y abre Ajustes → Pantalla para cambiarlo.
- **Aviso e interruptor de ADB por Wi-Fi**: tarjeta de advertencia destacada cuando el puerto 5555 está escuchando, con botón para apagar el Wi-Fi al terminar de programar.

### Fase 2 — Launcher ✅ (2026-10-21)
`:launcher` es el **cajón de apps principal y predeterminado** (reemplazó y desinstaló definitivamente a T-Launcher), conviviendo con la base de Wear OS (HomeActivity2: esferas, notificaciones, tarjetas y ajustes rápidos). Estética Apple Watch:
- Panal hexagonal en columnas (vecinos arriba y abajo), sin nombres, fondo negro. Icono central ~88 px, separación entre centros ~90 px (46 dp), hueco ~5 px.
- Ojo de pez sobre la pantalla cuadrada: ~80 px en el primer anillo, ~55 px en el segundo, ~40 px en los bordes y ~20 px en las esquinas; los iconos de los bordes se acercan al centro para que no queden huecos.
- Iconos en círculo lleno: los adaptativos y los ya redondos a sangre; los cuadrados sobre un círculo gris oscuro (no fondos claros).
- Arrastre con inercia, zoom con la corona, al abrir una app el icono se amplía (animación del sistema, una sola vez).
- **Sin panel de control propio**: para eso están los ajustes rápidos de Wear OS. Los accesos a ajustes abren las pantallas reales.

6. Instalado con categoría `HOME` activa en `AndroidManifest.xml` y fijado con `adb shell cmd package set-home-activity io.xenxo.launcher/.HomeActivity`. T-Launcher desinstalado con `adb uninstall com.tanxi.tlauncher`.
7. Comprobar con un mensaje real del móvil que siguen llegando las notificaciones (no deberían cambiar: las recoge Wear OS).
8. Pulir: orden manual de apps, apps xenxo fijadas en el centro, ocultar las apps chinas que sobren.

### Fase 2b — Esfera `:watchface` ✅ (2026-10-21)
Ambas esferas (Digital y Analógica) creadas en `:launcher`, instaladas, añadidas a favoritas y verificadas en vivo en el reloj con recepción de complicaciones de Mi Fitness (pasos, kcal, km, batería).
- Esfera real de Wear OS 2 con `CanvasWatchFaceService` (`android.support.wearable.watchface`). **Única excepción** a cero dependencias: librería *wearable support* de Google (`com.google.android.support:wearable` como implementation + `com.google.android.wearable:wearable` como `compileOnly`). No depende de Play Services. Watch Face Format NO (exige Wear OS 4).
- Debe aparecer en el selector de esferas del sistema (la pinta la pantalla de inicio de Wear OS, con T-Launcher o con `:launcher`).
- **Modo activo** (368×448, negro puro, márgenes ~30 px):
  - Arriba: fecha corta en castellano (`Mié 21 oct`, gris #9E9E9E, 17 sp) a la izquierda; icono de batería + `44%` a la derecha.
  - Hora `HH:mm` muy grande (~112 px, Roboto Light, blanco). **Sin segundos**.
  - Pasos: número grande ámbar #FFB300 (`7.750`, separador de miles español) + `/ 10.000 pasos` en gris #757575 + barra de progreso fina (6 px, fondo #2A2A2A) hacia el objetivo (configurable, por defecto 10.000).
  - Separador fino #1E1E1E.
  - Tres datos en columnas: kcal (coral #FF7043), km con coma decimal (turquesa #26C6DA), pulso con corazón (rojo #EF5350); etiquetas `KCAL` `KM` `PPM` en gris, 14 sp, mayúsculas espaciadas.
  - Marca `XENXO` muy discreta abajo (#3A3A3A).
- **Modo ambiente**: solo fecha (#5A5A5A), hora en contorno (trazo #BDBDBD, sin relleno) y `N pasos` en gris. Sin colores, sin barra, antialias desactivado si `lowBitAmbient`, respetar `burnInProtection` desplazando unos px.
- Refresco: una vez por minuto (`onTimeTick`). Pasos con `TYPE_STEP_COUNTER` (o de `:health` cuando exista); kcal y km estimados a partir de pasos con altura/peso configurables; pulso: última lectura guardada por `:health` (la esfera **no** mide pulso continuo).
- Toques: anillo/zona de pasos → abre `:health`; hora → panal de `:launcher` (si está instalado).

**Segunda esfera en el mismo módulo: "xenxo Analógica"** (diseño aprobado: `docs/watchface-xenxo-analog.png`, generada con `docs/watchface-xenxo-analog.svg`)
- Aprovecha la pantalla cuadrada: el dial sigue la forma del cristal (superelipse `|x/a|^5 + |y/b|^5 = 1`), no un círculo.
- **Borde = progreso de pasos**: pista de 6 px a ~14 px del borde (#1C1C1C) y tramo ámbar #FFB300 desde las 12 en sentido horario, proporcional a pasos/objetivo.
- Marcas sobre la superelipse interior (~30 px del borde): 60 marcas; minutos cortas #4A4A4A (2 px), horas largas blancas (4 px), y las de 12/3/6/9 en ámbar (5 px). Sin números.
- Centro-arriba `MIÉ 21` (gris, espaciado). Centro-abajo: pasos en ámbar grande + `PASOS` en gris pequeño; debajo corazón rojo + pulso y `·` + batería en gris.
- Agujas blancas con punta redondeada (horas 10 px × 92, minutos 7 px × 150, pequeña cola), eje ámbar con punto negro. **Sin segundero.**
- Ambiente: solo marcas de hora en gris, agujas en contorno, fecha y `N pasos` en gris. Sin pista de pasos.

### Fase 3 — Apps (secciones de `:xenxo`; prioridad: gimnasio → pádel → resto)
> **Principio general: todo en LOCAL.** El reloj no tiene SIM; depender del móvil para datos no es práctico. Solo Wrist AI usa Internet. Objetivo de batería: cargar solo por la noche y llegar al día siguiente.

9. `:padel` — marcador de partido, 100 % local ✅ (2026-10-21, verificado en el reloj salvo la corona). Implementación: `io.xenxo.padel.*` — `PadelMatch` (reglas; el estado se recalcula repitiendo la lista de puntos, así deshacer = quitar el último y el partido se recupera tras cerrar la app), `PadelStore` (partido en curso en SharedPreferences, historial SQLite, CSV), `ScoreboardView` (pintado a mano 368×448, contorno en ambiente), `PadelActivity` (icono propio «Pádel», tarea `io.xenxo.padel`, sin deslizar para cerrar), `PadelView` (pestaña). Formato elegido por el usuario: mejor de 3, super tie-break a 10 opcional en el tercero, punto de oro elegible. Extra: aviso de cambio de lado. Acceso desde `:gym` los martes (pádel 21:00), jueves y sábados.
   - Pantalla partida: **mitad de arriba = "Ellos", mitad de abajo = "Nosotros"**; un toque en la mitad = punto para ese equipo. Corona hacia atrás = deshacer. Vibración corta al ganar juego, doble al ganar set.
   - Reglas: 15-30-40, ventaja o **punto de oro** (elegible), sets a 6 con tie-break a 7 en 6-6, partido a 2 o 3 sets, cambio de saque indicado con un punto en el lado que saca.
   - Batería: **no** mantener la pantalla encendida; el marcador debe verse en **modo ambiente** (números grandes en contorno) y un toque despierta. Sin sensores ni GPS.
   - Historial de partidos (fecha, resultado, duración) en local; exportable.
10. `:health` — pasos e historial diario (sensor `TYPE_STEP_COUNTER`, muestreo cerca de medianoche), pulso (`TYPE_HEART_RATE`, permiso `BODY_SENSORS`), exportar CSV por ADB. Sustituye la dependencia de los servidores de Xiaomi; el launcher leerá de aquí.
11. `:gym` — **entrenos de fuerza + peso en ayunas** ✅ (2026-10-21, verificado en el reloj):
   - Al abrir: rutina del día según `docs/planning-gym.md` (o selector de día). Cada ejercicio muestra la última sesión y viene **pre-rellenado**: repetir serie = 1 toque. Peso con corona giratoria (0,5 kg) y reps con +/−.
   - Cálculo automático de 1RM al vuelo según fórmula Epley y detección de récords.
   - Al guardar la serie arranca el temporizador de descanso automático con cuenta atrás, +30s, saltar y doble vibración háptica al concluir. La vibración la da `RestAlarm` (alarma exacta única) para que llegue a su hora con la pantalla apagada; **pendiente de verificar en un entreno real** (`adb shell dumpsys alarm | grep -A3 io.xenxo` durante el descanso).
   - Tarjeta de ejercicio con progreso visual en tiempo real (`X/Y`), cambiando a contorno verde y checkmark `✓` al completar todas las series objetivo.
   - **Peso en ayunas**: diálogo de registro rápido con corona giratoria en pasos de 0,1 kg y persistencia en SQLite (`xenxo_gym.db`).
   - Pantalla completa optimizada para 368×448 AMOLED, ocultando la barra de pestañas durante los overlays y con soporte para retroceso/onBackPressed.
   - Voz local (Vosk/PocketSphinx) planificada para fase posterior opcional.
12. `:intervals` (para después) — HIIT/EMOM/Tabata con vibraciones y pulso en directo.
13. `:notifications` (Respuesta a notificaciones en castellano por Bluetooth) — Servicio `NotificationListenerService` en el reloj:
   - **Interceptación y lectura cómoda**: Muestra el mensaje completo de WhatsApp, Telegram, SMS, Gmail, etc. en fondo negro AMOLED con scroll fluido mediante la corona giratoria háptica.
   - **Dictado de voz en castellano relámpago**: Pulsar micro ➔ transcripción en ~200 ms con Groq Whisper ➔ envío inmediato mediante la acción `RemoteInput` de la notificación original.
   - **Plantillas rápidas táctiles en español**: "Sí", "No", "Voy de camino", "Te llamo en 5 min", "Perfecto", más respuestas inteligentes contextuales (Smart Replies).
14. `:browser` (Navegador web Chromium optimizado para reloj) — APK o sección con WebView/Chromium nativo (API 28):
   - **Solución a portales cautivos de Wi-Fi**: Permite abrir y aceptar las páginas de login o términos de los Wi-Fi del gimnasio, hoteles y aeropuertos directamente desde el reloj sin depender del móvil.
   - **Optimización para pantalla 368×448 AMOLED**: Modo oscuro forzado (#000000 puro), bloqueo de JavaScript innecesario y Reader Mode con ajuste de texto al ancho de pantalla (sin scroll horizontal).
   - **Navegación con corona**: Desplazamiento ultra-suave por las páginas web con la corona física.
15. `:nfc` — leer pegatinas NTAG/NDEF y lanzar acciones (abrir app, iniciar entreno, alarma, Wake-on-LAN). Primer uso: pegatinas con URI `xenxo://gym` (rutina del día) y `xenxo://padel` (marcador), abiertas por *intent filter* sin nada en segundo plano (el NFC solo lee con la pantalla encendida y desbloqueada). **Emulación HCE confirmada (`nfc.hce`)**:
   - **Tesla Key Card nativa**: Emulación completa de tarjeta física Tesla con AID `teslaLogic` (`0x7465736C614C6F676963`), curva criptográfica `secp256r1` (NIST P-256) y APDUs `80 04` (clave pública) y `80 11` (desafío ECDH). Permite emparejar el reloj en la pantalla del coche (Bloqueos -> Añadir llave) y abrir/arrancar el Tesla acercando el reloj al pilar B o a la consola central, 100% offline y sin necesidad de móvil ni cobertura.
   - **Tarjeta de visita**: Emulación NDEF con vCard propia para compartir contacto acercando el reloj a cualquier smartphone.
16. `:tesla` (App remota y control de vehículo) — Sección en `:xenxo` con UI AMOLED 368×448:
   - **Climatización remota**: Encender/apagar aire acondicionado/calefacción y ajustar temperatura con la corona giratoria háptica.
   - **Estado y batería**: Porcentaje de batería en tiempo real, autonomía restante (km), estado de carga y bloqueo.
   - **Controles rápidos de 1 toque**: Apertura remota de maletero delantero (Frunk), maletero trasero y desbloqueo de puertas.
   - **Conectividad**: Tesla Fleet API (vía Bluetooth del smartphone o Wi-Fi) y BLE local (`tesla-vehicle-command`).
   - **Integración por voz con `:wristai`**: Comandos directos ("Enciende el aire del Tesla a 21 grados", "¿Cuánta batería le queda al coche?").
17. `:outdoor` (para después) — altímetro (barómetro), brújula, coordenadas GPS.
18. `:intervals` (para después) — HIIT/EMOM/Tabata con vibraciones y pulso en directo.
19. `:voicenotes` — grabadora offline; transcripción local con el mismo modelo Vosk (dictado libre, más lento: se transcribe al cargar el reloj) o, opcionalmente, en servidor local con Whisper al llegar a casa.
20. `:remote` (para después) — mando Bluetooth HID (API 28 `BluetoothHidDevice`): pasar diapositivas, multimedia.
21. `:wol` (para después) — Wake-on-LAN (paquete mágico UDP por Wi-Fi) para el homelab.
22. `:backup` (script, no app) — `scripts/backup.ps1` que guarda en el PC las preferencias y datos exportables de las apps xenxo y la lista de paquetes desactivados, para poder dejar el reloj igual tras un reset (`scripts/restaurar-todo.ps1`).

## Cómo trabajar
- Antes de cada módulo, plan breve (3-5 líneas) y esperar OK.
- Compilar tras cada cambio. Lo que solo se pueda verificar en el reloj: dar el comando exacto (`adb install -r …`, `adb logcat -s <tag>`) y qué buscar.
- Probar siempre como app normal antes de tocar ajustes del sistema.
- No subir nada a ningún sitio sin pedirlo.
- **Tras cada avance importante por ADB**, actualizar `HANDOFF.md` en la raíz del proyecto con el estado actual (esferas activas, permisos concedidos, ajustes temporales, punto de corte). Esto permite rotar entre Antigravity y Claude Code sin perder contexto.
