param(
    [string]$Serial,
    [switch]$Build,
    [switch]$NoInstall,
    [switch]$InstallOnly,
    [switch]$NoBrowser
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

$projectRoot = Split-Path -Parent $PSScriptRoot
$apkPath = Join-Path $projectRoot 'app\build\outputs\apk\debug\app-debug.apk'
$gradlePath = Join-Path (Split-Path -Parent $projectRoot) 'alpha-code-android\gradlew.bat'
$packageName = 'com.alphacode.wifiprobe'
$activityName = "$packageName/.MainActivity"
$hostPort = 18787
$devicePort = 8787
$portalUrl = "http://127.0.0.1:$hostPort/"
$portalWaitSeconds = 120
$script:adbRestartAttempted = $false

function Restart-AdbServer {
    $oldErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'SilentlyContinue'
    & adb 'kill-server' *> $null
    Start-Sleep -Seconds 1
    & adb 'start-server' *> $null
    $ErrorActionPreference = $oldErrorActionPreference
}

function Invoke-Adb {
    param(
        [Parameter(Mandatory = $true, Position = 0, ValueFromRemainingArguments = $true)]
        [string[]]$Arguments
    )

    $oldErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'Continue'
    $output = @(& adb @Arguments 2>&1 | ForEach-Object { $_.ToString() })
    $exitCode = $LASTEXITCODE
    $ErrorActionPreference = $oldErrorActionPreference
    if ($exitCode -ne 0) {
        $outputText = $output -join "`n"
        $daemonError = $outputText -match '(?i)(could not read ok from ADB Server|failed to (?:start|check) daemon|cannot connect to daemon)'
        if ($daemonError -and -not $script:adbRestartAttempted) {
            $script:adbRestartAttempted = $true
            Write-Warning 'ADB server is not responding; restarting it once...'
            Restart-AdbServer
            return Invoke-Adb -Arguments $Arguments
        }
        throw "ADB failed: adb $($Arguments -join ' ')`n$outputText"
    }
    return $output
}

if (-not (Get-Command adb.exe -ErrorAction SilentlyContinue)) {
    throw 'adb.exe was not found in PATH. Add Android SDK Platform-Tools to PATH.'
}

if ($Build) {
    if (-not (Test-Path -LiteralPath $gradlePath)) {
        throw "Gradle wrapper not found: $gradlePath"
    }
    Write-Host 'Building APK...' -ForegroundColor Cyan
    & $gradlePath '-p' $projectRoot 'assembleDebug' 'lintDebug'
    if ($LASTEXITCODE -ne 0) {
        throw 'APK build failed.'
    }
}

if (-not (Test-Path -LiteralPath $apkPath)) {
    throw "APK not found: $apkPath. Run again with -Build."
}

if ([string]::IsNullOrWhiteSpace($Serial)) {
    $deviceLines = @(Invoke-Adb -Arguments @('devices', '-l') | Select-Object -Skip 1)
    $alphaCandidates = @(foreach ($line in $deviceLines) {
        if ($line -match '^(?<serial>\S+)\s+device\s+.*model:(?<model>\S+)') {
            $deviceSerial = $matches.serial
            $normalizedModel = $matches.model -replace '\\_', '_'
            if ($normalizedModel -match '^Alpha[_-]?Mini$') {
                [pscustomobject]@{
                    Serial = $deviceSerial
                    Model = $normalizedModel
                }
            }
        }
    })

    if ($alphaCandidates.Count -eq 0) {
        throw 'Alpha Mini was not found in ADB. Check with: adb devices -l'
    }
    if ($alphaCandidates.Count -gt 1) {
        throw 'Multiple Alpha Mini devices found. Run again with -Serial <serial>.'
    }
    $Serial = $alphaCandidates[0].Serial
}

$state = (Invoke-Adb -Arguments @('-s', $Serial, 'get-state') | Select-Object -First 1).ToString().Trim()
if ($state -ne 'device') {
    throw "Device $Serial is not ready (state: $state)."
}

$model = (Invoke-Adb -Arguments @('-s', $Serial, 'shell', 'getprop', 'ro.product.model') | Select-Object -First 1).ToString().Trim()
$sdk = (Invoke-Adb -Arguments @('-s', $Serial, 'shell', 'getprop', 'ro.build.version.sdk') | Select-Object -First 1).ToString().Trim()
if ($model -notmatch 'Alpha Mini') {
    throw "Selected device is not Alpha Mini: model=$model, serial=$Serial"
}

Write-Host "Alpha Mini: $Serial | Android SDK $sdk" -ForegroundColor Green

if (-not $NoInstall) {
    Write-Host 'Installing/updating Wi-Fi Probe (original app is untouched)...' -ForegroundColor Cyan
    Invoke-Adb -Arguments @('-s', $Serial, 'install', '-r', $apkPath) | ForEach-Object { Write-Host $_ }
}

Write-Host 'Granting Wi-Fi runtime permissions for headless boot...' -ForegroundColor Cyan
$oldErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = 'SilentlyContinue'
& adb '-s' $Serial 'shell' 'pm' 'grant' $packageName 'android.permission.ACCESS_FINE_LOCATION' *> $null
& adb '-s' $Serial 'shell' 'pm' 'grant' $packageName 'android.permission.ACCESS_COARSE_LOCATION' *> $null
$ErrorActionPreference = $oldErrorActionPreference

if ($InstallOnly) {
    Write-Host 'Launching probe once to clear Android stopped state...' -ForegroundColor Cyan
    Invoke-Adb -Arguments @('-s', $Serial, 'shell', 'am', 'start', '-n', $activityName) | ForEach-Object { Write-Host $_ }
    Write-Host 'Auto Portal installed. It will start after the next Alpha Mini boot.' -ForegroundColor Green
    exit 0
}

Write-Host 'Starting Wi-Fi Probe on Alpha Mini...' -ForegroundColor Cyan
Invoke-Adb -Arguments @('-s', $Serial, 'shell', 'am', 'start', '-n', $activityName) | ForEach-Object { Write-Host $_ }

# Remove an old forward if present, then create a fresh one.
$oldErrorActionPreference = $ErrorActionPreference
$ErrorActionPreference = 'SilentlyContinue'
& adb '-s' $Serial 'forward' '--remove' "tcp:$hostPort" *> $null
$ErrorActionPreference = $oldErrorActionPreference
Invoke-Adb -Arguments @('-s', $Serial, 'forward', "tcp:$hostPort", "tcp:$devicePort") | ForEach-Object { Write-Host $_ }

Write-Host "Waiting for portal at $portalUrl ..." -ForegroundColor Cyan
$ready = $false
for ($attempt = 1; $attempt -le $portalWaitSeconds; $attempt++) {
    try {
        $health = Invoke-WebRequest -UseBasicParsing -Uri "$portalUrl`health" -TimeoutSec 2
        if ($health.StatusCode -eq 200) {
            $ready = $true
            break
        }
    } catch {
        Start-Sleep -Seconds 1
    }
}

if (-not $ready) {
    Write-Warning "Portal did not respond after $portalWaitSeconds seconds. The robot may still be scanning; try $portalUrl again in a few seconds."
} else {
    Write-Host 'Portal is ready.' -ForegroundColor Green
}

if (-not $NoBrowser) {
    Start-Process $portalUrl
}

Write-Host ''
Write-Host "Setup page: $portalUrl" -ForegroundColor Green
Write-Host "Remove forward when done: adb -s $Serial forward --remove tcp:$hostPort" -ForegroundColor DarkGray
