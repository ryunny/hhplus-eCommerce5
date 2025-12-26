# k6 부하 테스트 결과 보고서

## 테스트 정보

| 항목 | 내용 |
|------|------|
| **테스트 일시** | YYYY-MM-DD HH:mm:ss |
| **테스트 시나리오** | coupon-fcfs-simple / coupon-fcfs-concurrency |
| **테스트 수행자** | (이름) |
| **테스트 환경** | Local / Staging / Production |
| **테스트 목적** | (예: 선착순 쿠폰 발급 동시성 검증) |

## 테스트 설정

### 애플리케이션 설정
```yaml
Server URL: http://localhost:8081
Coupon ID: 1
Coupon Stock: 100
User ID Range: 1 ~ 100
```

### 부하 설정
```yaml
Virtual Users: 100
Duration: 10s
Scenario: constant-vus / ramping-vus
```

---

## 테스트 결과

### 1. 전체 요약

| 메트릭 | 값 | 목표 | 결과 |
|--------|-----|------|------|
| **총 요청 수** | 예) 2,000 | - | - |
| **성공 (202)** | 예) 100 | ≤ 100 | ✅ Pass |
| **중복 (409)** | 예) 1,500 | = 0 | ❌ Fail |
| **재고 소진 (400)** | 예) 400 | - | - |
| **기타 실패** | 예) 0 | - | - |
| **평균 응답 시간** | 예) 156ms | < 500ms | ✅ Pass |
| **p95 응답 시간** | 예) 342ms | < 1000ms | ✅ Pass |
| **p99 응답 시간** | 예) 580ms | < 2000ms | ✅ Pass |
| **에러율** | 예) 0% | < 5% | ✅ Pass |

### 2. k6 메트릭 상세

#### HTTP 요청 메트릭
```
✓ http_reqs................: 2000 (200/s)
✓ http_req_duration........: avg=156ms p95=342ms p99=580ms
✓ http_req_waiting.........: avg=150ms p95=335ms p99=570ms
✓ http_req_connecting......: avg=2ms
✓ http_req_failed..........: 0.00%
```

#### 커스텀 비즈니스 메트릭
```
✓ coupon_issue_success.....: 100 (재고만큼만 성공)
✗ coupon_issue_duplicate...: 1500 (중복 발급 시도 발생)
✓ coupon_issue_stock_out...: 400 (재고 소진 후 정상 차단)
✓ coupon_issue_failure.....: 0 (예상치 못한 실패 없음)
```

#### Threshold 검증
```
✅ http_req_failed rate.....: < 5% 목표 달성
✅ http_req_duration p(95)..: < 1000ms 목표 달성
✅ http_req_duration p(99)..: < 2000ms 목표 달성
```

---

## 비즈니스 로직 검증

### 1. 재고 초과 발급 방지 ✅ / ❌

**검증 방법**:
```sql
SELECT
    c.id,
    c.total_quantity,
    c.issued_quantity,
    COUNT(uc.id) AS actual_issued
FROM coupons c
LEFT JOIN user_coupons uc ON c.id = uc.coupon_id
WHERE c.id = 1
GROUP BY c.id;
```

**결과**:
| 쿠폰 ID | 총 재고 | DB 발급 카운트 | 실제 발급 건수 |
|---------|---------|----------------|----------------|
| 1 | 100 | 100 | 100 |

**판정**: ✅ 재고 초과 발급 없음 / ❌ 재고 초과 발급 발생 (XXX건)

### 2. 중복 발급 방지 ✅ / ❌

**검증 방법**:
```sql
SELECT
    user_id,
    COUNT(*) AS duplicate_count
FROM user_coupons
WHERE coupon_id = 1
GROUP BY user_id
HAVING COUNT(*) > 1;
```

**결과**:
- 중복 발급 사용자: X명
- 총 중복 건수: X건

**판정**: ✅ 중복 발급 없음 / ❌ 중복 발급 발생

### 3. 에러 응답 정확성 ✅ / ❌

| 상황 | 예상 응답 | 실제 응답 | 판정 |
|------|-----------|-----------|------|
| 정상 발급 | 202 Accepted | 202 | ✅ |
| 재고 소진 | 400 Bad Request | 400 | ✅ |
| 중복 발급 | 409 Conflict | 409 | ✅ |

