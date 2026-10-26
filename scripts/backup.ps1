# scripts/backup.ps1
# Copia de seguridad integral de Xenxo Watch para Xiaomi Mi Watch (XMWT01)

param (
    [string]$Dispositivo = "",
    [string]$Destino = "backups"
)

$ErrorActionPreference = "Continue"
$Adb = "adb"

# Si no se pasa dispositivo, intentar detectar el reloj conectado
if ([string]::IsNullOrWhiteSpace($Dispositivo)) {
    $connected = (& $Adb devices) | Where-Object { $_ -match "\tdevice$" } | ForEach-Object { ($_ -split "\t")[0] }
    if ($connected.Count -eq 1) {
        $Dispositivo = $connected[0]
    } elseif ($connected.Count -gt 1) {
        # Preferir puerto 5555 típico de Wi-Fi ADB del reloj
        $watch = $connected | Where-Object { $_ -match ":5555$" } | Select-Object -First 1
        $Dispositivo = if ($watch) { $watch } else { $connected[0] }
    } else {
        Write-Error "No se especificó -Dispositivo y no hay dispositivos ADB conectados."
        exit 1
    }
}

Write-Host "=================================================" -ForegroundColor Cyan
Write-Host "  XENXO WATCH - COPIA DE SEGURIDAD AUTOMATIZADA  " -ForegroundColor Cyan
Write-Host "=================================================" -ForegroundColor Cyan

# 1. Asegurar conexion ADB
Write-Host "[1/6] Conectando con el reloj ($Dispositivo)..." -ForegroundColor Yellow
if ($Dispositivo -match ":") {
    $null = & $Adb connect $Dispositivo
}

$devs = & $Adb -s $Dispositivo get-state
if ($LASTEXITCODE -ne 0) {
    Write-Error "El reloj no responde en $Dispositivo."
    exit 1
}
Write-Host "  [OK] Reloj conectado correctamente." -ForegroundColor Green

# 2. Crear carpeta de destino con timestamp
$Fecha = Get-Date -Format "yyyyMMdd-HHmmss"
$BaseDir = Split-Path -Parent $PSScriptRoot
$BackupDir = Join-Path $BaseDir (Join-Path $Destino "backup-$Fecha")
$null = New-Item -ItemType Directory -Path $BackupDir -Force
Write-Host "[2/6] Carpeta de backup creada: $BackupDir" -ForegroundColor Yellow

# 3. Extraer bases de datos SQLite locales de xenxo
Write-Host "[3/6] Respaldando bases de datos SQLite locales..." -ForegroundColor Yellow
$DbDir = Join-Path $BackupDir "databases"
$null = New-Item -ItemType Directory -Path $DbDir -Force

$Databases = @("xenxo_gym.db", "xenxo_battery.db", "xenxo_padel.db", "xenxo_health.db")
foreach ($db in $Databases) {
    $null = & $Adb -s $Dispositivo shell "run-as io.xenxo cp databases/$db /sdcard/$db"
    $localFile = Join-Path $DbDir $db
    $null = & $Adb -s $Dispositivo pull "/sdcard/$db" $localFile
    $null = & $Adb -s $Dispositivo shell "rm -f /sdcard/$db"
    if (Test-Path $localFile) {
        $len = (Get-Item $localFile).Length
        Write-Host "  [OK] $db respaldada ($len bytes)" -ForegroundColor Green
    } else {
        Write-Host "  [--] $db omitida (sin datos todavia)" -ForegroundColor DarkGray
    }
}

# 4. Extraer preferencias
Write-Host "[4/6] Respaldando configuraciones y claves Tesla HCE..." -ForegroundColor Yellow
$PrefsDir = Join-Path $BackupDir "shared_prefs"
$null = New-Item -ItemType Directory -Path $PrefsDir -Force

$PrefsFiles = @(
    "launcher.xml",
    "tesla_key_store.xml",
    "tesla_state.xml",
    "outdoor_prefs.xml",
    "wol_prefs.xml"
)

foreach ($p in $PrefsFiles) {
    $null = & $Adb -s $Dispositivo shell "run-as io.xenxo cp shared_prefs/$p /sdcard/$p"
    $localFile = Join-Path $PrefsDir $p
    $null = & $Adb -s $Dispositivo pull "/sdcard/$p" $localFile
    $null = & $Adb -s $Dispositivo shell "rm -f /sdcard/$p"
    if (Test-Path $localFile) {
        Write-Host "  [OK] $p respaldada." -ForegroundColor Green
    }
}

# 5. Exportar ajustes del sistema y lista de paquetes deshabilitados
Write-Host "[5/6] Exportando estado de Android y debloat..." -ForegroundColor Yellow
& $Adb -s $Dispositivo shell pm list packages -d | Out-File -FilePath (Join-Path $BackupDir "paquetes-deshabilitados.txt") -Encoding ascii
& $Adb -s $Dispositivo shell settings list system | Out-File -FilePath (Join-Path $BackupDir "settings-system.txt") -Encoding ascii
& $Adb -s $Dispositivo shell settings list secure | Out-File -FilePath (Join-Path $BackupDir "settings-secure.txt") -Encoding ascii
& $Adb -s $Dispositivo shell settings list global | Out-File -FilePath (Join-Path $BackupDir "settings-global.txt") -Encoding ascii
Write-Host "  [OK] Ajustes y paquetes exportados." -ForegroundColor Green

# 6. Generar script de restauracion
Write-Host "[6/6] Generando script de restauracion..." -ForegroundColor Yellow
$RestoreScript = @"
param ([string]`$Dispositivo = "$Dispositivo")
`$Adb = "$Adb"
Write-Host "Restaurando backup a `$Dispositivo..." -ForegroundColor Cyan

Get-ChildItem -Path "`$PSScriptRoot\databases" -Filter *.db | ForEach-Object {
    `$f = `$_.Name
    & `$Adb -s `$Dispositivo push "`$_.FullName" "/sdcard/`$f"
    & `$Adb -s `$Dispositivo shell "run-as io.xenxo cp /sdcard/`$f databases/`$f; rm -f /sdcard/`$f"
    Write-Host "  [OK] `$f restaurada." -ForegroundColor Green
}

Get-ChildItem -Path "`$PSScriptRoot\shared_prefs" -Filter *.xml | ForEach-Object {
    `$f = `$_.Name
    & `$Adb -s `$Dispositivo push "`$_.FullName" "/sdcard/`$f"
    & `$Adb -s `$Dispositivo shell "run-as io.xenxo cp /sdcard/`$f shared_prefs/`$f; rm -f /sdcard/`$f"
    Write-Host "  [OK] `$f restaurada." -ForegroundColor Green
}

Write-Host "Restauracion completada." -ForegroundColor Cyan
"@

Set-Content -Path (Join-Path $BackupDir "restaurar.ps1") -Value $RestoreScript -Encoding ascii

Write-Host "`n=================================================" -ForegroundColor Green
Write-Host "  BACKUP COMPLETADO CON EXITO!                   " -ForegroundColor Green
Write-Host "  Ruta: $BackupDir" -ForegroundColor Green
Write-Host "=================================================" -ForegroundColor Green
