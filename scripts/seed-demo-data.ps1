param(
    [ValidateRange(1, 30)][int]$Users = 12,
    [ValidateRange(1, 3)][int]$OrdersPerUser = 2,
    [ValidatePattern('^[a-z0-9-]{3,24}$')][string]$BatchId = ('demo-' + (Get-Date -Format 'yyyyMMddHHmmss')),
    [string]$BaseUrl = 'http://127.0.0.1:8080/api/v1',
    [ValidateRange(1024, 65000)][int]$ServicePortBase = 8080,
    [ValidateRange(1024, 65000)][int]$MongoHostPort = 27018,
    [string]$Password = $env:DEMO_SEED_PASSWORD
)

$ErrorActionPreference = 'Stop'
$base = [Uri]$BaseUrl
if ($base.Scheme -ne 'http' -or $base.Host -notin @('localhost', '127.0.0.1')) {
    throw 'Demo seed chi duoc goi Gateway HTTP tren may local.'
}
$BaseUrl = $BaseUrl.TrimEnd('/')

if ([string]::IsNullOrWhiteSpace($Password)) {
    $envFile = Join-Path (Split-Path $PSScriptRoot -Parent) '.env'
    if (Test-Path -LiteralPath $envFile) {
        foreach ($line in Get-Content -LiteralPath $envFile) {
            if ($line -match '^DEMO_SEED_PASSWORD=(.*)$') { $Password = $matches[1]; break }
        }
    }
}
if ([string]::IsNullOrWhiteSpace($Password) -or $Password.Length -lt 8 -or $Password.Length -gt 72) {
    throw 'Dat DEMO_SEED_PASSWORD (8-72 ky tu) trong .env hoac truyen -Password.'
}

