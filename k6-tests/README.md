# k6 성능 테스트 가이드

이 디렉토리는 이커머스 프로젝트의 성능 테스트를 위한 k6 스크립트와 계획 문서를 포함합니다.

## 📚 문서 구조

| 문서 | 용도 | 대상 |
|------|------|------|
| **README.md** (현재) | 전체 개요 및 기술 가이드 | 개발자 |
| **[LOAD_TEST_PLAN.md](LOAD_TEST_PLAN.md)** | 부하 테스트 전체 계획서 | PM, 테스트 리더 |
| **[EXECUTION_CHECKLIST.md](EXECUTION_CHECKLIST.md)** | 단계별 실행 체크리스트 | 테스트 수행자 |
| **[QUICKSTART.md](QUICKSTART.md)** | 5분 빠른 시작 가이드 | 초보자 |
| **[TEST_RESULT_TEMPLATE.md](TEST_RESULT_TEMPLATE.md)** | 결과 보고서 템플릿 | 테스트 수행자 |

## 🎯 어떤 문서를 봐야 할까요?

- **처음 시작하는 경우** → [QUICKSTART.md](QUICKSTART.md)
- **전체 계획을 알고 싶다면** → [LOAD_TEST_PLAN.md](LOAD_TEST_PLAN.md)
- **실제 테스트를 수행한다면** → [EXECUTION_CHECKLIST.md](EXECUTION_CHECKLIST.md)
- **자세한 기술 정보** → 아래 계속 읽기

