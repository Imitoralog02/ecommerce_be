param([ValidateRange(1024, 64993)][int]$PortBase = 8280)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$runId = (Get-Date -Format 'yyyyMMddHHmmss') + [guid]::NewGuid().ToString('N').Substring(0, 6)
$prefix = "ecommerce_it_$runId"
$logDirectory = Join-Path $root ".local-runtime\integration-$runId"
$serviceNames = @('gateway', 'auth', 'catalog', 'cart', 'order', 'notification', 'customer')
$started = New-Object System.Collections.Generic.List[object]
$runStartedAt = Get-Date

Push-Location $root
try {
    foreach ($service in @('mongodb', 'redis', 'kafka', 'mailpit')) {
        $health = docker compose ps --format json $service | ConvertFrom-Json
        if ($LASTEXITCODE -ne 0 -or $health.Health -ne 'healthy') {
            throw "Docker service $service chua healthy."
        }
    }
    foreach ($port in $PortBase..($PortBase + 6)) {
        if (Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue) {
            throw "Cong $port da duoc process khac su dung."
        }
    }
    foreach ($service in $serviceNames) {
        $jar = Join-Path $root "$service-service\build\libs\$service-service-0.1.0-SNAPSHOT.jar"
        if (-not (Test-Path -LiteralPath $jar)) { throw "Chua build $jar. Chay .\gradlew.bat bootJar." }
    }
    New-Item -ItemType Directory -Path $logDirectory -Force | Out-Null

    $env:JWT_SECRET = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
    $env:INTERNAL_API_KEY = [guid]::NewGuid().ToString('N') + [guid]::NewGuid().ToString('N')
    $env:AUTH_MONGODB_URI = "mongodb://127.0.0.1:27018/${prefix}_auth"
    $env:CATALOG_MONGODB_URI = "mongodb://127.0.0.1:27018/${prefix}_catalog"
    $env:ORDER_MONGODB_URI = "mongodb://127.0.0.1:27018/${prefix}_order"
    $env:NOTIFICATION_MONGODB_URI = "mongodb://127.0.0.1:27018/${prefix}_notification"
    $env:CUSTOMER_MONGODB_URI = "mongodb://127.0.0.1:27018/${prefix}_customer"
    $env:REDIS_HOST = '127.0.0.1'
    $env:REDIS_PORT = '6379'
    $env:KAFKA_BOOTSTRAP_SERVERS = '127.0.0.1:9092'
    $env:ORDER_TOPIC = "order.lifecycle.it.$runId"
    $env:ORDER_INVENTORY_POLL_MS = '1000'
    $env:ORDER_OUTBOX_POLL_MS = '1000'
    $env:INVENTORY_DEMO_SEED_QUANTITY = '10'
    $env:CATALOG_SYNC_LEGACY_DEMO = 'false'
    $env:AUTH_URL = "http://127.0.0.1:$($PortBase + 1)"
    $env:CATALOG_URL = "http://127.0.0.1:$($PortBase + 2)"
    $env:CART_URL = "http://127.0.0.1:$($PortBase + 3)"
    $env:ORDER_URL = "http://127.0.0.1:$($PortBase + 4)"
    $env:NOTIFICATION_URL = "http://127.0.0.1:$($PortBase + 5)"
    $env:CUSTOMER_URL = "http://127.0.0.1:$($PortBase + 6)"

    foreach ($offset in @(1, 2, 3, 4, 5, 6, 0)) {
        $service = $serviceNames[$offset]
        $env:PORT = [string]($PortBase + $offset)
        $jar = Join-Path $root "$service-service\build\libs\$service-service-0.1.0-SNAPSHOT.jar"
        $stdout = Join-Path $logDirectory "$service.out.log"
        $stderr = Join-Path $logDirectory "$service.err.log"
        $process = Start-Process -FilePath 'java' -ArgumentList @('-jar', $jar) -WorkingDirectory $root `
            -RedirectStandardOutput $stdout -RedirectStandardError $stderr -WindowStyle Hidden -PassThru
        $started.Add($process)
    }

    $deadline = (Get-Date).AddSeconds(180)
    do {
        $up = 0
        foreach ($port in $PortBase..($PortBase + 6)) {
            try {
                $health = Invoke-RestMethod -Uri "http://127.0.0.1:$port/actuator/health" -TimeoutSec 2
                if ($health.status -eq 'UP') { $up++ }
            } catch { }
        }
        if ($up -eq 7) { break }
        foreach ($process in $started) {
            $process.Refresh()
            if ($process.HasExited) { throw "Service kiem thu PID $($process.Id) da thoat. Xem log tai $logDirectory." }
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    if ($up -ne 7) { throw "Chi co $up/7 service UP. Xem log tai $logDirectory." }
    Write-Output "INTEGRATION START run=$runId ports=$PortBase..$($PortBase + 6)"
    & (Join-Path $PSScriptRoot 'smoke-flash-sale-quota.ps1') `
        -BaseUrl "http://127.0.0.1:$PortBase/api/v1" `
        -CatalogPort ($PortBase + 2) -CatalogDatabase "${prefix}_catalog" -InternalKey $env:INTERNAL_API_KEY
    & (Join-Path $PSScriptRoot 'smoke-api.ps1') -BaseUrl "http://127.0.0.1:$PortBase/api/v1" -CheckDemoPromotion -CheckDemoFlashSale
    & (Join-Path $PSScriptRoot 'smoke-password-reset.ps1') -BaseUrl "http://127.0.0.1:$PortBase/api/v1"
    & (Join-Path $PSScriptRoot 'smoke-delivery.ps1') `
        -BaseUrl "http://127.0.0.1:$PortBase/api/v1" `
        -AuthDatabase "${prefix}_auth" -CatalogDatabase "${prefix}_catalog" -CatalogPort ($PortBase + 2)
    Write-Output "INTEGRATION PASSED databases=${prefix}_{auth,catalog,order,notification,customer} logs=$logDirectory"
} finally {
    foreach ($process in $started) {
        $process.Refresh()
        if (-not $process.HasExited) {
            Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue
        }
    }
    if ($started.Count -gt 0) {
        foreach ($offset in 0..6) {
            $port = $PortBase + $offset
            $service = $serviceNames[$offset]
            $jar = Join-Path $root "$service-service\build\libs\$service-service-0.1.0-SNAPSHOT.jar"
            $owners = @(Get-NetTCPConnection -State Listen -LocalPort $port -ErrorAction SilentlyContinue |
                Select-Object -ExpandProperty OwningProcess -Unique)
            foreach ($ownerPid in $owners) {
                $processInfo = Get-CimInstance Win32_Process -Filter "ProcessId = $ownerPid"
                if ($null -ne $processInfo -and $processInfo.CommandLine -like "*$jar*" -and
                    $processInfo.CreationDate -ge $runStartedAt) {
                    Stop-Process -Id $ownerPid -Force -ErrorAction Stop
                }
            }
        }
    }
    Pop-Location
}
