param(
    [int]$Iterations = 6,
    [string]$Serial = '010058YUD18082406465'
)

$ErrorActionPreference = 'Stop'
Set-StrictMode -Version Latest

function Invoke-Adb {
    param([string[]]$Arguments)
    $oldErrorActionPreference = $ErrorActionPreference
    $ErrorActionPreference = 'SilentlyContinue'
    $output = @(& adb @Arguments 2>&1 | ForEach-Object { $_.ToString() })
    $exitCode = $LASTEXITCODE
    $ErrorActionPreference = $oldErrorActionPreference
    if ($exitCode -ne 0) {
        throw "ADB failed: adb $($Arguments -join ' ')`n$($output -join "`n")"
    }
    return $output
}

function Wait-DeviceOnline {
    for ($attempt = 1; $attempt -le 90; $attempt++) {
        $oldErrorActionPreference = $ErrorActionPreference
        $ErrorActionPreference = 'SilentlyContinue'
        $lines = @(& adb 'devices' '-l' 2>&1 | ForEach-Object { $_.ToString() })
        $ErrorActionPreference = $oldErrorActionPreference
        if ($lines -match "^$Serial\s+device\s") {
            return ($attempt * 2)
        }
        Start-Sleep -Seconds 2
    }
    throw "Device did not return online within 180 seconds: $Serial"
}

function Get-PortalStatus {
    for ($attempt = 1; $attempt -le 120; $attempt++) {
        try {
            $health = Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:18787/health' -TimeoutSec 2
            if ($health.StatusCode -eq 200) {
                $body = (Invoke-WebRequest -UseBasicParsing -Uri 'http://127.0.0.1:18787/api/status' -TimeoutSec 3).Content
                return ($body | ConvertFrom-Json)
            }
        } catch {
            Start-Sleep -Seconds 1
        }
    }
    throw 'Portal did not become ready within 120 seconds.'
}

function Get-Sha256([string]$Value) {
    $bytes = [Text.Encoding]::UTF8.GetBytes($Value)
    $hash = [Security.Cryptography.SHA256]::Create().ComputeHash($bytes)
    return ([BitConverter]::ToString($hash)).Replace('-', '')
}

if (-not (Get-Command adb.exe -ErrorAction SilentlyContinue)) {
    throw 'adb.exe was not found in PATH.'
}

$records = @()
for ($round = 1; $round -le $Iterations; $round++) {
    Write-Host "[$round/$Iterations] Rebooting Alpha Mini..." -ForegroundColor Cyan
    Invoke-Adb @('-s', $Serial, 'reboot') | Out-Null
    $onlineSeconds = Wait-DeviceOnline
    Invoke-Adb @('-s', $Serial, 'forward', 'tcp:18787', 'tcp:8787') | Out-Null
    $status = Get-PortalStatus
    $record = [pscustomobject]@{
        Round = $round
        OnlineAfterSeconds = $onlineSeconds
        Action = $status.action
        GroupSsid = $status.groupSsid
        GroupIp = $status.groupOwnerIp
        PassphraseLength = $status.groupPassphrase.Length
        PassphraseSha256 = Get-Sha256 $status.groupPassphrase
    }
    $records += $record
    Write-Host "  Portal ready; SSID=$($record.GroupSsid), IP=$($record.GroupIp), passphrase length=$($record.PassphraseLength)" -ForegroundColor Green
}

$records | Format-Table -AutoSize
$ssidStable = (@($records.GroupSsid | Select-Object -Unique).Count -eq 1)
$passphraseStable = (@($records.PassphraseSha256 | Select-Object -Unique).Count -eq 1)
Write-Host "SSID_STABLE=$ssidStable" -ForegroundColor $(if ($ssidStable) { 'Green' } else { 'Yellow' })
Write-Host "PASSPHRASE_STABLE=$passphraseStable" -ForegroundColor $(if ($passphraseStable) { 'Green' } else { 'Yellow' })

if (-not $ssidStable -or -not $passphraseStable) {
    exit 2
}