## 목차
- [설치](#설치)
- [사전 준비](#사전-준비)
- [테스트 실행](#테스트-실행)
- [테스트 시나리오](#테스트-시나리오)
- [결과 분석](#결과-분석)

## 설치

### Windows
```bash
# Chocolatey 사용
choco install k6

# 또는 winget 사용
winget install k6
```

### 설치 확인
```bash
k6 version
```

## 사전 준비

### 1. 인프라 실행
테스트 전에 다음 서비스들이 실행되어야 합니다:

```bash
# Docker Compose로 MySQL, Redis, Kafka 실행
docker-compose up -d

# 서비스 확인
docker-compose ps
```

### 2. 애플리케이션 서버 실행
```bash
# Gradle로 애플리케이션 실행
./gradlew bootRun

# 서버 확인 (다른 터미널에서)
curl http://localhost:8080/actuator/health
```

### 3. 테스트 데이터 준비

#### 테스트용 쿠폰 생성
MySQL에 접속하여 테스트용 쿠폰을 생성합니다:

```sql
-- MySQL 접속
-- docker exec -it <mysql_container_name> mysql -u ecommerce_user -p

-- 테스트용 쿠폰 생성 (재고 100개)
INSERT INTO coupon (name, description, discount_type, discount_value, minimum_order_amount, maximum_discount_amount, quantity, issued_count, start_date, end_date, status, created_at, updated_at)
VALUES
('테스트 선착순 쿠폰', 'k6 성능 테스트용 쿠폰', 'FIXED', 5000, 10000, 5000, 100, 0, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), 'ACTIVE', NOW(), NOW());

-- 생성된 쿠폰 ID 확인
SELECT id, name, quantity, issued_count FROM coupon WHERE name LIKE '테스트%';
```

#### 테스트용 사용자 생성
```sql
-- 테스트용 사용자 100명 생성 (public_id: 1~100)
-- 참고: 실제 애플리케이션의 User 테이블 구조에 맞게 수정 필요

INSERT INTO users (public_id, name, balance, created_at, updated_at)
SELECT
    n AS public_id,
    CONCAT('test_user_', n) AS name,
    100000 AS balance,
    NOW() AS created_at,
    NOW() AS updated_at
FROM (
    SELECT @row := @row + 1 AS n
    FROM (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t1,
         (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t2,
         (SELECT @row := 0) t3
    LIMIT 100
) numbers;

-- 생성된 사용자 확인
SELECT COUNT(*) FROM users WHERE name LIKE 'test_user_%';
```

### 4. Redis 재고 초기화 (선착순 쿠폰용)
```bash
# Redis CLI 접속
docker exec -it <redis_container_name> redis-cli

# 쿠폰 재고 설정 (쿠폰 ID가 1인 경우)
SET coupon:stock:1 100

# 확인
GET coupon:stock:1

# 기존 발급 기록 삭제 (재테스트 시)
KEYS coupon:issued:1:*
# 위에서 나온 키들 삭제
DEL coupon:issued:1:1 coupon:issued:1:2 ...
# 또는 패턴으로 삭제 (주의: 프로덕션에서는 사용 금지)
# redis-cli KEYS "coupon:issued:1:*" | xargs redis-cli DEL
```

## 테스트 실행

### 환경 변수 설정
테스트 실행 시 환경 변수로 설정을 변경할 수 있습니다:

```bash
# Windows PowerShell
$env:BASE_URL="http://localhost:8080"
$env:COUPON_ID="1"
$env:USER_ID_START="1"
$env:USER_ID_END="100"
```

### 1. 간단한 테스트 (추천: 첫 실행)
100명의 사용자가 10초 동안 동시에 쿠폰 발급 요청:

```bash
k6 run scenarios/coupon-fcfs-simple.js
```

### 2. 전체 시나리오 테스트
Smoke → Load → Stress → Spike 테스트를 순차 실행 (약 9분 소요):

```bash
k6 run scenarios/coupon-fcfs-concurrency.js
```

### 3. 커스텀 설정으로 실행
```bash
# 특정 쿠폰과 사용자 범위로 실행
k6 run -e COUPON_ID=2 -e USER_ID_START=101 -e USER_ID_END=200 scenarios/coupon-fcfs-simple.js

# 다른 서버로 실행
k6 run -e BASE_URL=http://your-server:8080 scenarios/coupon-fcfs-simple.js
```

### 4. 결과를 파일로 저장
```bash
# JSON 파일로 저장
k6 run --out json=result.json scenarios/coupon-fcfs-simple.js

# CSV 파일로 저장
k6 run --out csv=result.csv scenarios/coupon-fcfs-simple.js

# InfluxDB로 전송 (모니터링 대시보드 연동 시)
k6 run --out influxdb=http://localhost:8086/k6 scenarios/coupon-fcfs-simple.js
```

## 테스트 시나리오

### 1. coupon-fcfs-simple.js (간단 테스트)
- **목적**: 빠른 동작 확인
- **설정**: 100 VU, 10초
- **예상 결과**: 재고 100개 → 성공 ~100건, 나머지 재고 소진

### 2. coupon-fcfs-concurrency.js (전체 시나리오)
4단계 테스트:

#### Stage 1: Smoke Test (0~30초)
- 10 VU, 30초
- 목적: 기본 동작 확인

#### Stage 2: Load Test (35초~2m35초)
- 50 → 100 → 0 VU
- 목적: 정상 부하 처리 확인

#### Stage 3: Stress Test (3분~8분)
- 100 → 200 → 300 → 0 VU
- 목적: 시스템 한계 확인

#### Stage 4: Spike Test (8분~8m50초)
- 10초 만에 500 VU 급증
- 목적: 실제 선착순 상황 시뮬레이션

## 결과 분석

### 주요 메트릭

#### HTTP 메트릭
- `http_reqs`: 총 요청 수
- `http_req_duration`: 요청 응답 시간
  - `avg`: 평균
  - `p(95)`: 95 백분위수
  - `p(99)`: 99 백분위수
- `http_req_failed`: 실패율

#### 커스텀 메트릭
- `coupon_issue_success`: 성공 건수 (202)
- `coupon_issue_duplicate`: 중복 발급 (409)
- `coupon_issue_stock_out`: 재고 소진 (400)
- `coupon_issue_failure`: 기타 실패

### 성공 기준

#### 기능 검증
- ✅ 성공 건수 ≤ 재고 수 (재고 초과 발급 방지)
- ✅ 중복 발급 = 0 (같은 사용자 재발급 방지)
- ✅ 성공 + 재고소진 = 총 요청 수 (모든 요청 처리)

#### 성능 검증
- ✅ `http_req_failed` < 5%
- ✅ `http_req_duration p(95)` < 1000ms
- ✅ `http_req_duration p(99)` < 2000ms

### 예상 결과 예시

```
========== 테스트 요약 ==========
총 요청 수: 500
성공 (202): 100
중복 (409): 0
재고 소진 (400): 400
기타 실패: 0
평균 응답 시간: 156.23ms
95% 응답 시간: 342.67ms
================================
```

## 문제 해결

### 테스트가 실패하는 경우

#### 1. 연결 오류 (Connection refused)
```
해결: 애플리케이션 서버가 실행 중인지 확인
curl http://localhost:8080/actuator/health
```

#### 2. 모든 요청이 400 (재고 소진)
```
해결: Redis 재고 재설정
docker exec -it <redis_container> redis-cli
SET coupon:stock:1 100
```

#### 3. 모든 요청이 409 (중복 발급)
```
해결: Redis 발급 기록 삭제
docker exec -it <redis_container> redis-cli
# KEYS "coupon:issued:1:*" 확인 후 삭제
```

#### 4. 응답 시간이 너무 느림
```
원인: Kafka Consumer가 처리 중일 수 있음
확인: 애플리케이션 로그에서 "CouponIssueEventHandler" 로그 확인
```

## 다음 단계

선착순 쿠폰 테스트가 성공했다면:

1. **대기열 시스템 테스트** - `scenarios/queue-load.js` (TODO)
2. **주문 생성 테스트** - Orchestration vs Choreography 비교 (TODO)
3. **통합 시나리오 테스트** - 실제 사용자 플로우 (TODO)

## 참고 자료

- [k6 공식 문서](https://k6.io/docs/)
- [k6 예제 모음](https://k6.io/docs/examples/)
- [성능 테스트 베스트 프랙티스](https://k6.io/docs/testing-guides/test-types/)