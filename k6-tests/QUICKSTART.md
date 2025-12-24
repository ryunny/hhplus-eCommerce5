# 빠른 시작 가이드

k6 테스트를 5분 안에 시작하는 방법입니다.

## 1단계: 준비 (2분)

### 인프라 실행
```bash
# 프로젝트 루트에서
docker-compose up -d
```

### 애플리케이션 실행
```bash
# 다른 터미널에서
./gradlew bootRun
```

## 2단계: 테스트 데이터 준비 (2분)

### MySQL에 테스트 쿠폰 생성
```bash
# MySQL 컨테이너 이름 확인
docker ps | findstr mysql

# MySQL 접속 (비밀번호: ecommerce123)
docker exec -it <mysql_container_name> mysql -u ecommerce_user -p

# 또는 docker-compose 사용
docker-compose exec mysql mysql -u ecommerce_user -p
```

MySQL에서 실행:
```sql
-- 쿠폰 생성
INSERT INTO coupon (name, description, discount_type, discount_value, minimum_order_amount, maximum_discount_amount, quantity, issued_count, start_date, end_date, status, created_at, updated_at)
VALUES ('k6 테스트 쿠폰', 'k6 테스트용', 'FIXED', 5000, 10000, 5000, 100, 0, NOW(), DATE_ADD(NOW(), INTERVAL 30 DAY), 'ACTIVE', NOW(), NOW());

-- 쿠폰 ID 확인 (메모해두기!)
SELECT id FROM coupon WHERE name = 'k6 테스트 쿠폰';
```

### Redis에 재고 설정
```bash
# Redis 컨테이너 이름 확인
docker ps | findstr redis

# Redis CLI 접속
docker exec -it <redis_container_name> redis-cli

# 또는 docker-compose 사용
docker-compose exec redis redis-cli
```

Redis에서 실행 (쿠폰 ID를 위에서 확인한 값으로 변경):
```
SET coupon:stock:1 100
GET coupon:stock:1
```

### 테스트 사용자 확인
```sql
-- MySQL에서 실행
-- 기존 사용자 ID 범위 확인
SELECT MIN(public_id), MAX(public_id) FROM users;

-- 또는 테스트 사용자 생성 (setup-test-data.sql 참고)
```

## 3단계: 테스트 실행 (1분)

### 방법 1: PowerShell 스크립트 (추천)
```powershell
cd k6-tests

# 기본 실행 (쿠폰 ID=1, 사용자 1~100)
.\run-test.ps1

# 커스텀 설정
.\run-test.ps1 -TestType simple -CouponId 1 -UserIdStart 1 -UserIdEnd 100
```

### 방법 2: 직접 실행
```bash
cd k6-tests

# 환경 변수 설정 (PowerShell)
$env:COUPON_ID="1"
$env:USER_ID_START="1"
$env:USER_ID_END="100"

# 실행
k6 run scenarios/coupon-fcfs-simple.js
```

## 결과 확인

테스트가 끝나면 다음과 같은 요약이 출력됩니다:

```
========== 간단 테스트 결과 ==========
✅ 성공: 100건
⚠️  중복: 0건
❌ 재고 소진: 0건
❌ 기타 실패: 0건
평균 응답시간: 156.23ms
====================================
```

### 성공 기준
- ✅ 성공 건수 ≤ 100 (재고 수)
- ✅ 중복 = 0
- ✅ 평균 응답시간 < 500ms

## 문제 해결

### "Connection refused" 에러
```bash
# 서버 실행 확인
curl http://localhost:8080/actuator/health
```

### 모든 요청이 실패
```bash
# Redis 재고 확인
docker exec -it <redis_container> redis-cli
GET coupon:stock:1

# 재고 재설정
SET coupon:stock:1 100
```

### 재테스트 시
```bash
# Redis 발급 기록 삭제
docker exec -it <redis_container> redis-cli
KEYS "coupon:issued:1:*"
# 표시된 키들 삭제

# MySQL 발급 기록 삭제
DELETE FROM user_coupon WHERE coupon_id = 1;
UPDATE coupon SET issued_count = 0 WHERE id = 1;
```

## 다음 단계

1. **전체 시나리오 테스트**
   ```bash
   .\run-test.ps1 -TestType full
   ```

2. **결과 분석**
   - `results/` 디렉토리의 JSON 파일 확인

3. **다른 시나리오 테스트**
   - 대기열 시스템
   - 주문 생성 (Orchestration vs Choreography)

자세한 내용은 [README.md](README.md)를 참고하세요.