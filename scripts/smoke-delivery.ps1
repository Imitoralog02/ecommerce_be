param(
    [string]$BaseUrl = 'http://127.0.0.1:8280/api/v1',
    [ValidatePattern('^ecommerce_it_[a-z0-9_]+_auth$')][string]$AuthDatabase,
    [ValidatePattern('^ecommerce_it_[a-z0-9_]+_catalog$')][string]$CatalogDatabase,
    [ValidateRange(1024, 65000)][int]$CatalogPort = 8282
)

$ErrorActionPreference = 'Stop'
$base = [Uri]$BaseUrl
if ($base.Scheme -ne 'http' -or $base.Host -notin @('localhost', '127.0.0.1')) {
    throw 'Delivery smoke test chi chay voi Gateway HTTP tren may local.'
}
if ([string]::IsNullOrWhiteSpace($env:INTERNAL_API_KEY)) {
    throw 'Can INTERNAL_API_KEY cua cac service kiem thu.'
}
$BaseUrl = $BaseUrl.TrimEnd('/')

function Invoke-Api {
    param([string]$Method, [string]$Path, [object]$Body, [hashtable]$Headers = @{})
    $request = @{
        Method = $Method
        Uri = "$BaseUrl$Path"
        Headers = $Headers
        TimeoutSec = 25
        ErrorAction = 'Stop'
    }
    if ($null -ne $Body) {
        $request.ContentType = 'application/json'
        $request.Body = ConvertTo-Json -InputObject $Body -Depth 10 -Compress
    }
    Invoke-RestMethod @request
}

function Assert-True {
    param([bool]$Condition, [string]$Message)
    if (-not $Condition) { throw $Message }
}

function Wait-InventoryState {
    param([string]$OrderId, [string]$Expected, [hashtable]$Headers)
    $deadline = (Get-Date).AddSeconds(60)
    do {
        $current = Invoke-Api GET "/orders/$OrderId" $null $Headers
        if ($current.inventoryState -eq $Expected) { return $current }
        if ($current.inventoryState -in @('REJECTED', 'RELEASED')) {
            throw "Don $OrderId da chuyen sang $($current.inventoryState), mong doi $Expected."
        }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)
    throw "Het thoi gian cho don $OrderId chuyen sang $Expected."
}

function Reservation-Count {
    param([string]$OrderId)
    $script = "db.getSiblingDB('" + $CatalogDatabase + "').inventory_variants.countDocuments({'reservations." + $OrderId + "':{" + '$exists' + ":true}})"
    $result = docker compose exec -T mongodb mongosh --quiet --eval $script
    if ($LASTEXITCODE -ne 0) { throw 'Khong doc duoc reservation trong MongoDB.' }
    return [int]($result | Select-Object -Last 1)
}

function Sale-Reservation-Count {
    param([string]$OrderId)
    $script = "db.getSiblingDB('" + $CatalogDatabase + "').flash_sale_allocations.countDocuments({'reservations." + $OrderId + "':{" + '$exists' + ":true}})"
    $result = docker compose exec -T mongodb mongosh --quiet --eval $script
    if ($LASTEXITCODE -ne 0) { throw 'Khong doc duoc Flash Sale reservation trong MongoDB.' }
    return [int]($result | Select-Object -Last 1)
}

