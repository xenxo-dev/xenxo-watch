# Wrist AI · xenxo.io

Asistente de voz para el **Xiaomi Mi Watch (XMWT01, Wear OS 2 China / Android 9)**.
Pulsas 🎤, preguntas, pulsas ■ y el reloj te lee la respuesta de **Gemini, ChatGPT o Claude**.

- Sin Google Play Services ni dependencias: solo el framework de Android.
- Gemini recibe el audio directamente (una sola llamada).
- ChatGPT y Claude: primero voz → texto (con Gemini u OpenAI) y luego la pregunta.
- Guarda las últimas 6 preguntas para que puedas repreguntar ("¿y mañana?").

## Controles

| Acción | Qué hace |
|---|---|
| Tocar 🎤 | Empieza a grabar (se corta solo a los 30 s) |
| Tocar ■ | Envía la pregunta |
| Tocar el chip `Gemini` | Cambia a ChatGPT → Claude → Gemini |
| Mantener el chip | Nueva conversación (borra el contexto) |
| Corona | Desplaza la respuesta |

## 1. Claves de API

Las suscripciones (Gemini Advanced, ChatGPT Plus, Claude Pro) **no sirven**: hacen falta claves de API, que se pagan por uso.

| Proveedor | Dónde | Necesaria para |
|---|---|---|
| Gemini | https://aistudio.google.com/apikey | Gemini, y voz→texto por defecto para los otros dos |
| OpenAI | https://platform.openai.com/api-keys | ChatGPT (y voz→texto si pones `"stt": "openai"`) |
| Anthropic | https://console.anthropic.com/settings/keys | Claude |

Con **solo la de Gemini** ya funciona todo lo de Gemini. Para usar Claude necesitas la de Anthropic **y** la de Gemini (o la de OpenAI con `"stt": "openai"`), porque Claude no acepta audio.

## 2. Compilar

1. Abre la carpeta en **Android Studio** (File → Open). Deja que sincronice Gradle; si te ofrece actualizar AGP o Kotlin, acepta.
2. Build → **Build APK(s)**. El APK queda en `wristai/build/outputs/apk/debug/wristai-debug.apk`.

## 3. Instalar en el reloj

En el reloj: Ajustes → Sistema → Acerca de → toca 7 veces la versión → Opciones de desarrollador → activa **Depuración ADB** y **Depuración por Wi-Fi** (te enseña IP:puerto).

```bash
adb connect IP_DEL_RELOJ:5555
adb install -r wristai/build/outputs/apk/debug/wristai-debug.apk
```

O sin PC: pasa el APK al móvil e instálalo con **WearOS Toolbox** o **Bugjaeger**.

## 4. Configurar (claves)

1. Abre **Wrist AI** una vez en el reloj (crea su carpeta) y acepta el permiso de micrófono.
2. Copia `config.example.json` a `config.json`, rellena tus claves y envíalo:

```bash
adb push config.json /sdcard/Android/data/io.xenxo.wristai/files/config.json
```

3. Cierra y vuelve a abrir la app → verás **"Configuración importada ✓"**. La app guarda las claves en su almacenamiento privado y **borra** el `config.json` del reloj. Borra también tu copia del PC si no la necesitas.

Para cambiar algo (modelo, proveedor por defecto…) repite el `push` solo con las claves que quieras cambiar.

### Opciones de `config.json`

| Clave | Por defecto | Notas |
|---|---|---|
| `provider` | `GEMINI` | `GEMINI`, `CHATGPT` o `CLAUDE` al abrir |
| `stt` | `gemini` | Quién transcribe para ChatGPT/Claude: `gemini` u `openai` |
| `language` | `es` | Idioma para la transcripción de OpenAI |
| `tts` | `true` | `false` = solo texto, sin voz |
| `gemini_model` | `gemini-flash-latest` | Alias que apunta al Flash más reciente |
| `openai_model` | `gpt-5.6-luna` | Modelo rápido y barato de OpenAI |
| `openai_stt_model` | `gpt-transcribe` | Transcripción de OpenAI |
| `claude_model` | `claude-haiku-5-5` | Cambia a `claude-sonnet-5-5` si quieres más calidad |
| `system_prompt` | respuestas cortas en español | Personalidad/estilo del asistente |

Si un modelo da `HTTP 404`, es que ese ID ya no existe: cambia el nombre en `config.json`.

## Voz

La respuesta se lee con el **Google Text-to-Speech** del reloj (ya lo tienes instalado). Si no habla, en el reloj ve a Ajustes → Accesibilidad → Texto a voz y descarga la voz en **español (España)**.

## Problemas típicos

| Síntoma | Causa probable |
|---|---|
| `Sin claves de API` | No se importó el `config.json` (revisa la ruta del `push` y reabre la app) |
| `HTTP 401/403` | Clave incorrecta o sin saldo/facturación activada |
| `HTTP 404` | ID de modelo obsoleto → cámbialo en `config.json` |
| `HTTP 429` | Límite de uso (capa gratuita de Gemini agotada) |
| Se queda en "Pensando…" | Sin conexión: el reloj sale a Internet por el Bluetooth del móvil o por su Wi-Fi |

## Siguiente paso posible

Pasar las peticiones por un proxy o servidor propio: las claves no estarían en el reloj y podrías añadir modelos locales como cuarto proveedor.
