# xenxo-watch ⌚

Suite de software autónomo, launcher personalizado, asistente de IA por voz y aplicaciones de fitness y salud para la familia de smartwatches **Xiaomi**.

Diseñado bajo la filosofía de **privacidad absoluta**, fondos negro puro **AMOLED (#000000)**, consumo de energía optimizado al milivatio y sincronización bidireccional por Bluetooth.

---

## 🎯 Plataformas soportadas

Este repositorio contiene dos integraciones especializadas según la arquitectura hardware y versión del sistema operativo del reloj:

```text
xenxo-watch/
├── mi-watch-china/       # Xiaomi Mi Watch (XMWT01) — ROM China / Wear OS 2 (Android 9)
└── xiaomi-watch-2/       # Xiaomi Watch 2 / 2 Pro — Wear OS 4+ Oficial (Android 13/14)
```

---

### 1. `mi-watch-china/` — Xiaomi Mi Watch (XMWT01, 2019)
Optimizada para el icónico primer reloj inteligente de Xiaomi con pantalla cuadrada y ROM local:
* **Hardware**: Snapdragon Wear 3100 (32-bit `msm8909`), ~900 MB RAM, 4 GB almacenamiento.
* **Pantalla**: Cuadrada 368×448 AMOLED con corona giratoria física (`SOURCE_ROTARY_ENCODER`).
* **Sistema**: Android 9 (API 28) · Wear OS 2 China («Local Edition»). Sin Google Play Services global.
* **Características**:
  * **Launcher Honeycomb**: Cajón de aplicaciones hexagonal estilo Apple Watch con efecto ojo de pez, inercia y zoom con corona.
  * **Esferas xenxo**: Digital, Analógica y Ultra con métricas de salud (pasos, pulso PPG, kcal, km) leídas en tiempo real.
  * **xenxo Suite (11 apps locales)**: Gym con 1RM y descansos, Pádel con marcador AOD, Salud PPG, Batería, Música offline (20 pistas), Navegador Chromium (resolución de portales cautivos Wi-Fi), Tesla (Key Card NFC HCE P-256 + control remoto), Intervalos, Brújula/Altímetro, Mando multimedia, Wake-on-LAN y Videojuego `xenxoRun`.
  * **Wrist AI**: Asistente por voz relámpago con Groq LPU Whisper (<300 ms) + Llama 3.3 70B / Gemini Flash y atajos locales en 10 ms.
  * Guía completa y scripts de debloat y batería en [`mi-watch-china/README.md`](mi-watch-china/README.md).

---

### 2. `xiaomi-watch-2/` — Xiaomi Watch 2 / Watch 2 Pro (2024+)
Aprovecha la potencia del hardware de última generación y Wear OS global oficial:
* **Hardware**: Snapdragon W5+ Gen 1 (4 nm, arquitectura 64-bit quad-core + coprocesador Cortex-M55 de ultra-bajo consumo), 2 GB RAM LPDDR4X, 32 GB almacenamiento.
* **Pantalla**: Circular 466×466 AMOLED (1.43", 326 ppi, hasta 600 nits).
* **Sistema**: Wear OS 3.5 / 4 / 5 (Android 11/13/14, API 30/33/34) con **Google Play Services global oficial**.
* **Sensores**: Sensor de frecuencia cardíaca PPG de 12 canales, SpO2, sensor de temperatura corporal, acelerómetro, giroscopio, barómetro y GPS de doble banda (L1+L5).
* **Control**: Pantalla táctil capacitiva y 2 botones físicos dedicados (en Watch 2) o corona rotatoria háptica (en Watch 2 Pro).
* **Características**:
  * **UI Circular 466×466**: Diseñada para pantalla redonda sin cortes perimetrales, con `ScalingLazyColumn` y diales curvos.
  * **Google Health Services API**: Acceso continuo y eficiente a métricas biológicas sin sobrecoste de batería.
  * **Google Play Wearable Data Layer**: Sincronización instantánea con el móvil sin trucos de paquetes ni ROMs intermedias.
  * **Wrist AI HD**: Voz TTS de alta definición, micrófono estéreo con cancelación de ruido y soporte para respuestas de IA complejas con Groq y Gemini.
  * Guía completa en [`xiaomi-watch-2/README.md`](xiaomi-watch-2/README.md).

---

## 🔒 Privacidad y Configuración

El proyecto no contiene tokens, claves de API ni credenciales privadas:
* Para habilitar el asistente inteligente por voz, copia `wristai/config.example.json` a `config.json` con tus claves personales (Groq, Gemini o Anthropic).
* Para el control remoto de vehículos Tesla, copia `xenxo/tesla_config.example.json` a `tesla_config.json`. En ausencia de archivo, el reloj funciona en modo simulación offline seguro.

---

## 📜 Licencia

Distribuido bajo licencia **MIT**. Consulta el archivo [`LICENSE`](LICENSE) para más detalles.
