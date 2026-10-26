# Pre-prompt para Claude Code (pegar como primer mensaje)

Copia desde la línea siguiente hasta el final y pégalo al lanzar `claude` dentro de la carpeta `xenxo-watch`.

---

Hola. Vamos a trabajar en el repositorio **xenxo-watch**: personalizar por completo mi Xiaomi Mi Watch XMWT01 (versión china, Android 9 / Wear OS 2 China, sin Google Play Services), emparejado con smartphone Android. Mi marca es xenxo.io. Trabajo desde **Windows** y quiero que me hables siempre en **castellano**.

**Antes de nada, lee `CLAUDE.md` entero.** Ahí están las restricciones del reloj, las reglas de diseño (cero dependencias, negro puro, nada en segundo plano), el estado del repo y el plan por fases. También están los mockups aprobados de las esferas en `docs/`.

**La prioridad nº 1 es la batería.** Dato real de hoy: lo cogí al 100 % a las 8:00 y a las 13:14 estaba al 47 % (44 % a las 13:52). Ese rato fue de uso intenso (reseteo, emparejado, instalar apps, ADB por Wi-Fi activo casi todo el tiempo), pero aun así quiero saber cuánto gasta en uso normal y llegar a un día completo **manteniendo la pantalla en reposo (AOD) activada**, porque me gusta.

Cómo quiero que trabajes:
1. Sigue las fases de `CLAUDE.md` en orden. Al empezar cada fase, dame un plan de 3-5 líneas y espera mi OK.
2. Compila tras cada cambio con `.\gradlew.bat assembleDebug`. Si algo solo se puede comprobar en el reloj, dame el comando exacto (adb install, adb shell, adb logcat) y dime qué buscar en la salida. Yo te pego los resultados.
3. Nada irreversible en el reloj: `pm disable-user` sí, `pm uninstall` de apps del sistema nunca. Guarda siempre los ajustes originales y prepara el script para restaurarlos.
4. Ni claves ni tokens en ficheros versionados.
5. Si algo no se puede hacer en este reloj (root, eSIM, pagos NFC, overlays bloqueados…), dímelo claro y propón la alternativa. No intentes saltarte protecciones.

**Empieza por la Fase 0:**
- Genera el Gradle wrapper (`gradle wrapper --gradle-version 8.14.3`) y deja compilando `:launcher` y `:wristai` sin añadir dependencias. Si me falta algo en el PC (JDK 17, Android SDK, variables de entorno), dime exactamente qué instalar.
- Después dime cómo conectar el reloj por ADB y cómo ejecutar `scripts\diagnostico-reloj.ps1`. Te pasaré el `.txt`.
- Con el informe, arranca la **medición de batería de referencia** descrita en `CLAUDE.md` y prepara la Fase 1 (limpieza de apps chinas, ajustes de batería, castellano) y la Fase 1c (`:power`).
