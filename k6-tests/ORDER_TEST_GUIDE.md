# 주문 시스템 부하 테스트 가이드

## 📋 목차
1. [개요](#개요)
2. [테스트 종류](#테스트-종류)
3. [실행 방법](#실행-방법)
4. [결과 해석](#결과-해석)
5. [문제 해결](#문제-해결)

---

## 개요

### 주문 vs 선착순 쿠폰 비교

| 항목 | 선착순 쿠폰 | 주문 시스템 |
|-----|-----------|-----------|
| 트래픽 패턴 | 순간 폭증 | 일정하고 지속적 |
| 성공률 | 1% (100개 쿠폰, 10,000명) | 99% (재고 충분) |
| 적합한 테스트 | **스파이크 테스트** ⭐⭐⭐⭐⭐ | **부하 + 내구성 테스트** ⭐⭐⭐⭐⭐ |
| 검증 항목 | 순간 부하 견디기 | 장시간 안정성 |

### 주문 시스템 테스트 목적

1. **부하 테스트 (Load Test)**: 정상 운영 부하에서 성능 검증
2. **패턴 비교 (Pattern Comparison)**: Orchestration vs Choreography 성능 비교
3. **내구성 테스트 (Soak Test)**: 장시간 운영 시 안정성 검증

---

## 테스트 종류

### 1. 부하 테스트 (Load Test) ⭐⭐⭐⭐⭐ 필수

**파일**: `scenarios/order-load-test.js`

**목적**: 정상 운영 부하에서 주문 시스템 성능 검증

**시나리오**:
```
워밍업:    50 VUs  (1분)
정상 부하: 100 VUs (5분)
피크타임:  200 VUs (5분) ⭐
정상 부하: 100 VUs (5분)
쿨다운:     0 VUs  (1분)

총 소요 시간: 17분
```

**검증 항목**:
- ✅ 평균 응답시간 < 500ms
- ✅ p95 < 1초
- ✅ p99 < 2초
- ✅ 에러율 < 1%
- ✅ 주문 처리 정확성

**언제 실행**:
- 매주 1회 (정기 성능 검증)
- 배포 전 (회귀 테스트)
- 성능 개선 후 (효과 검증)

---

### 2. 패턴 비교 테스트 (Pattern Comparison) ⭐⭐⭐⭐ 추천

**파일**: `scenarios/order-pattern-comparison.js`

**목적**: Orchestration vs Choreography 패턴 성능 비교

**시나리오**:
```
워밍업: 50 VUs  (30초)
비교:   100 VUs (2분)
쿨다운: 0 VUs   (30초)

총 소요 시간: 3분
```

**비교 항목**:
- 평균 응답시간
- p95, p99 응답시간
- 성공률
- 리소스 사용량

**Orchestration 특징**:
- ✅ 즉시 일관성 (Immediate Consistency)
- ✅ 명확한 트랜잭션 경계
- ⚠️ 응답 시간 길어질 수 있음

**Choreography 특징**:
- ✅ 빠른 응답 (비동기 처리)
- ✅ 확장성 좋음
- ⚠️ 최종 일관성 (Eventual Consistency)
- ⚠️ 디버깅 어려움

**언제 실행**:
- 패턴 선택 시 (아키텍처 결정)
- 성능 개선 후 (비교 분석)

---

### 3. 내구성 테스트 (Soak Test) ⭐⭐⭐ 권장

**파일**: `scenarios/order-soak-test.js`

**목적**: 장시간 운영 시 시스템 안정성 검증

**시나리오**:
```
워밍업: 100 VUs (5분)
유지:   100 VUs (2시간) ⭐
쿨다운:   0 VUs (5분)

총 소요 시간: 2시간 10분
```

**검증 항목**:
- JVM Heap 메모리 증가 추세 (메모리 누수)
- GC 빈도 및 시간 증가
- Thread 수 증가
- DB 커넥션 풀 누수
- 응답 시간 저하

**언제 실행**:
- 월 1회 (정기 안정성 검증)
- 메모리 누수 의심 시
- 대규모 이벤트 전

---

## 실행 방법

### 사전 준비

#### 1. 인프라 확인
```bash
# Docker 컨테이너 상태 확인
docker-compose ps

# 필수 서비스 확인
# - ecommerce-mysql
# - ecommerce-redis
# - ecommerce-kafka
# - ecommerce-app
# - pinpoint-web (모니터링)
```

#### 2. 애플리케이션 확인
```bash
# 헬스체크
curl http://localhost:8081/actuator/health

# 상품 목록 확인
curl http://localhost:8081/api/products | jq '.[0:5]'
```

#### 3. 데이터 확인
```bash
# MySQL 접속
docker exec -it ecommerce-mysql mysql -u ecommerce_user -p

# 상품 재고 확인
SELECT id, name, stock FROM products LIMIT 10;

# 사용자 수 확인
SELECT COUNT(*) FROM users;
```

---

### 테스트 실행

#### 1. 부하 테스트 (17분)
```bash
cd k6-tests
k6 run scenarios/order-load-test.js
```

**모니터링**:
- Pinpoint: http://localhost:8079
- 응답시간, 에러율 실시간 확인

**예상 결과**:
```
총 요청: ~5,000건
평균 응답시간: 200-400ms
p95: 500-800ms
성공률: > 99%
```

---

#### 2. 패턴 비교 테스트 (3분)
```bash
cd k6-tests
k6 run scenarios/order-pattern-comparison.js
```

**예상 결과**:
```
Orchestration:
  평균: 300-500ms
  p95: 600-900ms

Choreography:
  평균: 100-200ms ⭐ (더 빠름)
  p95: 200-400ms

차이: Choreography가 50-60% 더 빠름
```

**선택 가이드**:
- **즉시 일관성 필요** → Orchestration
- **빠른 응답 필요** → Choreography

---

#### 3. 내구성 테스트 (2시간)
```bash
cd k6-tests

# 기본 2시간
k6 run scenarios/order-soak-test.js

# 시간 변경 (4시간)
k6 run -e DURATION_HOURS=4 scenarios/order-soak-test.js
```

**모니터링 (필수)**:
- Pinpoint JVM 메트릭 지속 관찰
- 30분마다 스크린샷 저장

**확인 항목**:
- [ ] JVM Heap 메모리 일정 수준 유지
- [ ] GC 빈도/시간 증가 없음
- [ ] Thread 수 일정 수준 유지
- [ ] 응답 시간 초기 대비 10% 이내

---

## 결과 해석

### 부하 테스트 결과

#### 정상 기준
```
평균 응답시간: < 500ms
p95: < 1초
p99: < 2초
에러율: < 1%
성공률: > 99%
```

#### 경고 기준
```
평균 응답시간: 500-1000ms
p95: 1-2초
에러율: 1-5%
```

#### 위험 기준
```
평균 응답시간: > 1초
p95: > 2초
에러율: > 5%
```

---

### 패턴 비교 결과

#### Orchestration이 적합한 경우
- 금융 거래 (즉시 일관성 필수)
- 재고 정확성 중요
- 명확한 트랜잭션 경계 필요

#### Choreography가 적합한 경우
- 일반 쇼핑몰 주문 (최종 일관성 허용)
- 빠른 응답 중요
- 마이크로서비스 확장 계획

---

### 내구성 테스트 결과

#### 정상 패턴
```
JVM Heap: ──────── (일정)
GC 빈도: ──────── (일정)
응답시간: ──────── (일정)
```

#### 비정상 패턴 (메모리 누수)
```
JVM Heap: ↗️↗️↗️↗️↗️ (지속 증가)
GC 빈도: ↗️↗️↗️↗️↗️ (증가)
응답시간: ↗️↗️↗️↗️↗️ (저하)
```

**조치 사항**:
1. Heap dump 수집
2. 메모리 프로파일링
3. 리소스 누수 지점 파악
4. 코드 수정

---

## 문제 해결

### 문제 1: 높은 에러율 (> 5%)

**증상**:
```
http_req_failed: rate=10%
order_error_rate: rate=8%
```

**원인**:
- 재고 부족
- DB 커넥션 풀 고갈
- Kafka 처리 지연

**해결**:
```bash
# 재고 확인
docker exec ecommerce-mysql mysql -u ecommerce_user -p -e \
  "SELECT id, name, stock FROM ecommerce.products WHERE stock < 10;"

# DB 커넥션 풀 확인 (Pinpoint)
# - Active connections
# - Idle connections
# - Wait time

# Kafka consumer lag 확인
docker exec ecommerce-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group ecommerce-order-group \
  --describe
```

---

### 문제 2: 느린 응답 시간

**증상**:
```
http_req_duration p95: 3000ms
http_req_duration p99: 5000ms
```

**원인**:
- DB 쿼리 느림
- Redis 미적중
- Kafka 전송 지연

**해결**:
```sql
-- Slow Query 확인
SELECT * FROM performance_schema.events_statements_summary_by_digest
ORDER BY sum_timer_wait DESC LIMIT 10;

-- 인덱스 확인
SHOW INDEX FROM orders;
SHOW INDEX FROM products;
```

---

### 문제 3: 메모리 누수

**증상**:
- JVM Heap 지속 증가
- GC 빈도 증가
- Full GC 발생

**해결**:
```bash
# Heap dump 수집
docker exec ecommerce-app jmap -dump:live,format=b,file=/tmp/heap.hprof <PID>

# 분석 도구
# - Eclipse MAT
# - VisualVM
# - JProfiler
```

**일반적인 원인**:
- ThreadLocal 미정리
- 이벤트 리스너 미해제
- 캐시 무제한 증가
- DB 커넥션 누수

---

## 권장 테스트 주기

| 테스트 유형 | 주기 | 소요 시간 | 목적 |
|-----------|------|---------|------|
| 부하 테스트 | 주 1회 | 17분 | 정기 성능 검증 |
| 패턴 비교 | 필요 시 | 3분 | 아키텍처 결정 |
| 내구성 테스트 | 월 1회 | 2시간 | 안정성 검증 |

**배포 전 필수**:
- ✅ 부하 테스트 (회귀 검증)

**대규모 이벤트 전 필수**:
- ✅ 부하 테스트
- ✅ 내구성 테스트

---

## 다음 단계

테스트 완료 후:
1. 결과를 `TEST_RESULT_TEMPLATE.md`를 사용하여 문서화
2. 성능 개선 필요 항목 식별
3. 병목 지점 분석 및 개선
4. 재테스트로 개선 효과 검증

**문서 위치**:
- 테스트 계획: `k6-tests/LOAD_TEST_PLAN.md`
- 실행 체크리스트: `k6-tests/EXECUTION_CHECKLIST.md`
- 결과 템플릿: `k6-tests/TEST_RESULT_TEMPLATE.md`

---

**작성일**: 2025년 12월 25일
**버전**: 1.0
