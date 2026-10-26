# restaurar.ps1 — xenxo.io
# Deshace debloat.ps1 (vuelve a activar los paquetes) y ajustes-bateria.ps1 (pone los valores originales).
# Uso:
#   .\restaurar.ps1 -Ip 192.168.1.50:5555               # las dos cosas
#   .\restaurar.ps1 -Paquetes                             # solo paquetes
#   .\restaurar.ps1 -Ajustes                              # solo ajustes
# Guardado en UTF-8 con BOM para PowerShell 5.1.

param([string]$Ip = "", [switch]$Paquetes, [switch]$Ajustes)

$ErrorActionPreference = "Continue"
[Console]::OutputEncoding = [Text.Encoding]::UTF8
if (-not $Paquetes -and -not $Ajustes) { $Paquetes = $true; $Ajustes = $true }

if ($Ip) { adb connect $Ip | Out-Null }
if (-not (adb get-state 2>$null)) { Write-Host "No hay reloj conectado por ADB." -ForegroundColor Red; exit 1 }

if ($Paquetes) {
    $registro = Join-Path $PSScriptRoot "debloat-aplicado.txt"
    if (Test-Path $registro) {
        Get-Content $registro -Encoding UTF8 | Where-Object { $_.Trim() } | Sort-Object -Unique | ForEach-Object {
            $r = (adb shell "pm enable $_" 2>&1 | Out-String).Trim()
            Write-Host "  $r"
        }
        Rename-Item $registro ("debloat-aplicado-restaurado-" + (Get-Date -Format "yyyyMMdd-HHmm") + ".txt")
    } else { Write-Host "No hay debloat-aplicado.txt: no hay paquetes que reactivar." }
}

if ($Ajustes) {
    $originales = Join-Path $PSScriptRoot "ajustes-originales.txt"
    if (Test-Path $originales) {
        Get-Content $originales -Encoding UTF8 | Where-Object { $_ -match '^\S+ \S+ ' } | ForEach-Object {
            $ns, $clave, $valor = $_ -split ' ', 3
            if ($valor -eq "null") { adb shell "settings delete $ns $clave" | Out-Null }
            else { adb shell "settings put $ns $clave $valor" | Out-Null }
            Write-Host "  $ns $clave = $((adb shell "settings get $ns $clave" | Out-String).Trim())"
        }
        # ajustes-originales.txt se conserva: sigue siendo la referencia de fábrica.
    } else { Write-Host "No hay ajustes-originales.txt: no hay ajustes que restaurar." }
}
