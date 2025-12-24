# k6 부하 테스트 실행 체크리스트

이 문서는 부하 테스트를 처음 실행하는 사람도 따라할 수 있도록 단계별로 안내합니다.

---

## 📋 전체 흐름

```
[준비] → [실행] → [모니터링] → [분석] → [정리]
  30분     10분        10분        30분      10분
```

---

## 🚀 Phase 1: 사전 준비 (30분)

### Step 1: 인프라 실행 확인

```bash
# 1. Docker Compose 서비스 확인
docker-compose ps
```

**체크포인트**:
- [ ] MySQL 컨테이너 실행 중 (포트 3306)
- [ ] Redis 컨테이너 실행 중 (포트 6379)
- [ ] Kafka 컨테이너 실행 중 (포트 9092)
- [ ] Zookeeper 컨테이너 실행 중 (포트 2181)

**문제 발생 시**:
```bash
# 모든 서비스 재시작
docker-compose down
docker-compose up -d

# 개별 서비스 재시작 (예: Kafka)
docker-compose restart kafka
```

---

### Step 2: 애플리케이션 서버 실행

```bash
# 방법 1: Gradle로 직접 실행
./gradlew bootRun

# 방법 2: 빌드 후 JAR 실행
./gradlew build -x test
java -jar build/libs/ecommerce-0.0.1-SNAPSHOT.jar

# 방법 3: IDE에서 실행
# IntelliJ 또는 VS Code에서 EcommerceApplication.main() 실행
```

**체크포인트**:
- [ ] 서버 시작 성공 (로그 확인)
- [ ] 포트 8080 리스닝 중
- [ ] Kafka 연결 성공
- [ ] Redis 연결 성공
- [ ] MySQL 연결 성공

**서버 상태 확인**:
```bash
# 헬스체크
curl http://localhost:8080/actuator/health

# 간단한 API 테스트
curl http://localhost:8080/api/products
```

---

### Step 3: 테스트 데이터 준비

#### 3-1. MySQL 데이터 생성

```bash
# MySQL 접속
docker exec -it ecommerce-mysql mysql -u ecommerce_user -p
# 비밀번호: ecommerce123
```

```sql
-- 1. 테스트용 쿠폰 생성 (재고 100개)
INSERT INTO coupons (
    name, coupon_type, discount_rate, discount_amount,
    min_order_amount, total_quantity, issued_quantity,
    start_date, end_date, use_queue, version
)
VALUES (
    'k6 테스트 선착순 쿠폰',
    'RATE',
    10,  -- 10% 할인
    NULL,
    10000,  -- 최소 주문 금액
    100,    -- 총 재고
    0,      -- 발급 수
    NOW(),
    DATE_ADD(NOW(), INTERVAL 30 DAY),
    0,      -- 대기열 사용 안 함
    0       -- 낙관적 락 버전
);

-- 2. 생성된 쿠폰 ID 확인 (메모해두기!)
SELECT id, name, total_quantity FROM coupons
WHERE name LIKE 'k6 테스트%'
ORDER BY id DESC LIMIT 1;
-- 결과: id = ??? (이 값을 기억!)
```

**체크포인트**:
- [ ] 쿠폰 생성 완료
- [ ] 쿠폰 ID 기록: `______`

#### 3-2. 사용자 데이터 확인

```sql
-- 기존 사용자 수 확인
SELECT COUNT(*) FROM users;

-- 사용자 public_id 샘플 확인
SELECT id, public_id, name FROM users LIMIT 10;
```

**체크포인트**:
- [ ] 사용자 50명 이상 존재
- [ ] public_id 형식 확인 (UUID)

**사용자 부족 시**:
```sql
-- setup-test-data.sql 파일 참고하여 사용자 생성
```

#### 3-3. Redis 재고 설정

```bash
# Redis CLI 접속
docker exec -it ecommerce-redis redis-cli
```

```redis
# 쿠폰 재고 설정 (쿠폰 ID를 위에서 확인한 값으로 변경)
SET coupon:stock:1 100

# 확인
GET coupon:stock:1
# 결과: "100"

# 기존 발급 기록 삭제 (재테스트 시)
KEYS coupon:issued:1:*
# (표시된 키들을 삭제)
```

**체크포인트**:
- [ ] Redis 재고 설정 완료 (100)
- [ ] 기존 발급 기록 삭제 완료

---

### Step 4: k6 설정 파일 수정

```bash
cd k6-tests
```