$root = Split-Path $PSScriptRoot -Parent
Push-Location $root
try {
    $suffix = [guid]::NewGuid().ToString('N').Substring(0, 12)
    $password = 'Integration123!'
    $customerEmail = "it.customer.$suffix@example.test"
    $adminEmail = "it.operator.$suffix@example.test"
    $customer = Invoke-Api POST '/auth/register' @{
        email = $customerEmail; password = $password; fullName = 'Integration Customer'
    }
    $customerHeaders = @{ Authorization = "Bearer $($customer.accessToken)" }
    Invoke-Api POST '/auth/register' @{
        email = $adminEmail; password = $password; fullName = 'Integration Operator'
    } | Out-Null
    $promote = "db.getSiblingDB('" + $AuthDatabase + "').users.updateOne({email:'" + $adminEmail + "'},{" + '$set' + ":{role:'ADMIN'}}).modifiedCount"
    $modified = docker compose exec -T mongodb mongosh --quiet --eval $promote
    if ($LASTEXITCODE -ne 0) { throw 'Khong cap duoc quyen kiem thu trong database rieng.' }
    Assert-True ([int]($modified | Select-Object -Last 1) -eq 1) 'Tai khoan kiem thu khong duoc cap quyen.'
    $operator = Invoke-Api POST '/auth/login' @{ email = $adminEmail; password = $password }
    Assert-True ($operator.user.role -eq 'ADMIN') 'JWT kiem thu khong co role ADMIN.'
    $operatorHeaders = @{ Authorization = "Bearer $($operator.accessToken)" }

    $catalog = Invoke-Api GET '/products?page=0&size=20' $null
    $product = $null
    $candidates = @($catalog.content | Where-Object { $_.flashSaleId }) +
        @($catalog.content | Where-Object { -not $_.flashSaleId })
    foreach ($candidate in $candidates) {
        if (@($candidate.availableSizes).Count -eq 0 -or @($candidate.colorNames).Count -eq 0) { continue }
        $size = $candidate.availableSizes[0]
        $color = $candidate.colorNames[0]
        $path = '/products/' + $candidate.id + '/availability?size=' +
            [uri]::EscapeDataString($size) + '&color=' + [uri]::EscapeDataString($color)
        $stock = Invoke-Api GET $path $null
        if ([int]$stock.available -ge 2) { $product = $candidate; break }
    }
    Assert-True ($null -ne $product) 'Khong co bien the con du hang de kiem thu.'
    $startingAvailable = [int]$stock.available
    $startingSaleRemaining = $product.flashSaleRemaining
    $cart = Invoke-Api POST '/cart/items' @{
        productId = $product.id; selectedSize = $size; selectedColor = $color; quantity = 1
    } $customerHeaders
    $key = [guid]::NewGuid().ToString('N')
    $orderHeaders = @{ Authorization = $customerHeaders.Authorization; 'Idempotency-Key' = $key }
    $order = Invoke-Api POST '/orders' @{
        shippingType = 'STANDARD'
        paymentType = 'CASH_ON_DELIVERY'
        recipientName = 'Integration Customer'
        phone = '0901234567'
        addressLine = '1 Integration Street, Ho Chi Minh City'
        expectedCartItems = @(@{ id = $cart.items[0].id; quantity = 1 })
    } $orderHeaders
    $order = Wait-InventoryState $order.id 'RESERVED' $customerHeaders
    Assert-True ($order.status -eq 'CONFIRMED') 'Don COD khong duoc xac nhan.'
    Assert-True ((Reservation-Count $order.id) -eq 1) 'Catalog chua giu dung mot bien the.'
    if ($product.flashSaleId) {
        Assert-True ((Sale-Reservation-Count $order.id) -eq 1) 'Catalog chua giu luot Flash Sale.'
        $saleAfterReserve = Invoke-Api GET "/products/$($product.id)" $null
        Assert-True ([int]$saleAfterReserve.flashSaleRemaining -eq ([int]$startingSaleRemaining - 1)) 'Han muc Flash Sale khong giam khi giu don.'
    }
    Write-Output 'PASS delivery setup: COD confirmed and stock reserved'

    $processing = Invoke-Api PATCH "/orders/$($order.id)/fulfillment" @{ status = 'PROCESSING' } $operatorHeaders
    Assert-True ($processing.status -eq 'PROCESSING') 'Don khong chuyen sang PROCESSING.'
    $shipped = Invoke-Api PATCH "/orders/$($order.id)/fulfillment" @{
        status = 'SHIPPED'; trackingNumber = "IT-$suffix"
    } $operatorHeaders
    Assert-True ($shipped.status -eq 'SHIPPED') 'Don khong chuyen sang SHIPPED.'
    $delivered = Invoke-Api PATCH "/orders/$($order.id)/fulfillment" @{ status = 'DELIVERED' } $operatorHeaders
    Assert-True ($delivered.status -eq 'DELIVERED') 'Don khong chuyen sang DELIVERED.'
    $finalized = Wait-InventoryState $order.id 'FINALIZED' $customerHeaders
    Assert-True ($finalized.status -eq 'DELIVERED') 'Chot ton kho da doi sai trang thai don.'
    Assert-True ((Reservation-Count $order.id) -eq 0) 'Reservation van con sau khi giao hang.'
    if ($product.flashSaleId) {
        Assert-True ((Sale-Reservation-Count $order.id) -eq 0) 'Flash Sale reservation van con sau khi giao hang.'
        $saleAfterDelivery = Invoke-Api GET "/products/$($product.id)" $null
        Assert-True ([int]$saleAfterDelivery.flashSaleRemaining -eq ([int]$startingSaleRemaining - 1)) 'Giao hang da hoan nham luot Flash Sale.'
    }
    $after = Invoke-Api GET $path $null
    Assert-True ([int]$after.available -eq ($startingAvailable - 1)) 'Chot reservation da cong lai hoac tru them ton kho.'
    Write-Output 'PASS delivery: reservation finalized without restoring stock'

    $lines = @($finalized.items | ForEach-Object {
        @{ productId = $_.productId; size = $_.selectedSize; color = $_.selectedColor;
            quantity = [int]$_.quantity; flashSaleId = $_.flashSaleId; unitPrice = $_.unitPrice }
    })
    Invoke-RestMethod -Method POST -Uri "http://127.0.0.1:$CatalogPort/internal/inventory/reservations/$($order.id)/finalize" `
        -Headers @{ 'X-Internal-Api-Key' = $env:INTERNAL_API_KEY } -ContentType 'application/json' `
        -Body (ConvertTo-Json -InputObject @{ lines = $lines } -Depth 10 -Compress) -TimeoutSec 20 | Out-Null
    $afterRetry = Invoke-Api GET $path $null
    Assert-True ([int]$afterRetry.available -eq ($startingAvailable - 1)) 'Goi lai finalize da doi ton kho.'
    if ($product.flashSaleId) {
        $saleAfterRetry = Invoke-Api GET "/products/$($product.id)" $null
        Assert-True ([int]$saleAfterRetry.flashSaleRemaining -eq ([int]$startingSaleRemaining - 1)) 'Goi lai finalize da doi han muc Flash Sale.'
    }
    Write-Output 'PASS delivery retry: finalize is idempotent'

    $deadline = (Get-Date).AddSeconds(60)
    do {
        [object[]]$notifications = Invoke-Api GET '/notifications' $null $customerHeaders
        $deliveredNotice = @($notifications | Where-Object {
            $_.referenceId -eq $order.id -and $_.type -eq 'ORDER_DELIVERED'
        })
        if ($deliveredNotice.Count -gt 0) { break }
        Start-Sleep -Seconds 1
    } while ((Get-Date) -lt $deadline)
    Assert-True ($deliveredNotice.Count -gt 0) 'Kafka chua tao thong bao giao hang.'
    Write-Output 'PASS delivery notification: Kafka event persisted'
    Write-Output "SMOKE DELIVERY PASSED order=$($order.id) catalogDb=$CatalogDatabase"
} finally {
    Pop-Location
}
