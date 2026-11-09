param(
    [string]$BaseUrl,
    [int]$CatalogPort,
    [string]$CatalogDatabase,
    [string]$InternalKey
)

$ErrorActionPreference = 'Stop'
if ($BaseUrl -notmatch '^https?://(127\.0\.0\.1|localhost):\d+/api/v1/?$' -or
    $CatalogDatabase -notmatch '^ecommerce_it_[a-zA-Z0-9_]+_catalog$') {
    throw 'Quota smoke test chỉ chạy trên database tích hợp và API local.'
}
$campaigns = @(Invoke-RestMethod -Uri "$($BaseUrl.TrimEnd('/'))/flash-sales/active")
$sale = $campaigns[0]
$product = @($sale.products)[0]
if ($null -eq $product -or [int]$product.flashSaleRemaining -ne 20) {
    throw "Flash Sale demo chưa sẵn sàng: campaign=$($sale.id), product=$($product.id), remaining=$($product.flashSaleRemaining)."
}
$size = $product.availableSizes[0]
$color = $product.colorNames[0]
$availabilityUrl = "$($BaseUrl.TrimEnd('/'))/products/$($product.id)/availability?size=$([Uri]::EscapeDataString($size))&color=$([Uri]::EscapeDataString($color))"
$startingStock = [int](Invoke-RestMethod -Uri $availabilityUrl).available
if ($startingStock -lt 2) { throw 'Không đủ hàng để kiểm tra hai yêu cầu đồng thời.' }

$setOne = 'db.flash_sale_allocations.updateOne({_id:"demo_week:prod_dress_wrap"},{$set:{capacity:1,available:1}})'
$restore = 'db.flash_sale_allocations.updateOne({_id:"demo_week:prod_dress_wrap"},{$set:{capacity:20,available:20}})'
$jobs = @()
$winner = $null
try {
    docker compose exec -T mongodb mongosh --quiet $CatalogDatabase --eval $setOne | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Không đặt được hạn mức kiểm thử.' }
    $body = @{ lines = @(@{
        productId = $product.id
        size = $size
        color = $color
        quantity = 1
        flashSaleId = 'demo_week'
        unitPrice = $product.price
    }) } | ConvertTo-Json -Depth 6 -Compress
    foreach ($index in 1..2) {
        $orderId = 'ord_' + ([string]$index * 64)
        $jobs += Start-Job -ScriptBlock {
            param($port, $key, $id, $payload)
            try {
                $result = Invoke-WebRequest -Method POST `
                    -Uri "http://127.0.0.1:$port/internal/inventory/reservations/$id" `
                    -Headers @{ 'X-Internal-Api-Key' = $key } -ContentType 'application/json' `
                    -Body $payload -TimeoutSec 20
                [pscustomobject]@{ id = $id; status = [int]$result.StatusCode }
            } catch {
                [pscustomobject]@{ id = $id; status = [int]$_.Exception.Response.StatusCode }
            }
        } -ArgumentList $CatalogPort, $InternalKey, $orderId, $body
    }
    $jobs | Wait-Job | Out-Null
    $results = @($jobs | Receive-Job)
    $winner = $results | Where-Object status -eq 204 | Select-Object -First 1
    if (@($results | Where-Object status -eq 204).Count -ne 1 -or
        @($results | Where-Object status -eq 409).Count -ne 1) {
        throw "Hai yêu cầu tranh một lượt sale trả: $($results | ConvertTo-Json -Compress)"
    }
    $allocation = Invoke-RestMethod -Uri "$($BaseUrl.TrimEnd('/'))/products/$($product.id)"
    if ($null -ne $allocation.flashSaleId) { throw 'Giá sale vẫn hiển thị khi hết hạn mức.' }
    if ([int](Invoke-RestMethod -Uri $availabilityUrl).available -ne ($startingStock - 1)) {
        throw 'Tồn kho không được hoàn cho yêu cầu thua hạn mức.'
    }
    Write-Output 'PASS flash sale concurrency: one winner, one conflict, no stock leak'
} finally {
    $jobs | Remove-Job -Force -ErrorAction SilentlyContinue
    if ($null -ne $winner) {
        try {
            Invoke-WebRequest -Method POST `
                -Uri "http://127.0.0.1:$CatalogPort/internal/inventory/reservations/$($winner.id)/release" `
                -Headers @{ 'X-Internal-Api-Key' = $InternalKey } -ContentType 'application/json' `
                -Body $body -TimeoutSec 20 | Out-Null
        } catch { Write-Warning "Không thể hoàn reservation kiểm thử: $_" }
    }
    docker compose exec -T mongodb mongosh --quiet $CatalogDatabase --eval $restore | Out-Null
}
if ([int](Invoke-RestMethod -Uri $availabilityUrl).available -ne $startingStock) {
    throw 'Tồn kho chưa được hoàn sau kiểm thử đồng thời.'
}
Write-Output 'PASS flash sale concurrency cleanup: stock and quota restored'