---

## 성능 분석

### 1. 응답 시간 분포

```
Min: 85ms
Avg: 156ms
Med: 148ms
Max: 890ms
p90: 280ms
p95: 342ms
p99: 580ms
```

**분석**:
- 대부분 요청이 200ms 이내 처리
- p99도 1초 이하로 우수한 성능
- 최대 응답 시간 890ms는 초기 연결 지연으로 추정

### 2. 처리량 (Throughput)

```
총 요청: 2,000건
Duration: 10초
평균 TPS: 200 req/s
최대 TPS: 350 req/s (추정)
```

**분석**:
- 초당 200건 처리 가능
- 목표 TPS (50) 대비 400% 달성

### 3. 시간대별 추이

| 시간 | 요청 수 | 평균 응답 시간 | 에러율 |
|------|---------|----------------|--------|
| 0-2초 | 400 | 120ms | 0% |
| 2-4초 | 400 | 150ms | 0% |
| 4-6초 | 400 | 160ms | 0% |
| 6-8초 | 400 | 170ms | 0% |
| 8-10초 | 400 | 180ms | 0% |

**분석**:
- 시간이 지날수록 응답 시간 소폭 증가 (캐시 효과 감소)
- 안정적인 성능 유지

---

## 인프라 메트릭 (옵션)

### Redis
```
Commands/sec: 500
Connected clients: 100
Memory usage: 50MB
Cache hit ratio: 98%
```

### Kafka
```
Producer throughput: 200 msg/s
Consumer lag: < 10 messages
Partitions: 10
```

### MySQL
```
Active connections: 20
Slow queries: 0
Lock wait time: 0ms
```

### JVM (애플리케이션)
```
Heap usage: 1.2GB / 2GB
GC count: 3
GC time: 50ms
Thread count: 50
```

---

## 발견된 이슈

### 이슈 1: (제목)
**심각도**: Critical / High / Medium / Low

**현상**:
- (문제 상황 설명)

**재현 방법**:
1. (재현 단계)
2. ...

**원인 분석**:
- (원인 추정)

**해결 방안**:
- [ ] (조치 항목 1)
- [ ] (조치 항목 2)

### 이슈 2: (제목)
...

---

## 개선 사항

### 성능 개선
1. **Redis Connection Pool 증가**
   - 현재: 10
   - 제안: 50
   - 예상 효과: 응답 시간 20% 감소

2. **Kafka Consumer 수 증가**
   - 현재: 10
   - 제안: 20
   - 예상 효과: Consumer Lag 감소

### 코드 개선
1. **중복 체크 로직 개선**
   - 문제: Redis + DB 이중 체크로 인한 지연
   - 제안: Redis만으로 1차 차단, DB는 비동기 검증

2. **에러 처리 개선**
   - 문제: 일부 에러 메시지 불명확
   - 제안: 구체적인 에러 메시지 제공

---

## 결론

### 테스트 통과 여부
**✅ PASS** / **❌ FAIL** / **⚠️ CONDITIONAL PASS**

### 요약
- (테스트 결과 한 줄 요약)

### 주요 성과
1. 재고 초과 발급 방지 검증 완료
2. p95 응답 시간 342ms로 목표(1000ms) 대비 우수
3. 200 TPS 처리 능력 확인

### 해결 필요 사항
1. 중복 발급 체크 로직 개선 필요
2. 에러 메시지 명확화

### 다음 단계
- [ ] 발견된 이슈 수정
- [ ] 재테스트 수행
- [ ] 대기열 시스템 테스트 진행

---

## 첨부 파일

- [ ] k6 결과 JSON: `results/test-YYYYMMDD-HHMMSS.json`
- [ ] 애플리케이션 로그: `app-YYYYMMDD.log`
- [ ] DB 검증 쿼리 결과: `db-validation.txt`
- [ ] 스크린샷: (k6 출력, 모니터링 대시보드 등)

---

**보고서 작성**: (작성자 이름)
**검토**: (검토자 이름)
**승인**: (승인자 이름)
