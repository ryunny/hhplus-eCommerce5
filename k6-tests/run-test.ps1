# k6 테스트 실행 PowerShell 스크립트
# 사용법: .\run-test.ps1 [테스트종류] [쿠폰ID] [시작사용자ID] [종료사용자ID]
# 예시: .\run-test.ps1 simple 1 1 100

param(
    [string]$TestType = "simple",
    [string]$CouponId = "1",
    [string]$UserIdStart = "1",
    [string]$UserIdEnd = "100",
    [string]$BaseUrl = "http://localhost:8080"
)

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "k6 성능 테스트 실행 스크립트" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 환경 변수 설정
$env:BASE_URL = $BaseUrl
$env:COUPON_ID = $CouponId
$env:USER_ID_START = $UserIdStart
$env:USER_ID_END = $UserIdEnd

Write-Host "설정 정보:" -ForegroundColor Yellow
Write-Host "  - 서버 URL: $BaseUrl" -ForegroundColor White
Write-Host "  - 쿠폰 ID: $CouponId" -ForegroundColor White
Write-Host "  - 사용자 ID 범위: $UserIdStart ~ $UserIdEnd" -ForegroundColor White
Write-Host "  - 테스트 타입: $TestType" -ForegroundColor White
Write-Host ""

# 서버 상태 확인
Write-Host "서버 상태 확인 중..." -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "$BaseUrl/actuator/health" -TimeoutSec 5 -ErrorAction Stop
    if ($response.StatusCode -eq 200) {
        Write-Host "✅ 서버 정상 작동 중" -ForegroundColor Green
    }
} catch {
    Write-Host "❌ 서버 연결 실패: $BaseUrl" -ForegroundColor Red
    Write-Host "애플리케이션 서버가 실행 중인지 확인해주세요." -ForegroundColor Red
    exit 1
}

Write-Host ""

# 테스트 파일 선택
$testFile = switch ($TestType.ToLower()) {
    "simple" { "scenarios/coupon-fcfs-simple.js" }
    "full" { "scenarios/coupon-fcfs-concurrency.js" }
    "concurrency" { "scenarios/coupon-fcfs-concurrency.js" }
    default {
        Write-Host "❌ 알 수 없는 테스트 타입: $TestType" -ForegroundColor Red
        Write-Host "사용 가능한 타입: simple, full, concurrency" -ForegroundColor Yellow
        exit 1
    }
}

# 테스트 실행
Write-Host "테스트 시작: $testFile" -ForegroundColor Green
Write-Host "----------------------------------------" -ForegroundColor Cyan

$timestamp = Get-Date -Format "yyyy-MM-dd_HH-mm-ss"
$outputFile = "results/result_$timestamp.json"

# results 디렉토리 생성
if (-not (Test-Path "results")) {
    New-Item -ItemType Directory -Path "results" | Out-Null
}

# k6 실행
k6 run --out json=$outputFile $testFile

if ($LASTEXITCODE -eq 0) {
    Write-Host ""
    Write-Host "✅ 테스트 완료!" -ForegroundColor Green
    Write-Host "결과 파일: $outputFile" -ForegroundColor Cyan
} else {
    Write-Host ""
    Write-Host "❌ 테스트 실패 (종료 코드: $LASTEXITCODE)" -ForegroundColor Red
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan