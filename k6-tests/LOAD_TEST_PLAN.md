# 이커머스 시스템 부하 테스트 계획서

## 문서 정보
- **작성일**: 2025-12-24
- **버전**: 1.0
- **도구**: k6 (Grafana k6)
- **프로젝트**: 이커머스 플랫폼

---

## 1. 개요

### 1.1 테스트 목적

본 부하 테스트는 다음 목적을 달성하기 위해 수행됩니다:

1. **동시성 처리 검증**: Redis + Kafka 기반 아키텍처의 동시 요청 처리 능력 확인
2. **데이터 정합성 보장**: 재고 초과 발급, 중복 발급 등 비즈니스 로직 오류 방지 검증
3. **성능 지표 수립**: 응답 시간, 처리량(TPS), 에러율 등 성능 기준선 설정
4. **시스템 한계 파악**: 트래픽 증가 시 병목 지점 및 장애 포인트 식별
5. **실전 대비**: 실제 이벤트 상황(선착순 쿠폰, 한정 수량 판매)에 대한 시스템 안정성 확보

### 1.2 테스트 범위

**테스트 대상 시스템**:
- Spring Boot 애플리케이션 (포트: 8080)
- MySQL 8.0 (트랜잭션 처리)
- Redis 7 (캐싱, 분산 락, 재고 관리)
- Kafka (비동기 이벤트 처리)

**제외 사항**:
- 프론트엔드 UI 테스트
- 외부 결제 게이트웨이 연동
- 이메일/SMS 발송 기능

---

## 2. 테스트 대상 API 선정

프로젝트의 핵심 비즈니스 로직과 트래픽 집중도를 고려하여 다음 API들을 선정했습니다.

### 2.1 우선순위 1: 선착순 쿠폰 발급 API ⭐⭐⭐

**엔드포인트**: `POST /api/coupons/{couponId}/issue-fcfs/{publicId}`

**선정 이유**:
1. **동시성 이슈 핵심**: 수백 명이 동시에 접근하는 대표적인 경쟁 조건(race condition) 시나리오
2. **재고 관리 검증**: 쿠폰 100개 제한 시 정확히 100건만 발급되어야 함
3. **중복 방지 검증**: 같은 사용자가 여러 번 요청해도 1건만 발급
4. **아키텍처 검증**: Redis(빠른 검증) + Kafka(비동기 처리) 조합의 효과 측정

**비즈니스 임팩트**: 높음 (실제 이벤트 시 고객 불만, 매출 손실 직결)

**기술적 특징**:
- Redis `setIfAbsent`로 중복 체크
- Redis `decrement`로 재고 차감
- Kafka 이벤트 발행 (비동기)
- 멱등성 키로 중복 처리 방지

### 2.2 우선순위 2: 대기열 진입 API ⭐⭐

**엔드포인트**: `POST /api/queue/{queueId}/enter/{publicId}`

**선정 이유**:
1. **Rate Limiting 검증**: 초당 1000건 제한이 정확히 동작하는지 확인
2. **순서 보장 검증**: 대기 번호가 순차적으로 발급되는지 확인
3. **부하 분산**: Kafka 파티션 20개로 처리량 향상 검증

**비즈니스 임팩트**: 중간 (티켓팅, 한정판 판매 등에 활용)

### 2.3 우선순위 3: 주문 생성 API (패턴 비교) ⭐⭐

**엔드포인트**:
- `POST /api/orders/orchestration/{publicId}` (동기)
- `POST /api/orders/choreography/{publicId}` (비동기)

**선정 이유**:
1. **패턴 비교**: Orchestration vs Choreography 성능 차이 측정
2. **복합 트랜잭션**: 재고 확인 → 결제 → 쿠폰 사용의 연속 처리 검증
3. **실 사용 시나리오**: 실제 고객이 가장 많이 사용하는 기능

**비즈니스 임팩트**: 높음 (매출 직결)

### 2.4 우선순위 4: 인기 상품 조회 API ⭐

**엔드포인트**: `GET /api/products/popular?days=7`

**선정 이유**:
1. **캐싱 효과 검증**: Redis 캐시 히트율 및 성능 향상 측정
2. **읽기 부하**: 높은 읽기 트래픽 처리 능력 확인

**비즈니스 임팩트**: 낮음 (조회 성능은 중요하지만 데이터 정합성 이슈 없음)

---

## 3. 테스트 시나리오 설계

### 3.1 선착순 쿠폰 발급 테스트

#### 시나리오 1: 간단 동시성 테스트 (coupon-fcfs-simple.js)

**목표**: 빠른 동작 확인 및 기본 동시성 검증

**설정**:
```javascript
VUs: 100명
Duration: 10초
사용자 ID: 순차 (1~100)
```

