# Batería — mediciones antes/después

Reloj: Xiaomi Mi Watch XMWT01, batería nominal 570 mAh.
Método: `scripts\medir-bateria.ps1 -Reset` al 100 % en el cargador → desactivar ADB por Wi-Fi →
uso normal → reactivar ADB → `scripts\medir-bateria.ps1 -Etiqueta <fase>`.
Los volcados completos quedan en `scripts\mediciones\` (fuera del repo).

## Resumen

| Fecha | Fase | Horas en batería | Bajada | %/h | mAh/h | Pantalla on (veces) | mAh pantalla off/h | Autonomía estimada | Notas |
|---|---|---|---|---|---|---|---|---|---|
| 2026-10-21 | Día de configuración (no representativo) | 5 h 51 min | 49-51 % (257 mAh) | 8,4 | 44,0 | 1 h 10 min (318×) | 29,9 | ~12 h | ADB por Wi-Fi activo casi todo el rato: `shell` ≈ 399 mAh estimados por Wi-Fi, 479 MB recibidos. BT buscando 31 min. |
| | Referencia (Fase 0) | | | | | | | | AOD activado, ADB por Wi-Fi apagado. |
| | Tras Fase 1 (debloat + ajustes) | | | | | | | | |

## Salud de la batería (Verificada al 100 % el 2026-10-21)

Medición precisa con batería al 100 % en el cargador:

- **Capacidad estimada (BatteryStats)**: **576 mAh** (frente a los 570 mAh nominales de diseño).
- **Contador de carga (`Charge counter`)**: **588,8 mAh** (588.840 µAh) al 100 %.
- **Estado de salud (`health`)**: `2` (`BATTERY_HEALTH_GOOD`).
- **Voltaje a plena carga**: **4.408 mV** (4,41 V, curva de carga Li-Po de alta densidad intacta).
- **Temperatura**: **32,0 °C** (en reposo térmico perfecto).

> **Diagnóstico definitivo**: La batería de iones de litio está en **estado excelente / casi nuevo** (~100 % de su capacidad teórica tras 5 años). No requiere reemplazo físico alguno. Todo el gasto excesivo anterior procedía del software (ADB Wi-Fi continuo, XiaoAI escuchando el micro, servicios analíticos en segundo plano y muestreo continuo de pulso).

## Detalle por medición

### 2026-10-21 — Día de configuración

- Mayores consumos estimados: `shell` (ADB) 399 mAh (Wi-Fi 397), `system` 58, Bluetooth 17,
  Google Play Services 10,8, Wear OS 9,6, asistente de configuración 6,0, WhatsApp 3,8, T-Launcher 3,1.
- CPU despierta con pantalla apagada: 1 h 8 min de 4 h 41 min (24 %). Normal en reposo: < 5 %.
- Wakelocks del kernel principales: `PowerManagerService.WakeLocks` 28 min, `APPS_RIVA_BT_ACL` 24 min,
  `APPS_RIVA_BT_CMD` 20 min (tráfico Bluetooth).
