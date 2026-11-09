param([string]$BaseUrl = 'http://127.0.0.1:8280/api/v1')

$ErrorActionPreference = 'Stop'
if ($BaseUrl -notmatch '^http://(127\.0\.0\.1|localhost):\d+/api/v1$') {
    throw 'Password reset smoke test chỉ chạy trên Gateway local.'
}
$email = 'reset+' + [guid]::NewGuid().ToString('N') + '@example.test'
$oldPassword = 'OldPassword123!'
$newPassword = 'NewPassword456!'
$register = Invoke-RestMethod -Method POST -Uri "$BaseUrl/auth/register" `
    -ContentType 'application/json' -Body (@{
        email = $email; password = $oldPassword; fullName = 'Reset Integration Test'
    } | ConvertTo-Json -Compress)
if ($register.user.email -ne $email) { throw 'Không đăng ký được tài khoản reset test.' }

Invoke-RestMethod -Method POST -Uri "$BaseUrl/auth/password-reset/request" `
    -ContentType 'application/json' -Body (@{ email = $email } | ConvertTo-Json -Compress) | Out-Null
$messageUrl = 'http://127.0.0.1:8025/view/latest.txt?query=' + [uri]::EscapeDataString("to:$email")
$deadline = (Get-Date).AddSeconds(20)
$message = $null
do {
    try { $message = Invoke-RestMethod -Uri $messageUrl -TimeoutSec 5 } catch { Start-Sleep -Milliseconds 500 }
} while ($null -eq $message -and (Get-Date) -lt $deadline)
if ($null -eq $message -or $message -notmatch 'Mã đặt lại mật khẩu của bạn: ([0-9]{8})') {
    throw 'Không nhận được email chứa mã khôi phục trong Mailpit.'
}
$code = $Matches[1]
$wrongCode = if ($code -eq '00000000') { '99999999' } else { '00000000' }

try {
    Invoke-RestMethod -Method POST -Uri "$BaseUrl/auth/password-reset/confirm" `
        -ContentType 'application/json' -Body (@{
            email = $email; code = $wrongCode; newPassword = $newPassword
        } | ConvertTo-Json -Compress) | Out-Null
    throw 'Mã sai vẫn được chấp nhận.'
} catch {
    if ([int]$_.Exception.Response.StatusCode -ne 400) { throw }
}
Invoke-RestMethod -Method POST -Uri "$BaseUrl/auth/password-reset/confirm" `
    -ContentType 'application/json' -Body (@{
        email = $email; code = $code; newPassword = $newPassword
    } | ConvertTo-Json -Compress) | Out-Null

try {
    Invoke-RestMethod -Method POST -Uri "$BaseUrl/auth/login" `
        -ContentType 'application/json' -Body (@{
            email = $email; password = $oldPassword
        } | ConvertTo-Json -Compress) | Out-Null
    throw 'Mật khẩu cũ vẫn đăng nhập được.'
} catch {
    if ([int]$_.Exception.Response.StatusCode -ne 401) { throw }
}
$login = Invoke-RestMethod -Method POST -Uri "$BaseUrl/auth/login" `
    -ContentType 'application/json' -Body (@{
        email = $email; password = $newPassword
    } | ConvertTo-Json -Compress)
if ($login.user.email -ne $email) { throw 'Mật khẩu mới không đăng nhập được.' }
try {
    Invoke-RestMethod -Method POST -Uri "$BaseUrl/auth/refresh" `
        -ContentType 'application/json' -Body (@{
            refreshToken = $register.refreshToken
        } | ConvertTo-Json -Compress) | Out-Null
    throw 'Refresh token cũ vẫn dùng được sau đổi mật khẩu.'
} catch {
    if ([int]$_.Exception.Response.StatusCode -ne 401) { throw }
}
try {
    Invoke-RestMethod -Method POST -Uri "$BaseUrl/auth/password-reset/confirm" `
        -ContentType 'application/json' -Body (@{
            email = $email; code = $code; newPassword = 'AnotherPassword789!'
        } | ConvertTo-Json -Compress) | Out-Null
    throw 'Mã khôi phục đã dùng vẫn được chấp nhận.'
} catch {
    if ([int]$_.Exception.Response.StatusCode -ne 400) { throw }
}
Write-Output 'PASS password reset: email delivered, wrong/replayed code rejected, old password and refresh revoked'
