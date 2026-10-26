# medir-bateria.ps1 — xenxo.io
# Medición de batería del Mi Watch. Por defecto solo LEE: vuelca batterystats y saca un resumen.
# Uso:
#   .\medir-bateria.ps1 -Ip 192.168.1.50:5555 -Reset     # al 100 % y en el cargador, justo antes de desconectar
#   .\medir-bateria.ps1 -Ip 192.168.1.50:5555            # al acabar el periodo de uso (vuelca y resume)
# Los volcados completos van a scripts\mediciones\ (fuera del repo); el resumen se pasa a docs\bateria.md.
# Guardado en UTF-8 con BOM para PowerShell 5.1.

param(
    [string]$Ip = "",
    [switch]$Reset,
    [switch]$Force,
    [string]$Etiqueta = "medicion"
)

$ErrorActionPreference = "Continue"
[Console]::OutputEncoding = [Text.Encoding]::UTF8

if ($Ip) { adb connect $Ip | Out-Null }
if (-not (adb get-state 2>$null)) { Write-Host "No hay reloj conectado por ADB." -ForegroundColor Red; exit 1 }

function Sh($cmd) { (adb shell $cmd 2>$null | Out-String).TrimEnd() }

$bateria = Sh "dumpsys battery"
$nivel = if ($bateria -match 'level:\s*(\d+)') { [int]$Matches[1] } else { -1 }
$cargando = $bateria -match '(AC|USB|Wireless) powered:\s*true'

if ($Reset) {
    if ((-not $cargando -or $nivel -lt 100) -and -not $Force) {
        Write-Host "El reloj está al $nivel % y cargando=$cargando. El reset se hace al 100 % y en el cargador (o usa -Force)." -ForegroundColor Yellow
        exit 1
    }
    Sh "dumpsys batterystats --reset" | Write-Host
    Write-Host ("Estadísticas a cero a las {0:HH:mm} con {1} %." -f (Get-Date), $nivel) -ForegroundColor Green
    Write-Host "Ahora: desactiva 'Depuración por Wi-Fi' en el reloj, desconéctalo del cargador y úsalo con normalidad." -ForegroundColor Green
    exit 0
}

# --- Volcado completo ---
$dir = Join-Path $PSScriptRoot "mediciones"
New-Item -ItemType Directory -Force $dir | Out-Null
$stamp = Get-Date -Format "yyyyMMdd-HHmm"
$raw = Join-Path $dir "batterystats-$Etiqueta-$stamp.txt"
$stats = (adb shell "dumpsys -t 60 batterystats --charged" 2>$null | Out-String)
Set-Content $raw $stats -Encoding UTF8
Add-Content $raw "`n===== dumpsys battery =====`n$bateria" -Encoding UTF8

# --- Resumen ---
function Horas($texto) {
    $h = 0.0
    if ($texto -match '(\d+)d') { $h += 24 * [int]$Matches[1] }
    if ($texto -match '(\d+)h') { $h += [int]$Matches[1] }
    if ($texto -match '(\d+)m(?!s)') { $h += [int]$Matches[1] / 60 }
    if ($texto -match '(\d+)s') { $h += [int]$Matches[1] / 3600 }
    $h
}
function Linea($patron) {
    $l = $stats -split "`n" | Where-Object { $_ -match $patron } | Select-Object -First 1
    if ($l) { $l.Trim() }
}

$tBat = Linea '^\s*Time on battery:'
$horas = Horas (($tBat -split 'realtime')[0])
$bajada = if ((Linea 'Amount discharged \(lower bound\)') -match '(\d+)') { [int]$Matches[1] } else { 0 }
$mah = if ((Linea '^\s*Discharge:') -match '([\d.]+) mAh') { [double]$Matches[1] } else { 0 }

# UID -> paquete, para que el ranking se entienda
$uids = @{}
foreach ($l in (Sh "pm list packages -U") -split "`n") {
    if ($l -match 'package:(\S+) uid:(\d+)') {
        $n = [int]$Matches[2] - 10000
        $k = "u0a$n"
        if ($uids.ContainsKey($k)) { $uids[$k] += ",$($Matches[1])" } else { $uids[$k] = $Matches[1] }
    }
}
$sistema = @{ "Uid 0" = "root/kernel"; "Uid 1000" = "system"; "Uid 1001" = "radio"; "Uid 1002" = "bluetooth"; "Uid 1027" = "nfc"; "Uid 2000" = "shell (ADB)" }

$ranking = $stats -split "`n" | Where-Object { $_ -match '^\s{4}(Uid \S+|Screen|Wifi|Bluetooth|Idle|Cell standby|Phone calls|Unaccounted|Over-counted):' } |
    Select-Object -First 12 | ForEach-Object {
        $l = $_.Trim()
        if ($l -match '^Uid (u0a\d+)' -and $uids[$Matches[1]]) { $l = "$l  <- $($uids[$Matches[1]])" }
        elseif ($l -match '^(Uid \d+):' -and $sistema[$Matches[1]]) { $l = "$l  <- $($sistema[$Matches[1]])" }
        $l
    }
$wakelocks = $stats -split "`n" | Where-Object { $_ -match '^\s*Kernel Wake lock' } | Select-Object -First 8 | ForEach-Object { $_.Trim() }

$resumen = @(
    "Medición '$Etiqueta' — $(Get-Date -Format 'yyyy-MM-dd HH:mm')"
    "Nivel ahora: $nivel %  (cargando: $cargando)"
    $tBat
    (Linea '^\s*Time on battery screen off:')
    (Linea '^\s*Time on battery screen doze:')
    (Linea '^\s*Screen on:')
    (Linea '^\s*Discharge:')
    (Linea '^\s*Screen off discharge:')
    (Linea '^\s*Screen doze discharge:')
    (Linea '^\s*Screen on discharge:')
    (Linea 'Amount discharged \(lower bound\)')
    (Linea 'Capacity:.*actual drain')
    (Linea 'Bluetooth scan time')
    (Linea '^\s*Device light idling')
    (Linea '^\s*Device full idling')
    ""
    ("Gasto medio: {0:N1} %/h  |  {1:N1} mAh/h  |  autonomía estimada a este ritmo: {2:N1} h" -f `
        ($(if ($horas) { $bajada / $horas } else { 0 })), ($(if ($horas) { $mah / $horas } else { 0 })), ($(if ($bajada) { 100 * $horas / $bajada } else { 0 })))
    ""
    "Mayores consumos estimados (mAh):"
    $ranking
    ""
    "Wakelocks del kernel:"
    $wakelocks
) | Where-Object { $_ -ne $null }

$res = Join-Path $dir "resumen-$Etiqueta-$stamp.txt"
Set-Content $res $resumen -Encoding UTF8
$resumen | ForEach-Object { Write-Host $_ }
Write-Host "`nVolcado: $raw`nResumen: $res" -ForegroundColor Green