**예상 동작**:
- 첫 요청: 100명이 동시 요청 → 성공 100건
- 이후 요청: 같은 사용자 반복 요청 → 중복 409 응답

**검증 항목**:
- ✅ 성공 건수 ≤ 재고 수
- ✅ 중복 발급 = 0
- ✅ 평균 응답 시간 < 500ms

#### 시나리오 2: 전체 시나리오 테스트 (coupon-fcfs-concurrency.js)

4단계로 구성된 종합 테스트:

**Stage 1: Smoke Test (0~30초)**
```
VUs: 10명
목적: 기본 동작 확인, 버그 조기 발견
검증: API 정상 동작, 200/202 응답
```

**Stage 2: Load Test (35초~2분35초)**
```
VUs: 0 → 50 → 100 → 0 (점진적 증가/감소)
목적: 정상 운영 부하에서의 성능 측정
검증:
  - 응답 시간 p95 < 1초
  - 에러율 < 5%
  - 재고 관리 정확성
```

**Stage 3: Stress Test (3분~8분)**
```
VUs: 100 → 200 → 300 → 0
목적: 시스템 한계 파악
검증:
  - 어느 VU 수에서 성능 저하 시작?
  - Redis/Kafka 병목 발생 여부
  - 에러 발생 패턴 분석
```

**Stage 4: Spike Test (8분~8분50초)**
```
VUs: 0 → 500 (10초 만에 급증) → 500 유지 → 0
목적: 실제 선착순 이벤트 시뮬레이션
검증:
  - 급격한 트래픽 증가 대응 능력
  - Circuit Breaker 동작 여부
  - 재고 정합성 유지 (500명 요청 → 100건만 성공)
```

### 3.2 대기열 시스템 테스트 (TODO)

**목표**: Rate Limiting 및 순서 보장 검증

**설정**:
```
초당 요청 수: 2000건 (제한: 1000건/초)
Duration: 1분
```

**검증**:
- Rate Limiting이 정확히 1000 TPS로 제한하는지
- 대기 번호가 중복 없이 순차 발급되는지

### 3.3 주문 생성 패턴 비교 테스트 (TODO)

**목표**: Orchestration vs Choreography 성능 비교

**설정**:
```
각 패턴별로:
  - VUs: 50명
  - Duration: 2분
  - 동일한 상품/쿠폰 조건
```

**비교 지표**:
| 지표 | Orchestration | Choreography |
|------|---------------|--------------|
| 평균 응답 시간 | ? | ? |
| p95 응답 시간 | ? | ? |
| 에러율 | ? | ? |
| TPS | ? | ? |

---

## 4. 성공 기준 및 메트릭

### 4.1 기능적 요구사항 (Must Pass)

| 항목 | 기준 | 중요도 |
|------|------|--------|
| **재고 초과 발급 방지** | 성공 건수 ≤ 재고 수 | Critical |
| **중복 발급 방지** | 중복 발급 = 0건 | Critical |
| **에러 처리** | 재고 소진 후 400 응답 | High |
| **멱등성** | 같은 요청 재시도 시 같은 결과 | High |

### 4.2 성능 요구사항 (SLA)

| 메트릭 | 목표 | 허용 | 실패 |
|--------|------|------|------|
| **평균 응답 시간** | < 200ms | < 500ms | > 1000ms |
| **p95 응답 시간** | < 500ms | < 1000ms | > 2000ms |
| **p99 응답 시간** | < 1000ms | < 2000ms | > 3000ms |
| **에러율** | < 1% | < 5% | > 10% |
| **처리량 (TPS)** | > 100 | > 50 | < 50 |

### 4.3 k6 Threshold 설정

```javascript
thresholds: {
    http_req_failed: ['rate<0.05'],           // 실패율 < 5%
    http_req_duration: ['p(95)<1000'],        // p95 < 1초
    http_req_duration: ['p(99)<2000'],        // p99 < 2초

    // 커스텀 메트릭
    coupon_issue_success: ['count<=100'],     // 재고만큼만 성공
    coupon_issue_duplicate: ['count==0'],     // 중복 0건
}
```

---

## 5. 테스트 환경

### 5.1 인프라 구성

```
┌─────────────────────────────────────────┐
│  k6 Load Generator (로컬)               │
│  - VUs: 최대 500                        │
│  - 시나리오: JavaScript                 │
└─────────────┬───────────────────────────┘
              │ HTTP Requests
              ▼
┌─────────────────────────────────────────┐
│  Spring Boot Application                │
│  - Port: 8080                           │
│  - JVM Heap: 2GB                        │
│  - Connection Pool: 50                  │
└──┬──────────┬──────────────┬────────────┘
   │          │              │
   ▼          ▼              ▼
┌──────┐  ┌──────┐      ┌──────────┐
│MySQL │  │Redis │      │  Kafka   │
│ 8.0  │  │  7   │      │(Zookeeper│
└──────┘  └──────┘      └──────────┘
```

