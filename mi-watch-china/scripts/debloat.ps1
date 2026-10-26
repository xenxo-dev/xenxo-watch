# debloat.ps1 — xenxo.io
# Desactiva (pm disable-user, REVERSIBLE) apps chinas que gastan batería/RAM y no aportan nada fuera de China.
# Nunca desinstala. Se deshace con .\restaurar.ps1 -Paquetes.
# Uso:
#   .\debloat.ps1 -Ip 192.168.1.50:5555            # simulacro: solo enseña qué haría
#   .\debloat.ps1 -Ip 192.168.1.50:5555 -Aplicar   # lo hace y apunta cada paquete en debloat-aplicado.txt
# Guardado en UTF-8 con BOM para PowerShell 5.1.

param([string]$Ip = "", [switch]$Aplicar)

$ErrorActionPreference = "Continue"
[Console]::OutputEncoding = [Text.Encoding]::UTF8

# Revisada con el diagnóstico del 2026-10-21. Paquete = motivo.
$paquetes = [ordered]@{
    "com.xiaomi.wear.xiaoai"                          = "Asistente XiaoAI (solo chino)"
    "com.xiaomi.wear.anonymous.xiaoai"                = "XiaoAI en segundo plano: 48 MB de RAM y TTS chino siempre en marcha"
    "com.xiaomi.wear.hotwordle"                       = "Palabra de activación de XiaoAI (micro escuchando)"
    "com.onetrack.watch"                              = "Analíticas de Xiaomi (OneTrackService permanente)"
    "com.xiaomi.wear.market"                          = "Tienda de apps de Xiaomi"
    "com.xiaomi.wear.lpa"                             = "Gestor de eSIM (imposible fuera de China)"
    "com.xiaomi.wear.card"                            = "Tarjetas de transporte/pago chinas (14 MB de RAM)"
    "com.xiaomi.mihome"                               = "Mi Home (domótica de Xiaomi China)"
    "com.xiaomi.wear.tutorial"                        = "Tutorial inicial"
    "com.google.android.clockwork.gestures.tutorial"  = "Tutorial de gestos"
    "com.google.android.apps.wearable.retailattractloop" = "Demo de tienda"
    "com.longcheertel.midtest"                        = "Test de fábrica"
}
# NO tocar nunca: com.xiaomi.wearable y com.xiaomi.account (emparejado con Xiaomi Wear), wear.fitness,
# wear.sportlogger, wear.deskclock, wear.weather, wear.watchface.*, wear.charging, com.google.android.gms,
# com.google.android.wearable.app, apps.wearable.settings/systemui, bluetooth, nfc, iflytek (teclado),
# com.android.phone y todo lo de Qualcomm "sidekick" (chip que pinta el modo ambiente).
# Pendientes de valorar tras medir: com.xiaomi.wear.mqs, com.qualcomm.qti.operationmeasurement,
# com.google.android.inputmethod.pinyin, com.google.android.apps.handwriting.ime, wear.camera, wear.notes, wear.recorder.

if ($Ip) { adb connect $Ip | Out-Null }
if (-not (adb get-state 2>$null)) { Write-Host "No hay reloj conectado por ADB." -ForegroundColor Red; exit 1 }

$instalados = (adb shell "pm list packages" | Out-String) -split "`r?`n" | ForEach-Object { $_ -replace '^package:', '' }
$desactivados = (adb shell "pm list packages -d" | Out-String) -split "`r?`n" | ForEach-Object { $_ -replace '^package:', '' }
$registro = Join-Path $PSScriptRoot "debloat-aplicado.txt"

foreach ($p in $paquetes.Keys) {
    if ($instalados -notcontains $p) { Write-Host "  (no está)       $p"; continue }
    if ($desactivados -contains $p) { Write-Host "  (ya desactivado) $p"; continue }
    if (-not $Aplicar) { Write-Host "  desactivaría    $p  — $($paquetes[$p])" -ForegroundColor Yellow; continue }
    $r = (adb shell "pm disable-user --user 0 $p" 2>&1 | Out-String).Trim()
    if ($r -match 'disabled-user') {
        Add-Content $registro $p -Encoding UTF8
        Write-Host "  desactivado     $p" -ForegroundColor Green
    } else {
        Write-Host "  ERROR           $p : $r" -ForegroundColor Red
    }
}

if (-not $Aplicar) { Write-Host "`nSimulacro. Para aplicarlo: .\debloat.ps1 -Aplicar" -ForegroundColor Cyan }
else { Write-Host "`nHecho. Registro en $registro. Para deshacer: .\restaurar.ps1 -Paquetes" -ForegroundColor Green }