function Invoke-Api {
    param([string]$Method, [string]$Path, [object]$Body, [hashtable]$Headers = @{})
    $request = @{
        Method = $Method
        Uri = "$BaseUrl$Path"
        Headers = $Headers
        TimeoutSec = 20
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

function Wait-Order {
    param([string]$Id, [hashtable]$Headers, [string]$ExpectedState)
    $deadline = (Get-Date).AddSeconds(90)
    do {
        $order = Invoke-Api GET "/orders/$Id" $null $Headers
        if ($order.inventoryState -eq $ExpectedState) { return $order }
        if ($order.inventoryState -in @('REJECTED', 'RELEASED') -and $order.inventoryState -ne $ExpectedState) {
            throw "Don $Id chuyen sang $($order.status)/$($order.inventoryState), mong doi $ExpectedState."
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "Het thoi gian cho don $Id chuyen sang $ExpectedState."
}

function Wait-CartEmpty {
    param([hashtable]$Headers)
    $deadline = (Get-Date).AddSeconds(60)
    do {
        $cart = Invoke-Api GET '/cart' $null $Headers
        if ($null -eq $cart.items -or @($cart.items).Count -eq 0) { return }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw 'Gio hang chua duoc xoa sau khi dat don.'
}

function Wait-Notification {
    param([string]$OrderId, [string]$Type, [hashtable]$Headers)
    $deadline = (Get-Date).AddSeconds(90)
    do {
        [object[]]$notifications = Invoke-Api GET '/notifications' $null $Headers
        if (@($notifications | Where-Object { $_.referenceId -eq $OrderId -and $_.type -eq $Type }).Count -gt 0) {
            return
        }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "Khong nhan duoc thong bao $Type cho don $OrderId."
}

foreach ($port in $ServicePortBase..($ServicePortBase + 6)) {
    $health = Invoke-RestMethod -Uri "http://127.0.0.1:$port/actuator/health" -TimeoutSec 5
    Assert-True ($health.status -eq 'UP') "Service cong $port chua san sang."
}
try {
    $connections = @(Get-NetTCPConnection -State Established -ErrorAction Stop)
    foreach ($offset in @(1, 2, 4, 5, 6)) {
        $servicePort = $ServicePortBase + $offset
        $owners = @(Get-NetTCPConnection -State Listen -LocalPort $servicePort -ErrorAction Stop |
            Select-Object -ExpandProperty OwningProcess -Unique)
        Assert-True ($owners.Count -eq 1) "Khong xac dinh duoc process o cong $servicePort."
        $mongoConnections = @($connections | Where-Object {
            $_.OwningProcess -eq $owners[0] -and $_.RemotePort -eq $MongoHostPort
        })
        Assert-True ($mongoConnections.Count -gt 0) "Service cong $servicePort khong ket noi MongoDB Docker cong $MongoHostPort. Dung service va nap .env truoc khi seed."
    }
} catch {
    throw "Khong xac minh duoc 5 ket noi MongoDB Docker: $_"
}
$catalog = Invoke-Api GET '/products?page=0&size=100' $null
$products = @($catalog.content | Where-Object {
    @($_.availableSizes).Count -gt 0 -and @($_.colorNames).Count -gt 0
})
Assert-True ($products.Count -ge 3) 'Can it nhat 3 san pham co size/mau de seed.'

Write-Output "SEED START batch=$BatchId users=$Users ordersPerUser=$OrdersPerUser"
$sessions = @()
$createdOrders = 0
$cancelledOrders = 0
$rejectedOrders = 0

for ($userIndex = 1; $userIndex -le $Users; $userIndex++) {
    $userCode = $userIndex.ToString('D2')
    $email = "demo.$BatchId.u$userCode@example.test"
    $phone = '090' + $userIndex.ToString('D7')
    try {
        $registered = Invoke-Api POST '/auth/register' @{
            fullName = "Demo Customer $userCode"
            email = $email
            password = $Password
            phone = $phone
        }
    } catch {
        if ([int]$_.Exception.Response.StatusCode -eq 409) {
            throw "BatchId $BatchId da ton tai. Hay chon BatchId moi de khong tao trung dia chi/don hang."
        }
        throw
    }
    Assert-True (-not [string]::IsNullOrWhiteSpace($registered.accessToken)) "Khong tao duoc $email."
    $auth = @{ Authorization = "Bearer $($registered.accessToken)" }

    $homeAddress = Invoke-Api POST '/addresses' @{
        recipientName = "Demo Customer $userCode"
        phoneNumber = $phone
        streetAddress = "$userIndex Demo Street"
        ward = 'Ward 1'
        district = 'District 1'
        city = 'Ho Chi Minh City'
        isDefault = $true
        label = 'Demo Home'
    } $auth
    Invoke-Api POST '/addresses' @{
        recipientName = "Demo Customer $userCode"
        phoneNumber = $phone
        streetAddress = "$userIndex Sample Avenue"
        ward = 'Ward 2'
        district = 'District 3'
        city = 'Ho Chi Minh City'
        isDefault = $false
        label = 'Demo Office'
    } $auth | Out-Null

    for ($wishlistIndex = 0; $wishlistIndex -lt 3; $wishlistIndex++) {
        $favorite = $products[($userIndex + $wishlistIndex) % $products.Count]
        Invoke-Api POST '/wishlist/items' @{ productId = $favorite.id } $auth | Out-Null
    }

    $orderIds = @()
    $userCancelled = 0
    for ($orderIndex = 1; $orderIndex -le $OrdersPerUser; $orderIndex++) {
        $product = $products[(($userIndex * 2) + $orderIndex) % $products.Count]
        $cart = Invoke-Api POST '/cart/items' @{
            productId = $product.id
            selectedSize = $product.availableSizes[0]
            selectedColor = $product.colorNames[0]
            quantity = 1
        } $auth
        $quote = Invoke-Api POST '/orders/quote' @{
            shippingType = $(if ($orderIndex % 2 -eq 0) { 'EXPRESS' } else { 'STANDARD' })
        } $auth
        $expectedItems = @($cart.items | ForEach-Object { @{ id = $_.id; quantity = [int]$_.quantity } })
        $body = @{
            shippingType = $(if ($orderIndex % 2 -eq 0) { 'EXPRESS' } else { 'STANDARD' })
            paymentType = 'CASH_ON_DELIVERY'
            recipientName = $homeAddress.recipientName
            phone = $homeAddress.phoneNumber
            addressLine = "$($homeAddress.streetAddress), $($homeAddress.ward), $($homeAddress.district), $($homeAddress.city)"
            expectedCartItems = $expectedItems
        }
        $key = "$BatchId-u$userCode-o$($orderIndex.ToString('D2'))"
        $order = Invoke-Api POST '/orders' $body @{
            Authorization = $auth.Authorization
            'Idempotency-Key' = $key
        }
        Assert-True ([decimal]$order.total -eq [decimal]$quote.total) "Tong tien don $($order.id) khong khop bao gia."
        $order = Wait-Order $order.id $auth 'RESERVED'
        Assert-True ($order.status -eq 'CONFIRMED') "Don $($order.id) khong CONFIRMED."
        Wait-CartEmpty $auth
        Wait-Notification $order.id 'ORDER_CREATED' $auth
        $orderIds += $order.id
        $createdOrders++

        if (($userIndex + $orderIndex) % 4 -eq 0) {
            Invoke-Api PATCH "/orders/$($order.id)/cancel" $null $auth | Out-Null
            $order = Wait-Order $order.id $auth 'RELEASED'
            Assert-True ($order.status -eq 'CANCELLED') "Don $($order.id) chua duoc huy."
            $userCancelled++
            $cancelledOrders++
        }
    }

    if ($userIndex -eq 1) {
        $product = $products[0]
        $availabilityPath = '/products/' + $product.id + '/availability?size=' +
            [uri]::EscapeDataString($product.availableSizes[0]) + '&color=' +
            [uri]::EscapeDataString($product.colorNames[0])
        $availability = Invoke-Api GET $availabilityPath $null
        $tooMany = [int]$availability.available + 1
        if ($tooMany -le 99) {
            $cart = Invoke-Api POST '/cart/items' @{
                productId = $product.id
                selectedSize = $product.availableSizes[0]
                selectedColor = $product.colorNames[0]
                quantity = $tooMany
            } $auth
            $rejected = Invoke-Api POST '/orders' @{
                shippingType = 'STANDARD'
                paymentType = 'CASH_ON_DELIVERY'
                recipientName = $homeAddress.recipientName
                phone = $homeAddress.phoneNumber
                addressLine = "$($homeAddress.streetAddress), $($homeAddress.city)"
                expectedCartItems = @(@{ id = $cart.items[0].id; quantity = $tooMany })
            } @{
                Authorization = $auth.Authorization
                'Idempotency-Key' = "$BatchId-u$userCode-rejected"
            }
            $rejected = Wait-Order $rejected.id $auth 'REJECTED'
            Assert-True ($rejected.status -eq 'CANCELLED') 'Don qua ton kho khong bi tu choi.'
            Invoke-Api DELETE '/cart' $null $auth | Out-Null
            $rejectedOrders++
        }
    }

    $productForCart = $products[($userIndex + 4) % $products.Count]
    Invoke-Api POST '/cart/items' @{
        productId = $productForCart.id
        selectedSize = $productForCart.availableSizes[0]
        selectedColor = $productForCart.colorNames[0]
        quantity = 1
    } $auth | Out-Null

    $sessions += [pscustomobject]@{
        Email = $email
        Headers = $auth
        OrderIds = $orderIds
        ExpectedCreated = $orderIds.Count
        ExpectedCancelled = $userCancelled
    }
    Write-Output "SEED user=$userCode orders=$($orderIds.Count) cancelled=$userCancelled addresses=2 wishlist=3 cart=1"
}

foreach ($session in $sessions) {
    $deadline = (Get-Date).AddSeconds(90)
    do {
        [object[]]$notifications = Invoke-Api GET '/notifications' $null $session.Headers
        $created = @($notifications | Where-Object { $_.type -eq 'ORDER_CREATED' }).Count
        $cancelled = @($notifications | Where-Object { $_.type -eq 'ORDER_CANCELLED' }).Count
        if ($created -ge $session.ExpectedCreated -and $cancelled -ge $session.ExpectedCancelled) { break }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    Assert-True ($created -ge $session.ExpectedCreated -and $cancelled -ge $session.ExpectedCancelled) "Thong bao Kafka chua du cho $($session.Email)."
    [object[]]$orders = Invoke-Api GET '/orders' $null $session.Headers
    Assert-True ($orders.Count -ge $session.OrderIds.Count) "Lich su don chua du cho $($session.Email)."
    Write-Output "VERIFY $($session.Email): orders=$($orders.Count) created=$created cancelled=$cancelled"
}

Write-Output "SEED COMPLETE batch=$BatchId users=$Users addresses=$($Users * 2) wishlists=$Users confirmedOrCancelledOrders=$createdOrders cancelledOrders=$cancelledOrders rejectedOrders=$rejectedOrders carts=$Users"
