param(
    [switch]$Install
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Set-Location $root
Write-Host "=== lateralWheelie v1.0.14 | clean + lint + debug APK ===" -ForegroundColor Cyan

# AGP 8.7 officially uses Gradle 8.9 and JDK 17.
$studioJbr = Join-Path $env:ProgramFiles "Android\Android Studio\jbr"
if (-not $env:JAVA_HOME -and (Test-Path (Join-Path $studioJbr "bin\java.exe"))) {
    $env:JAVA_HOME = $studioJbr
    $env:Path = "$env:JAVA_HOME\bin;$env:Path"
}
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    throw "No se encontró Java. Instala Android Studio/JDK 17 o configura JAVA_HOME."
}
Write-Host "Java:" -ForegroundColor DarkCyan
$javaVersion = & $env:ComSpec /d /c "java -version 2>&1"
if ($LASTEXITCODE -ne 0) { throw "Java no pudo ejecutarse correctamente (código $LASTEXITCODE)." }
$javaVersion | Select-Object -First 3 | ForEach-Object { Write-Host "  $_" }

# Locate Android SDK and make it explicit for Gradle.
$sdkCandidates = @(
    $env:ANDROID_SDK_ROOT,
    $env:ANDROID_HOME,
    (Join-Path $env:LOCALAPPDATA "Android\Sdk")
) | Where-Object { $_ -and (Test-Path $_) } | Select-Object -Unique
if (-not $sdkCandidates) {
    throw "No se encontró Android SDK. Instala SDK Platform 35 desde Android Studio > SDK Manager."
}
$sdk = $sdkCandidates[0]
$env:ANDROID_SDK_ROOT = $sdk
$env:ANDROID_HOME = $sdk
("sdk.dir=" + ($sdk -replace '\\','\\\\')) | Set-Content -Encoding ASCII (Join-Path $root "local.properties")
Write-Host "Android SDK: $sdk" -ForegroundColor DarkCyan

$tools = Join-Path $root ".tools"
$gradleHome = Join-Path $tools "gradle-8.9"
$gradleCmd = Join-Path $gradleHome "bin\gradle.bat"
if (-not (Test-Path $gradleCmd)) {
    New-Item -ItemType Directory -Force -Path $tools | Out-Null
    $zip = Join-Path $tools "gradle-8.9-bin.zip"
    Write-Host "Descargando Gradle 8.9 (una sola vez)..." -ForegroundColor Yellow
    Invoke-WebRequest -UseBasicParsing "https://services.gradle.org/distributions/gradle-8.9-bin.zip" -OutFile $zip
    Expand-Archive -Path $zip -DestinationPath $tools -Force
    Remove-Item $zip -Force
}
Write-Host "Gradle: 8.9 (fijado por el proyecto)" -ForegroundColor DarkCyan

Write-Host "`n=== Validación Android Lint + compilación ===" -ForegroundColor Cyan
& $gradleCmd --no-daemon clean lintDebug assembleDebug
if ($LASTEXITCODE -ne 0) { throw "Gradle terminó con código $LASTEXITCODE" }

$apk = Join-Path $root "app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apk)) { throw "Gradle terminó pero no existe $apk" }
$out = Join-Path $root "lateralWheelie-v1.0.14-debug.apk"
Copy-Item $apk $out -Force
Write-Host "`nAPK generada: $out" -ForegroundColor Green

if ($Install) {
    $adbCmd = Get-Command adb -ErrorAction SilentlyContinue
    $adbPath = if ($adbCmd) { $adbCmd.Source } else { $null }
    if (-not $adbPath) {
        $candidate = Join-Path $sdk "platform-tools\adb.exe"
        if (Test-Path $candidate) { $adbPath = $candidate }
    }
    if (-not $adbPath) { throw "APK compilada, pero no se encontró adb para instalarla." }
    Write-Host "`n=== Dispositivos ADB ===" -ForegroundColor Cyan
    & $adbPath devices
    Write-Host "Instalando actualización..." -ForegroundColor Cyan
    & $adbPath install -r $out
    if ($LASTEXITCODE -ne 0) { throw "adb install terminó con código $LASTEXITCODE" }
    Write-Host "`n=== Smoke test de arranque ===" -ForegroundColor Cyan
    & $adbPath logcat -c
    & $adbPath shell am force-stop com.nemerpus.lateralwheelie
    & $adbPath shell am start -W -n com.nemerpus.lateralwheelie/.MainActivity | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "No se pudo lanzar MainActivity." }
    Start-Sleep -Seconds 4
    $appPid = (& $adbPath shell pidof com.nemerpus.lateralwheelie).Trim()
    if (-not $appPid) {
        $crash = & $adbPath logcat -d -v time | Select-String -Pattern "FATAL EXCEPTION|AndroidRuntime|com.nemerpus.lateralwheelie|Caused by:" -Context 3,12
        $crash | Out-Host
        throw "Smoke test fallido: lateralWheelie no sigue en ejecución tras 4 segundos."
    }
    Write-Host "Proceso activo PID $appPid" -ForegroundColor Green
    $fatal = & $adbPath logcat -d -v time | Select-String -Pattern "FATAL EXCEPTION|Process: com.nemerpus.lateralwheelie"
    if ($fatal) { $fatal | Out-Host; throw "Smoke test detectó una excepción fatal." }
    Write-Host "Smoke test de arranque: OK" -ForegroundColor Green
}