### 5.2 시스템 사양

**애플리케이션 서버**:
- CPU: 미정 (실제 환경에 맞게 기록)
- Memory: 미정
- OS: Windows / Linux

**데이터베이스**:
- MySQL 8.0 (Docker)
- Redis 7 (Docker)
- Kafka (Docker)

### 5.3 네트워크

- **로컬 테스트**: localhost (지연 시간 < 1ms)
- **실제 환경**: (추후 기록)

---

## 6. 테스트 데이터 준비

### 6.1 필수 데이터

| 데이터 | 수량 | 생성 방법 |
|--------|------|-----------|
| 테스트 쿠폰 | 재고 100개 | SQL 스크립트 |
| 테스트 사용자 | 1000명 | SQL 스크립트 |
| 테스트 상품 | 10개 | 기존 데이터 활용 |

### 6.2 Redis 초기화

```bash
# 쿠폰 재고 설정
SET coupon:stock:{couponId} 100

# 기존 발급 기록 삭제 (재테스트 시)
DEL coupon:issued:{couponId}:*
```

### 6.3 데이터 정리 스크립트

테스트 완료 후 데이터 정리:
```sql
-- 테스트 쿠폰 발급 기록 삭제
DELETE FROM user_coupons WHERE coupon_id IN (
  SELECT id FROM coupons WHERE name LIKE 'k6 테스트%'
);

-- 발급 카운트 초기화
UPDATE coupons SET issued_quantity = 0
WHERE name LIKE 'k6 테스트%';
```

---

## 7. 테스트 실행 계획

### 7.1 실행 순서

1. **사전 준비 (30분)**
   - [ ] 인프라 실행 (Docker Compose)
   - [ ] 애플리케이션 서버 시작
   - [ ] 테스트 데이터 생성 (SQL)
   - [ ] Redis 재고 설정

2. **Smoke Test (5분)**
   - [ ] 간단 테스트 실행 (`coupon-fcfs-simple.js`)
   - [ ] 결과 확인: 기본 동작 정상?
   - [ ] 문제 발견 시 수정 후 재시작

3. **Load Test (10분)**
   - [ ] 전체 시나리오 실행 시작
   - [ ] 실시간 모니터링
   - [ ] 로그 확인

4. **결과 분석 (30분)**
   - [ ] 메트릭 분석
   - [ ] 로그 분석
   - [ ] 병목 지점 식별

5. **보고서 작성 (1시간)**
   - [ ] 테스트 결과 정리
   - [ ] 개선 사항 도출
   - [ ] 다음 테스트 계획

### 7.2 실행 명령어

```bash
# 1. 간단 테스트
cd k6-tests
k6 run scenarios/coupon-fcfs-simple.js

# 2. 전체 시나리오 (약 9분)
k6 run scenarios/coupon-fcfs-concurrency.js

# 3. 결과를 파일로 저장
k6 run --out json=results/test-$(date +%Y%m%d-%H%M%S).json \
  scenarios/coupon-fcfs-simple.js

# 4. 커스텀 설정
k6 run -e COUPON_ID=2 -e USER_ID_START=101 \
  scenarios/coupon-fcfs-simple.js
```

---

## 8. 모니터링 및 관찰 항목

### 8.1 애플리케이션 메트릭

- **JVM 메모리**: Heap 사용률, GC 빈도
- **Thread Pool**: Active threads, Queue size
- **DB Connection Pool**: Active connections, Wait time

### 8.2 인프라 메트릭

**Redis**:
- Commands/sec
- Connected clients
- Memory usage
- Cache hit ratio

**Kafka**:
- Producer throughput
- Consumer lag
- Partition count

**MySQL**:
- Slow queries
- Lock wait time
- Connection count

### 8.3 k6 출력 메트릭

```
http_reqs................: 총 요청 수
http_req_duration........: 응답 시간 (avg, p95, p99)
http_req_failed..........: 실패율
http_req_waiting.........: 서버 처리 시간
http_req_connecting......: 연결 시간

coupon_issue_success.....: 쿠폰 발급 성공
coupon_issue_duplicate...: 중복 발급 시도
coupon_issue_stock_out...: 재고 소진
```

---

## 9. 위험 요소 및 대응 방안

