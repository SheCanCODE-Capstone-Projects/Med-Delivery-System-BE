Write-Host "Testing MedDelivery API Registration Endpoint" -ForegroundColor Green
Write-Host "==============================================" -ForegroundColor Green
Write-Host ""

# Test 1: Health Check
Write-Host "1. Testing Health Check..." -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "https://med-delivery-system-be-production.up.railway.app/health" -Method GET -UseBasicParsing
    Write-Host "Status Code: $($response.StatusCode)" -ForegroundColor Green
    Write-Host "Response: $($response.Content)" -ForegroundColor Cyan
} catch {
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# Test 2: Registration
Write-Host "2. Testing Registration Endpoint..." -ForegroundColor Yellow
$body = @{
    fullName = "Test User"
    email = "test@example.com"
    phoneNumber = "+1234567890"
} | ConvertTo-Json

Write-Host "Request Body: $body" -ForegroundColor Gray

try {
    $headers = @{
        "Content-Type" = "application/json"
        "Accept" = "application/json"
    }
    
    $response = Invoke-WebRequest -Uri "https://med-delivery-system-be-production.up.railway.app/api/auth/register" `
        -Method POST `
        -Headers $headers `
        -Body $body `
        -UseBasicParsing
    Write-Host "Status Code: $($response.StatusCode)" -ForegroundColor Green
    Write-Host "Response: $($response.Content)" -ForegroundColor Cyan
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Write-Host "Status Code: $statusCode" -ForegroundColor Red
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    if ($_.ErrorDetails.Message) {
        Write-Host "Details: $($_.ErrorDetails.Message)" -ForegroundColor Yellow
    }
}
Write-Host ""

Write-Host "==============================================" -ForegroundColor Green
Write-Host "Test Complete" -ForegroundColor Green