**config/env.js 수정**:
```javascript
export const config = {
    baseUrl: 'http://localhost:8080',  // ✅ 포트 확인
    couponId: '1',  // ✅ 위에서 생성한 쿠폰 ID로 변경
    userIdStart: 1,
    userIdEnd: 50,  // ✅ 사용자 수에 맞게 조정
    // ...
};
```

**체크포인트**:
- [ ] baseUrl 올바른 포트 설정
- [ ] couponId 올바른 쿠폰 ID 설정
- [ ] userIdEnd 사용자 수에 맞게 설정

---

## 🎯 Phase 2: 테스트 실행 (10분)

### Step 5: Smoke Test (간단 테스트)

```bash
cd k6-tests

# 환경 변수 설정 (PowerShell)
$env:COUPON_ID="1"
$env:USER_ID_START="1"
$env:USER_ID_END="50"

# 실행
k6 run scenarios/coupon-fcfs-simple.js
```

**예상 출력**:
```
running (00m10.0s), 000/100 VUs, 2000 complete and 0 interrupted iterations
✓ 상태 코드 확인

========== 간단 테스트 결과 ==========
✅ 성공: 100건
⚠️  중복: 0건
❌ 재고 소진: 1900건
평균 응답시간: 156.23ms
====================================
```

**체크포인트**:
- [ ] 테스트 정상 완료
- [ ] 성공 건수 = 재고 수 (100)
- [ ] 중복 발급 = 0
- [ ] 평균 응답 시간 < 500ms
- [ ] 에러 없음

**실패 시 조치**:
```bash
# Connection refused → 서버 실행 확인
curl http://localhost:8080/api/products

# 모든 요청 실패 → Redis 재고 확인
docker exec ecommerce-redis redis-cli GET coupon:stock:1

# 중복 발급 발생 → Redis 발급 기록 삭제 후 재실행
```

---

### Step 6: 전체 시나리오 테스트 (선택)

**⚠️ 주의**: 약 9분 소요

```bash
# 전체 시나리오 실행
k6 run scenarios/coupon-fcfs-concurrency.js
```

**실행 단계**:
1. **Smoke Test** (0-30초): 기본 동작 확인
2. **Load Test** (35초-2분35초): 점진적 부하 증가
3. **Stress Test** (3분-8분): 시스템 한계 테스트
4. **Spike Test** (8분-9분): 급격한 트래픽 증가

**체크포인트**:
- [ ] 모든 단계 완료
- [ ] 각 단계별 결과 확인
- [ ] threshold 통과

---

## 📊 Phase 3: 모니터링 (실시간)

### Step 7: 실시간 모니터링

**별도 터미널에서 실행**:

#### 터미널 1: 애플리케이션 로그
```bash
# 로그 실시간 확인
tail -f logs/application.log

# 또는 에러만 필터링
tail -f logs/application.log | grep ERROR
```

#### 터미널 2: Redis 모니터링
```bash
docker exec -it ecommerce-redis redis-cli

# Redis 명령어
MONITOR  # 모든 명령어 실시간 출력
INFO stats  # 통계 확인
```

#### 터미널 3: Kafka Consumer Lag
```bash
# Kafka Consumer Group 확인
docker exec ecommerce-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group ecommerce-payment-group \
  --describe
```

**체크포인트**:
- [ ] 애플리케이션 에러 없음
- [ ] Redis 응답 정상
- [ ] Kafka Consumer Lag < 100

---

## 🔍 Phase 4: 결과 분석 (30분)

### Step 8: k6 결과 확인

**메트릭 확인**:
```
http_reqs................: ???? (???/s)
http_req_duration........: avg=???ms p95=???ms p99=???ms
http_req_failed..........: ??%

coupon_issue_success.....: ????
coupon_issue_duplicate...: ????
coupon_issue_stock_out...: ????
```

**체크리스트**:
- [ ] 총 요청 수 기록
- [ ] 성공 건수 ≤ 재고 수
- [ ] 중복 발급 = 0
- [ ] p95 < 1000ms
- [ ] 에러율 < 5%

---

### Step 9: DB 검증

