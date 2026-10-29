param(
    [string]$BaseUrl = 'http://localhost:8080/api/v1'
)

$ErrorActionPreference = 'Stop'
$base = [Uri]$BaseUrl
if ($base.Host -notin @('localhost', '127.0.0.1')) {
    throw 'Smoke test chỉ chạy với Gateway trên máy local.'
}
$BaseUrl = $BaseUrl.TrimEnd('/')

function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body,
        [hashtable]$Headers = @{}
    )
    $request = @{
        Method = $Method
        Uri = "$BaseUrl$Path"
        Headers = $Headers
        TimeoutSec = 15
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

$email = 'smoke+' + [guid]::NewGuid().ToString('N') + '@example.test'
$password = 'SmokeTest123!'
$registered = Invoke-Api POST '/auth/register' @{
    fullName = 'API Smoke Test'
    email = $email
    password = $password
}
Assert-True ($registered.user.email -eq $email) 'Đăng ký không trả đúng tài khoản.'
$session = Invoke-Api POST '/auth/login' @{ email = $email; password = $password }
Assert-True (-not [string]::IsNullOrWhiteSpace($session.accessToken)) 'Đăng nhập không trả access token.'
$refreshed = Invoke-Api POST '/auth/refresh' @{ refreshToken = $session.refreshToken }
Assert-True ($refreshed.refreshToken -ne $session.refreshToken) 'Refresh token không được xoay vòng.'
$auth = @{ Authorization = "Bearer $($refreshed.accessToken)" }
$profile = Invoke-Api GET '/me' $null $auth
Assert-True ($profile.email -eq $email) 'GET /me không trả đúng tài khoản.'
$updatedProfile = Invoke-Api PUT '/me' @{ fullName = 'API Smoke Updated'; phone = '0901234567' } $auth
Assert-True ($updatedProfile.fullName -eq 'API Smoke Updated') 'PUT /me không lưu tên mới.'
Write-Output 'PASS auth: register, login, refresh, profile'

$catalog = Invoke-Api GET '/products?page=0&size=20' $null
Assert-True (@($catalog.content).Count -gt 0) 'Catalog không có sản phẩm.'
$product = $null
$size = $null
$color = $null
foreach ($candidate in $catalog.content) {
    if (@($candidate.availableSizes).Count -eq 0 -or @($candidate.colorNames).Count -eq 0) { continue }
    $candidateSize = $candidate.availableSizes[0]
    $candidateColor = $candidate.colorNames[0]
    $availabilityPath = '/products/' + $candidate.id + '/availability?size=' +
        [uri]::EscapeDataString($candidateSize) + '&color=' + [uri]::EscapeDataString($candidateColor)
    $availability = Invoke-Api GET $availabilityPath $null
    if ($availability.available -gt 0) {
        $product = $candidate
        $size = $candidateSize
        $color = $candidateColor
        break
    }
}
Assert-True ($null -ne $product) 'Không có biến thể còn hàng để kiểm tra COD.'
$startingAvailability = [int]$availability.available
Write-Output 'PASS catalog: products, availability'

$wishlist = Invoke-Api POST '/wishlist/items' @{ productId = $product.id } $auth
Assert-True (@($wishlist.items | Where-Object productId -eq $product.id).Count -eq 1) 'Thêm wishlist thất bại.'
$wishlist = Invoke-Api GET '/wishlist' $null $auth
Assert-True (@($wishlist.items | Where-Object productId -eq $product.id).Count -eq 1) 'Wishlist không được lưu.'
$wishlist = Invoke-Api DELETE ('/wishlist/items/' + $product.id) $null $auth
Assert-True (@($wishlist.items | Where-Object productId -eq $product.id).Count -eq 0) 'Xóa wishlist thất bại.'
Write-Output 'PASS wishlist: add, list, remove'

$address = Invoke-Api POST '/addresses' @{
    recipientName = 'API Smoke Test'
    phoneNumber = '0901234567'
    streetAddress = '1 Duong Thu Nghiem'
    ward = 'Phuong 1'
    district = 'Quan 1'
    city = 'Ho Chi Minh'
    isDefault = $true
    label = 'Nha rieng'
} $auth
$addresses = @(Invoke-Api GET '/addresses' $null $auth)
Assert-True (@($addresses | Where-Object { $_.id -eq $address.id -and $_.isDefault }).Count -eq 1) 'Địa chỉ mặc định không được lưu.'
Write-Output 'PASS addresses: create, list, default'

$cart = Invoke-Api POST '/cart/items' @{
    productId = $product.id
    selectedSize = $size
    selectedColor = $color
    quantity = 1
} $auth
Assert-True (@($cart.items).Count -eq 1) 'Thêm giỏ hàng thất bại.'
$cart = Invoke-Api GET '/cart' $null $auth
Assert-True ($cart.items[0].product.id -eq $product.id) 'Giỏ hàng không được lưu.'
$quote = Invoke-Api POST '/orders/quote' @{ shippingType = 'STANDARD' } $auth
Assert-True ([decimal]$quote.total -eq [decimal]$cart.subtotal) 'Báo giá COD không khớp tổng giỏ hàng.'
Write-Output 'PASS cart and quote: add, list, server price'

$key = [guid]::NewGuid().ToString()
$orderHeaders = @{ Authorization = $auth.Authorization; 'Idempotency-Key' = $key }
$orderBody = @{
    shippingType = 'STANDARD'
    paymentType = 'CASH_ON_DELIVERY'
    recipientName = $address.recipientName
    phone = $address.phoneNumber
    addressLine = '1 Duong Thu Nghiem, Phuong 1, Quan 1, Ho Chi Minh'
    expectedCartItems = @(@{ id = $cart.items[0].id; quantity = 1 })
}
$mismatchBody = $orderBody.Clone()
$mismatchBody.expectedCartItems = @(@{ id = $cart.items[0].id; quantity = 2 })
try {
    Invoke-Api POST '/orders' $mismatchBody @{
        Authorization = $auth.Authorization
        'Idempotency-Key' = [guid]::NewGuid().ToString()
    } | Out-Null
    throw 'Đặt COD với giỏ hàng đã thay đổi không bị từ chối.'
} catch {
    if ([int]$_.Exception.Response.StatusCode -ne 409) { throw }
}
Write-Output 'PASS checkout guard: changed cart rejected'
$abandonedKey = [guid]::NewGuid().ToString()
$abandoned = Invoke-Api POST ('/orders/by-request/' + $abandonedKey + '/abandon') $null $auth
Assert-True ($abandoned.outcome -eq 'ABANDONED') 'Không kết thúc được yêu cầu COD chưa có đơn.'
try {
    Invoke-Api POST '/orders' $orderBody @{
        Authorization = $auth.Authorization
        'Idempotency-Key' = $abandonedKey
    } | Out-Null
    throw 'Yêu cầu COD đã kết thúc vẫn tạo được đơn.'
} catch {
    if ([int]$_.Exception.Response.StatusCode -ne 409) { throw }
}
Write-Output 'PASS checkout recovery: abandoned key blocks late POST'
$order = Invoke-Api POST '/orders' $orderBody $orderHeaders
Assert-True (-not [string]::IsNullOrWhiteSpace($order.id)) 'Tạo đơn không trả ID.'
try {
$replayed = Invoke-Api POST '/orders' $orderBody $orderHeaders
Assert-True ($replayed.id -eq $order.id) 'Idempotency-Key tạo đơn trùng.'
$recovered = Invoke-Api GET ('/orders/by-request/' + $key) $null $auth
Assert-True ($recovered.id -eq $order.id) 'Tra cứu Idempotency-Key không trả đúng đơn.'
$resolved = Invoke-Api POST ('/orders/by-request/' + $key + '/abandon') $null $auth
Assert-True ($resolved.outcome -eq 'ORDER_FOUND' -and $resolved.order.id -eq $order.id) 'Kết thúc yêu cầu COD đã có đơn không trả lại đơn.'

$deadline = (Get-Date).AddSeconds(45)
do {
    $order = Invoke-Api GET ('/orders/' + $order.id) $null $auth
    if ($order.status -ne 'PENDING') { break }
    Start-Sleep -Seconds 2
} while ((Get-Date) -lt $deadline)
Assert-True ($order.status -eq 'CONFIRMED') "Đơn không được xác nhận: $($order.status)/$($order.inventoryState)."
$orders = @(Invoke-Api GET '/orders' $null $auth)
$orderIds = @($orders | ForEach-Object { $_.id })
Assert-True ($orderIds -contains $order.id) "Đơn $($order.id) không có trong lịch sử: $($orderIds -join ',')."
Assert-True (@($orders).Count -eq 1) 'Yêu cầu COD đã kết thúc xuất hiện trong lịch sử.'
Write-Output 'PASS orders: COD, idempotency, inventory confirmation, history'

$deadline = (Get-Date).AddSeconds(30)
do {
    $cart = Invoke-Api GET '/cart' $null $auth
    if (@($cart.items).Count -eq 0) { break }
    Start-Sleep -Seconds 2
} while ((Get-Date) -lt $deadline)
Assert-True (@($cart.items).Count -eq 0) 'Các sản phẩm đã đặt vẫn nằm trong giỏ sau xác nhận.'
Write-Output 'PASS cart reconciliation: purchased items consumed'

$deadline = (Get-Date).AddSeconds(45)
do {
    $notifications = @(Invoke-Api GET '/notifications' $null $auth)
    $notification = $notifications | Where-Object { $_.referenceId -eq $order.id } | Select-Object -First 1
    if ($null -ne $notification) { break }
    Start-Sleep -Seconds 2
} while ((Get-Date) -lt $deadline)
Assert-True ($null -ne $notification) "Không nhận được thông báo cho $($order.id) qua Kafka. Các đơn tham chiếu: $(@($notifications | ForEach-Object { $_.referenceId }) -join ',')."
$marked = Invoke-Api PATCH ('/notifications/' + $notification.id + '/read') $null $auth
Assert-True ($marked.read -eq $true) 'Đánh dấu thông báo đã đọc thất bại.'
Write-Output 'PASS notifications: event, list, mark read'

} finally {
    try {
        $cancelled = Invoke-Api PATCH ('/orders/' + $order.id + '/cancel') $null $auth
    } catch {
        Write-Warning "Không thể hủy đơn smoke test $($order.id): $_"
    }
}
Assert-True ($cancelled.status -eq 'CANCELLED') 'Hủy đơn thất bại.'
Write-Output 'PASS cancellation: confirmed order cancelled'

$deadline = (Get-Date).AddSeconds(45)
do {
    $afterCancel = Invoke-Api GET ('/orders/' + $order.id) $null $auth
    $stock = Invoke-Api GET $availabilityPath $null
    if ($afterCancel.inventoryState -eq 'RELEASED' -and [int]$stock.available -ge $startingAvailability) { break }
    Start-Sleep -Seconds 2
} while ((Get-Date) -lt $deadline)
Assert-True ($afterCancel.inventoryState -eq 'RELEASED') 'Hủy đơn chưa hoàn tồn kho.'
Assert-True ([int]$stock.available -ge $startingAvailability) 'Tồn kho không được hoàn sau khi hủy.'
Write-Output 'PASS inventory reconciliation: stock released'

$tooMany = $startingAvailability + 1
if ($tooMany -le 99) {
    $cart = Invoke-Api POST '/cart/items' @{
        productId = $product.id
        selectedSize = $size
        selectedColor = $color
        quantity = $tooMany
    } $auth
    $rejectedBody = $orderBody.Clone()
    $rejectedBody.expectedCartItems = @(@{ id = $cart.items[0].id; quantity = $tooMany })
    $rejected = Invoke-Api POST '/orders' $rejectedBody @{
        Authorization = $auth.Authorization
        'Idempotency-Key' = [guid]::NewGuid().ToString()
    }
    $deadline = (Get-Date).AddSeconds(45)
    do {
        $rejected = Invoke-Api GET ('/orders/' + $rejected.id) $null $auth
        if ($rejected.inventoryState -ne 'PENDING') { break }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    Assert-True ($rejected.status -eq 'CANCELLED' -and $rejected.inventoryState -eq 'REJECTED') 'Đơn vượt tồn kho không bị từ chối.'
    $cart = Invoke-Api GET '/cart' $null $auth
    Assert-True (@($cart.items).Count -eq 1) 'Giỏ hàng bị xóa khi đơn thiếu tồn kho.'
    Invoke-Api DELETE '/cart' $null $auth | Out-Null
    Write-Output 'PASS insufficient stock: order rejected, cart preserved'
}
Write-Output 'SMOKE TEST PASSED'
