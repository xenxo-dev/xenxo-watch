# Xiaomi Watch 2 / 2 Pro · xenxo-watch ⌚

Adaptación de la suite **xenxo OS** para el **Xiaomi Watch 2** y **Xiaomi Watch 2 Pro** con **Wear OS 4+ oficial de Google** (Android 13/14, API 33/34) y pantalla circular AMOLED de 466×466 píxeles.

---

## ⚡ Ventajas del Xiaomi Watch 2 frente a generaciones anteriores

* **Procesador Snapdragon W5+ Gen 1 (4 nm)**:
  * 4 núcleos Cortex-A53 a 1.7 GHz para ejecución fluida de interfaces y lógica de IA.
  * Coprocesador Cortex-M55 (250 MHz) que se encarga de sensores y pantalla de bajo consumo en modo ambiente (AOD) sin despertar la CPU principal.
* **Memoria y Almacenamiento**:
  * **2 GB de RAM LPDDR4X** (multitarea real sin cierres de procesos por falta de memoria).
  * **32 GB de memoria eMMC** (espacio para cientos de canciones, registros locales y cachés).
* **Pantalla Circular AMOLED 466×466 (1.43 pulgadas)**:
  * Densidad de 326 ppi y brillo de hasta 600 nits.
  * Geometría circular nativa: interfaces rediseñadas con curvatura concéntrica, márgenes `BoxInsetLayout` y paddings dinámicos.
* **Ecosistema Wear OS Oficial de Google**:
  * Google Play Services Global preinstalado.
  * Google Health Services API (`androidx.health.services.client`) con lecturas biométricas estandarizadas de alta eficiencia.
  * Sincronización transparente con cualquier smartphone Android mediante el canal estándar Wearable Data Layer.

---

## 📦 Estructura de módulos

```text
xiaomi-watch-2/
├── launcher/         # Launcher circular + Esferas circulares 466x466
├── xenxo/            # Suite deportiva (Gym, Pádel, Salud, Tesla, Navegador, Energía)
└── wristai/          # Asistente de voz por IA con VoiceOrb circular de 466x466
```

---

## 🚀 Compilación e Instalación

### Requisitos
* JDK 17 o superior.
* Android SDK Platform 34 (Android 14) / Platform 33.
* ADB activo en el reloj (Ajustes → Sistema → Información → 7 toques en «Número de compilación» → Opciones de desarrollador → Depuración inalámbrica).

### Compilación de todos los APKs
```bash
cd xiaomi-watch-2
.\gradlew.bat assembleDebug
```

Los paquetes resultantes se generan en:
* `launcher/build/outputs/apk/debug/launcher-debug.apk`
* `xenxo/build/outputs/apk/debug/xenxo-debug.apk`
* `wristai/build/outputs/apk/debug/wristai-debug.apk`

### Despliegue por ADB Wi-Fi
```bash
adb connect <ip-del-xiaomi-watch-2>:5555
adb install -r launcher/build/outputs/apk/debug/launcher-debug.apk
adb install -r xenxo/build/outputs/apk/debug/xenxo-debug.apk
adb install -r wristai/build/outputs/apk/debug/wristai-debug.apk
```

---

## 🎨 Adaptaciones Circulares

1. **Gestos táctiles perimetrales**:
   * En el Xiaomi Watch 2 estándar (con dos botones físicos sin corona rotatoria), las operaciones de ajuste fino (pesos de gimnasio, temperatura del Tesla, temporizador de descanso) admiten **deslizamiento táctil en arco por el borde de la pantalla**, emulando una rueda de corona virtual.
2. **Distribución concéntrica**:
   * Las tarjetas y métricas concentran la información en el círculo central de 380 px, garantizando que ningún texto ni icono se corte por la curvatura exterior del bisel.
3. **Sensores de Salud con Google Health Services**:
   * Soporte de lectura de pulso PPG de 12 canales, oxígeno en sangre SpO2 y estimación precisa de consumo calórico adaptado al perfil del usuario.