```sql
-- 1. 쿠폰 발급 결과 확인
SELECT
    c.id,
    c.name,
    c.total_quantity,
    c.issued_quantity,
    COUNT(uc.id) AS actual_issued,
    (c.total_quantity - COUNT(uc.id)) AS remaining
FROM coupons c
LEFT JOIN user_coupons uc ON c.id = uc.coupon_id
WHERE c.id = 1  -- ✅ 쿠폰 ID 변경
GROUP BY c.id;

-- 2. 중복 발급 확인 (있으면 안 됨!)
SELECT
    user_id,
    COUNT(*) AS duplicate_count
FROM user_coupons
WHERE coupon_id = 1  -- ✅ 쿠폰 ID 변경
GROUP BY user_id
HAVING COUNT(*) > 1;

-- 3. 발급 시간 분포 확인
SELECT
    DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS issued_time,
    COUNT(*) AS count
FROM user_coupons
WHERE coupon_id = 1  -- ✅ 쿠폰 ID 변경
GROUP BY DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s')
ORDER BY issued_time;
```

**체크포인트**:
- [ ] 실제 발급 건수 = 성공 건수
- [ ] 중복 발급 0건
- [ ] 발급 시간이 테스트 기간과 일치

---

### Step 10: 결과 보고서 작성

`TEST_RESULT_TEMPLATE.md`를 복사하여 작성:

```bash
cp TEST_RESULT_TEMPLATE.md results/test-YYYYMMDD-HHmmss.md
```

**필수 기록 항목**:
1. 테스트 설정 (VU, Duration, Coupon ID 등)
2. k6 메트릭 (요청 수, 응답 시간, 에러율)
3. 비즈니스 검증 (재고 관리, 중복 방지)
4. 발견된 이슈
5. 개선 사항

---

## 🧹 Phase 5: 정리 (10분)

### Step 11: 데이터 정리

#### 재테스트를 위한 정리:
```sql
-- 1. 테스트 쿠폰 발급 기록 삭제
DELETE FROM user_coupons
WHERE coupon_id = 1;  -- ✅ 쿠폰 ID 변경

-- 2. 발급 카운트 초기화
UPDATE coupons
SET issued_quantity = 0
WHERE id = 1;  -- ✅ 쿠폰 ID 변경

-- 확인
SELECT id, issued_quantity FROM coupons WHERE id = 1;
```

```redis
# Redis 재고 재설정
SET coupon:stock:1 100

# 발급 기록 삭제
KEYS coupon:issued:1:*
# (표시된 키들 삭제)
```

#### 테스트 완전 종료:
```sql
-- 테스트 쿠폰 완전 삭제
DELETE FROM user_coupons WHERE coupon_id IN (
  SELECT id FROM coupons WHERE name LIKE 'k6 테스트%'
);

DELETE FROM coupons WHERE name LIKE 'k6 테스트%';
```

**체크포인트**:
- [ ] DB 데이터 정리 완료
- [ ] Redis 데이터 정리 완료
- [ ] 다음 테스트 준비 완료

---

## ⚠️ 트러블슈팅

### 문제 1: Connection refused

**증상**: `curl: (7) Failed to connect`

**해결**:
```bash
# 서버 실행 확인
ps aux | grep java

# 포트 확인
netstat -ano | findstr "8080"

# 서버 재시작
./gradlew bootRun
```

### 문제 2: 모든 요청이 400 (재고 소진)

**증상**: 첫 요청부터 재고 소진 에러

**해결**:
```redis
# Redis 재고 확인
GET coupon:stock:1

# 재설정
SET coupon:stock:1 100
```

### 문제 3: 중복 발급 발생

**증상**: duplicate_count > 0

**해결**:
1. Redis 발급 기록 삭제
2. 애플리케이션 로직 확인
3. 동시성 제어 코드 검토

### 문제 4: k6 명령어를 찾을 수 없음

**증상**: `'k6' is not recognized`

**해결**:
```bash
# Windows
choco install k6
# 또는
winget install k6

# 설치 확인
k6 version
```

---

## 📝 체크리스트 요약

**테스트 전**:
- [ ] 인프라 실행 (MySQL, Redis, Kafka)
- [ ] 애플리케이션 서버 실행
- [ ] 테스트 쿠폰 생성
- [ ] Redis 재고 설정
- [ ] k6 설정 확인

**테스트 중**:
- [ ] k6 실행
- [ ] 실시간 모니터링
- [ ] 에러 확인

**테스트 후**:
- [ ] k6 결과 확인
- [ ] DB 검증
- [ ] 보고서 작성
- [ ] 데이터 정리

---

**질문이나 문제 발생 시**:
- README.md 참고
- LOAD_TEST_PLAN.md의 트러블슈팅 섹션 확인
- 이슈 트래커에 문의
