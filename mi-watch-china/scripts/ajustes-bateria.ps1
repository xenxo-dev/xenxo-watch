# ajustes-bateria.ps1 — xenxo.io
# Ajustes de batería por ADB. Antes de tocar nada guarda el valor original de cada clave en
# ajustes-originales.txt (solo la primera vez: nunca se sobrescribe un original ya guardado).
# Se deshace con .\restaurar.ps1 -Ajustes.
# Uso:
#   .\ajustes-bateria.ps1 -Ip 192.168.1.50:5555                    # simulacro
#   .\ajustes-bateria.ps1 -Ip 192.168.1.50:5555 -Aplicar           # aplica
#   .\ajustes-bateria.ps1 -Aplicar -Brillo 50 -WifiAuto              # ojo: -WifiAuto corta el ADB por Wi-Fi
# Guardado en UTF-8 con BOM para PowerShell 5.1.

param([string]$Ip = "", [switch]$Aplicar, [int]$Brillo = 70, [switch]$WifiAuto)

$ErrorActionPreference = "Continue"
[Console]::OutputEncoding = [Text.Encoding]::UTF8

# Espacio, clave, valor nuevo, motivo. AOD, levantar muñeca, ubicación y radio móvil NO se tocan (decisión del usuario).
# Las comas entre filas son necesarias: sin ellas PowerShell aplana la lista.
$cambios = @(
    @("system", "screen_brightness",       "$Brillo", "Brillo fijo bajo (estaba a 134/255)"),
    @("global", "wifi_wakeup_enabled",     "0",       "Que el Wi-Fi no se encienda solo al acercarse a redes guardadas"),
    @("global", "ble_scan_always_enabled", "0",       "Sin búsquedas Bluetooth de ubicación en segundo plano"),
    @("global", "wifi_scan_always_enabled","0",       "Sin búsquedas Wi-Fi de ubicación en segundo plano"),
    @("global", "mobile_data",             "0",       "Datos móviles fuera: no hay SIM (las llamadas van por el móvil)")
)
# Wi-Fi solo cuando no hay Bluetooth. Va aparte porque al aplicarlo el reloj apaga el Wi-Fi y se pierde el ADB.
# En Wear OS esta clave vive en "system" (valores: on, off, auto, off_airplane).
if ($WifiAuto) { $cambios += ,@("system", "clockwork_wifi_setting", "auto", "Wi-Fi automático: solo sin conexión Bluetooth con el móvil") }

if ($Ip) { adb connect $Ip | Out-Null }
if (-not (adb get-state 2>$null)) { Write-Host "No hay reloj conectado por ADB." -ForegroundColor Red; exit 1 }

$originales = Join-Path $PSScriptRoot "ajustes-originales.txt"
$guardados = @{}
if (Test-Path $originales) {
    Get-Content $originales -Encoding UTF8 | Where-Object { $_ -match '^\S+ \S+ ' } | ForEach-Object {
        $c = $_ -split ' ', 3; $guardados["$($c[0]) $($c[1])"] = $true
    }
}

foreach ($c in $cambios) {
    $ns, $clave, $nuevo, $motivo = $c
    $actual = (adb shell "settings get $ns $clave" | Out-String).Trim()
    if ($actual -eq $nuevo) { Write-Host "  (ya está)  $ns $clave = $actual"; continue }
    if (-not $Aplicar) { Write-Host "  cambiaría  $ns $clave : $actual -> $nuevo  — $motivo" -ForegroundColor Yellow; continue }
    if (-not $guardados["$ns $clave"]) {
        # Formato: espacio clave valor ("null" = no existía; al restaurar se borra)
        Add-Content $originales "$ns $clave $actual" -Encoding UTF8
        $guardados["$ns $clave"] = $true
    }
    adb shell "settings put $ns $clave $nuevo" | Out-Null
    $comprobado = (adb shell "settings get $ns $clave" | Out-String).Trim()
    $color = if ($comprobado -eq $nuevo) { "Green" } else { "Red" }
    Write-Host "  cambiado   $ns $clave : $actual -> $comprobado" -ForegroundColor $color
}

if (-not $Aplicar) { Write-Host "`nSimulacro. Para aplicarlo: .\ajustes-bateria.ps1 -Aplicar" -ForegroundColor Cyan }
else { Write-Host "`nHecho. Originales en $originales. Para deshacer: .\restaurar.ps1 -Ajustes" -ForegroundColor Green }