| 위험 요소 | 영향도 | 확률 | 대응 방안 |
|-----------|--------|------|-----------|
| 재고 초과 발급 | Critical | 중 | Redis 동시성 제어 검증, 트랜잭션 격리 수준 확인 |
| Redis 메모리 부족 | High | 낮음 | Maxmemory policy 설정, 모니터링 |
| Kafka Consumer Lag | Medium | 중 | Consumer 수 증가, 처리 성능 개선 |
| DB Connection Pool 고갈 | High | 중 | Pool size 조정, Connection timeout 설정 |
| 네트워크 지연 | Low | 낮음 | 로컬 테스트로 영향 최소화 |

---

## 10. 테스트 결과 분석 기준

### 10.1 성공 조건

다음 모든 조건을 만족해야 성공으로 판단:

1. ✅ **기능 검증**
   - 재고 초과 발급 = 0건
   - 중복 발급 = 0건
   - 에러 응답 정확 (400, 409)

2. ✅ **성능 검증**
   - p95 응답 시간 < 1초
   - 에러율 < 5%
   - 처리량 > 50 TPS

3. ✅ **안정성 검증**
   - 서버 크래시 없음
   - 메모리 누수 없음
   - DB 데드락 없음

### 10.2 분석 리포트 구성

테스트 완료 후 다음 내용을 포함한 리포트 작성:

1. **요약**
   - 테스트 일시, 환경, 시나리오
   - Pass/Fail 여부
   - 주요 발견 사항

2. **상세 메트릭**
   - 응답 시간 분포 그래프
   - 에러율 추이
   - TPS 변화

3. **병목 분석**
   - 가장 느린 구간
   - 리소스 사용률
   - 개선 제안

4. **비즈니스 검증**
   - 재고 관리 정확성
   - 중복 방지 효과
   - 데이터 정합성

---

## 11. 다음 단계 (Roadmap)

### Phase 1: 선착순 쿠폰 (현재)
- [x] 테스트 스크립트 작성
- [ ] 테스트 실행
- [ ] 결과 분석
- [ ] 개선 사항 적용

### Phase 2: 대기열 시스템
- [ ] 시나리오 설계
- [ ] 스크립트 작성
- [ ] Rate Limiting 검증

### Phase 3: 주문 시스템
- [ ] Orchestration vs Choreography 비교
- [ ] 복합 트랜잭션 테스트
- [ ] 보상 트랜잭션 검증

### Phase 4: 통합 시나리오
- [ ] 실제 사용자 플로우 시뮬레이션
- [ ] 혼합 워크로드 테스트
- [ ] 장기 실행 테스트 (Soak Test)

---

## 12. 참고 자료

### 12.1 프로젝트 문서
- `README.md`: k6 사용 가이드
- `QUICKSTART.md`: 빠른 시작
- `setup-test-data.sql`: 테스트 데이터 생성 스크립트

### 12.2 외부 자료
- [k6 공식 문서](https://k6.io/docs/)
- [k6 성능 테스트 베스트 프랙티스](https://k6.io/docs/testing-guides/test-types/)
- [Grafana Cloud k6](https://grafana.com/products/cloud/k6/)

### 12.3 관련 기술
- Redis 동시성 제어: `SET NX`, `DECR`
- Kafka 멱등성: Idempotent Producer
- JPA 낙관적 락: `@Version`

---

## 부록 A: 용어 정의

| 용어 | 설명 |
|------|------|
| **VU (Virtual User)** | k6에서 가상 사용자 |
| **TPS (Transactions Per Second)** | 초당 트랜잭션 처리 수 |
| **p95, p99** | 95%, 99% 백분위수 응답 시간 |
| **Smoke Test** | 소규모 부하로 기본 동작 확인 |
| **Load Test** | 정상 운영 부하 테스트 |
| **Stress Test** | 한계 파악 테스트 |
| **Spike Test** | 급격한 부하 증가 테스트 |
| **Soak Test** | 장기간 안정성 테스트 |

---

## 부록 B: 체크리스트

### 테스트 시작 전
- [ ] Docker Compose로 인프라 실행
- [ ] 애플리케이션 서버 정상 실행 확인
- [ ] MySQL에 테스트 데이터 생성
- [ ] Redis에 재고 설정
- [ ] Kafka 토픽 생성 확인
- [ ] k6 설치 확인 (`k6 version`)

### 테스트 실행 중
- [ ] k6 실시간 출력 모니터링
- [ ] 애플리케이션 로그 확인
- [ ] Redis 메모리 사용률 확인
- [ ] Kafka Consumer Lag 확인

### 테스트 완료 후
- [ ] 결과 JSON 파일 저장
- [ ] DB에서 발급 내역 확인
- [ ] 중복 발급 여부 쿼리로 검증
- [ ] 로그에서 에러 확인
- [ ] 테스트 데이터 정리

---

**문서 승인**:
- 작성자: Claude Code
- 검토자: (검토자 이름)
- 승인일: (승인 날짜)
