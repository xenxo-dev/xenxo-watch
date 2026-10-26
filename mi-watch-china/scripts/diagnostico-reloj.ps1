# diagnostico-reloj.ps1 — xenxo.io
# Saca un informe del Xiaomi Mi Watch (XMWT01) por ADB. Solo LEE, no cambia nada.
# Uso:  .\diagnostico-reloj.ps1 -Ip 192.168.1.50:5555
#       (o sin -Ip si el reloj ya aparece en "adb devices")
# Guardado en UTF-8 con BOM: sin BOM, PowerShell 5.1 lo lee como ANSI y la «—» rompe las cadenas.

param([string]$Ip = "")

$ErrorActionPreference = "Continue"
# La salida de adb viene en UTF-8 (nombres de apps en chino); sin esto PowerShell la lee con la página OEM.
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$stamp = Get-Date -Format "yyyyMMdd-HHmm"
$out = Join-Path $PSScriptRoot ("diagnostico-reloj-" + $stamp + ".txt")

if ($Ip) { adb connect $Ip | Out-Null }
if (-not (adb get-state 2>$null)) { Write-Host "No hay reloj conectado por ADB." -ForegroundColor Red; exit 1 }

function Seccion($titulo, $cmd) {
    Add-Content $out "`n===== $titulo =====`n$ adb shell $cmd" -Encoding UTF8
    $r = adb shell $cmd 2>&1 | Out-String
    Add-Content $out $r.TrimEnd() -Encoding UTF8
}

Set-Content $out "Diagnóstico Mi Watch — $(Get-Date)" -Encoding UTF8

Seccion "Sistema"            "getprop ro.product.model; getprop ro.build.version.release; getprop ro.build.version.sdk; getprop ro.build.display.id; getprop ro.cw_build.platform_mr"
Seccion "Idioma"             "getprop persist.sys.locale; getprop ro.product.locale; settings get system system_locales"
Seccion "Launcher actual"    "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
Seccion "Todos los HOME"     "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
Seccion "Esfera actual"      "settings get secure current_watchface; dumpsys wallpaper | grep -i -E 'component|service' | head -n 10"
Seccion "Funciones hardware" "pm list features | grep -i -E 'nfc|hce|bluetooth|sensor|telephony|euicc|watch|wifi'"
Seccion "NFC"                "dumpsys nfc | head -n 40"
Seccion "Batería"            "dumpsys battery"
Seccion "Salud batería (kernel)" "for f in capacity charge_counter charge_full charge_full_design cycle_count health voltage_now current_now temp; do echo `$f=`$(cat /sys/class/power_supply/battery/`$f 2>/dev/null); done"
Seccion "Consumo (resumen)"  "dumpsys batterystats --charged | grep -E 'Estimated power use|Uid|Screen|Wifi|Bluetooth|Cell' | head -n 60"
Seccion "Wakelocks y alarmas" "dumpsys power | grep -i -E 'wake lock|PARTIAL|mWakefulness|mScreenOffTimeout' | head -n 40"
Seccion "Memoria"            "cat /proc/meminfo | head -n 6"
Seccion "Procesos que más RAM usan" "dumpsys -t 60 meminfo | sed -n '/Total PSS by process/,/Total PSS by OOM/p' | head -n 40"
Seccion "Servicios en marcha" "dumpsys activity services | grep -E 'ServiceRecord' | head -n 60"
Seccion "Escuchas de notificaciones" "settings get secure enabled_notification_listeners"
Seccion "Apps del sistema"   "pm list packages -s"
Seccion "Apps de usuario"    "pm list packages -3"
Seccion "Apps desactivadas"  "pm list packages -d"
Seccion "Ajustes de pantalla" "settings get system screen_off_timeout; settings get system screen_brightness; settings get system screen_brightness_mode; settings get global always_on_display; settings get secure doze_enabled"
Seccion "Radios y escaneos"  "settings get global ble_scan_always_enabled; settings get global wifi_scan_always_enabled; settings get global wifi_on; settings get global bluetooth_on; settings get secure location_mode; settings get global adb_enabled; getprop service.adb.tcp.port"

# Copia íntegra de los ajustes tal y como están ahora: es la referencia para restaurar más adelante.
$ajustes = Join-Path $PSScriptRoot ("ajustes-diagnostico-" + $stamp + ".txt")
Set-Content $ajustes "Ajustes del reloj — $(Get-Date)" -Encoding UTF8
foreach ($ns in "system", "secure", "global") {
    Add-Content $ajustes "`n===== settings $ns =====" -Encoding UTF8
    Add-Content $ajustes ((adb shell settings list $ns 2>&1 | Out-String).TrimEnd()) -Encoding UTF8
}

Write-Host "Informe guardado en: $out" -ForegroundColor Green
Write-Host "Ajustes guardados en: $ajustes" -ForegroundColor Green
