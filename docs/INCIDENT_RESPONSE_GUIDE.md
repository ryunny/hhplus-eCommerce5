# 장애 대응 가이드

## 📋 목차
1. [개요](#개요)
2. [장애 등급 정의](#장애-등급-정의)
3. [장애 대응 체계](#장애-대응-체계)
4. [장애 시나리오별 대응](#장애-시나리오별-대응)
5. [모니터링 및 알림](#모니터링-및-알림)
6. [장애 대응 체크리스트](#장애-대응-체크-리스트)
7. [사후 분석 (Post-Mortem)](#사후-분석-post-mortem)
8. [연락처 및 에스컬레이션](#연락처-및-에스컬레이션)

---

## 개요

### 문서 목적
본 문서는 E-Commerce 쿠폰 발급 시스템의 장애 발생 시 신속하고 체계적인 대응을 위한 가이드입니다.

### 적용 범위
- Spring Boot 애플리케이션
- MySQL 데이터베이스
- Redis 캐시
- Kafka 메시지 브로커
- Pinpoint APM

### 주요 원칙
1. **신속한 감지**: 장애를 빠르게 인지
2. **우선순위 판단**: 비즈니스 영향도 기반 대응
3. **체계적 대응**: 단계별 절차 준수
4. **투명한 소통**: 관련자 즉시 공유
5. **철저한 기록**: 모든 조치 사항 문서화

---

## 장애 등급 정의

### P0 - Critical (치명적)
**정의**: 서비스 전체 중단 또는 핵심 기능 불가

**예시**:
- 애플리케이션 전체 다운
- 데이터베이스 접근 불가
- 데이터 손실 또는 손상
- 보안 침해

**대응 시간**:
- 감지: 즉시 (1분 이내)
- 대응 시작: 5분 이내
- 해결 목표: 1시간 이내

**에스컬레이션**: 즉시 CTO/VP Engineering

---

### P1 - High (높음)
**정의**: 주요 기능 저하, 다수 사용자 영향

**예시**:
- 쿠폰 발급 기능 중단
- 응답 시간 10배 이상 증가
- Redis 전체 장애
- Kafka 메시지 유실

**대응 시간**:
- 감지: 5분 이내
- 대응 시작: 15분 이내
- 해결 목표: 4시간 이내

**에스컬레이션**: 30분 내 미해결 시 상위 보고

---

### P2 - Medium (중간)
**정의**: 부분적 기능 저하, 일부 사용자 영향

**예시**:
- 특정 API 응답 지연
- 캐시 히트율 감소
- 데이터베이스 슬로우 쿼리
- Kafka Consumer Lag 증가

**대응 시간**:
- 감지: 15분 이내
- 대응 시작: 1시간 이내
- 해결 목표: 1일 이내

**에스컬레이션**: 4시간 내 미해결 시 상위 보고

---

### P3 - Low (낮음)
**정의**: 사용자 영향 없음, 내부 문제

**예시**:
- 로그 경고 메시지
- 리소스 사용률 증가
- 비핵심 기능 오류

**대응 시간**:
- 감지: 1시간 이내
- 대응 시작: 1일 이내
- 해결 목표: 1주일 이내

**에스컬레이션**: 필요시

---

## 장애 대응 체계

### 대응 프로세스
```
┌──────────────┐
│ 1. 장애 감지  │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ 2. 등급 판단  │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ 3. 초동 조치  │ ← 현상 완화 (Mitigation)
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ 4. 원인 분석  │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ 5. 근본 해결  │
└──────┬───────┘
       │
       ▼
┌──────────────┐
│ 6. 사후 분석  │
└──────────────┘
```

### 역할 및 책임

#### Incident Commander (장애 대응 책임자)
- 장애 대응 총괄
- 우선순위 결정
- 에스컬레이션 판단
- 이해관계자 커뮤니케이션

#### On-Call Engineer (당직 엔지니어)
- 장애 1차 대응
- 초동 조치 실행
- 로그 및 메트릭 수집
- 상황 보고

#### Subject Matter Expert (SME)
- 특정 영역 전문가
- 심층 분석 및 해결
- 기술적 조언 제공

---

## 장애 시나리오별 대응

### 시나리오 1: Redis 장애

#### 증상
```
✗ Redis 연결 불가
✗ Cache GET/PUT 실패
✗ 응답 시간 급증 (캐시 미스로 인한 DB 부하)
✗ 로그: "Cache GET failed (fallback to DB)"
```

#### 영향도
- **비즈니스 영향**: 중간 (성능 저하, 서비스는 지속)
- **장애 등급**: P2 (캐시 무효화) ~ P1 (전체 장애)

#### 감지 방법
1. Pinpoint APM 알림
   - Redis 연결 실패
   - 응답 시간 임계값 초과
2. 애플리케이션 로그
   - `Cache GET failed` 대량 발생
3. Redis 모니터링
   - Container down
   - Memory 부족

#### 초동 조치 (5분 이내)

**Step 1: 상황 파악**
```bash
# Redis 컨테이너 상태 확인
docker ps | grep redis

# Redis 로그 확인
docker logs ecommerce-redis --tail 100

# Redis 연결 테스트
docker exec ecommerce-redis redis-cli ping
```

**Step 2: 임시 조치**
```bash
# Case 1: 컨테이너 중지 → 재시작
docker restart ecommerce-redis

# Case 2: 메모리 부족 → 캐시 일부 삭제
docker exec ecommerce-redis redis-cli --scan --pattern "cache:*" | head -1000 | xargs docker exec ecommerce-redis redis-cli DEL

# Case 3: 설정 문제 → 재배포
docker-compose up -d redis
```

**Step 3: 모니터링**
```bash
# Redis 상태 지속 확인
watch -n 1 'docker exec ecommerce-redis redis-cli ping'

# 애플리케이션 로그 모니터링
docker logs -f ecommerce-app | grep -i "redis\|cache"
```

#### 근본 원인 분석

**가능한 원인**:
1. **메모리 부족**
   - 진단: `INFO memory` 확인
   - 해결: maxmemory 증가, eviction policy 조정

2. **네트워크 단절**
   - 진단: Docker 네트워크 확인
   - 해결: 네트워크 재생성

3. **설정 오류**
   - 진단: redis.conf 검토
   - 해결: 설정 수정 및 재시작

4. **디스크 I/O 병목**
   - 진단: AOF/RDB 파일 확인
   - 해결: Persistence 설정 최적화

#### 복구 절차

```bash
# 1. Redis 데이터 백업 (가능한 경우)
docker exec ecommerce-redis redis-cli BGSAVE

# 2. 설정 수정
# docker-compose.yml 또는 redis.conf 수정

# 3. Redis 재시작
docker-compose restart redis

# 4. 데이터 복구 (필요시)
docker exec ecommerce-redis redis-cli --rdb /data/dump.rdb

# 5. 캐시 워밍 (필요시)
curl http://localhost:8081/api/coupons/issuable
```

#### 예방 조치

1. **리소스 모니터링**
   ```yaml
   # docker-compose.yml
   redis:
     deploy:
       resources:
         limits:
           memory: 512M
         reservations:
           memory: 256M
   ```

2. **Persistence 최적화**
   ```conf
   # redis.conf
   save 900 1
   save 300 10
   save 60 10000
   maxmemory 256mb
   maxmemory-policy allkeys-lru
   ```

3. **헬스체크 강화**
   ```yaml
   healthcheck:
     test: ["CMD", "redis-cli", "ping"]
     interval: 10s
     timeout: 3s
     retries: 3
   ```

4. **백업 자동화**
   ```bash
   # Crontab
   0 */6 * * * docker exec ecommerce-redis redis-cli BGSAVE
   ```

---

### 시나리오 2: Kafka 장애

#### 증상
```
✗ Kafka 브로커 접근 불가
✗ Producer 전송 실패
✗ Consumer Lag 급증
✗ 쿠폰 발급 요청 누락
✗ 로그: "Failed to send message to Kafka"
```

#### 영향도
- **비즈니스 영향**: 높음 (쿠폰 발급 기능 마비)
- **장애 등급**: P1 (핵심 기능 중단)

#### 감지 방법
1. Kafka 모니터링
   - Broker down
   - Producer send failure rate 증가
2. 애플리케이션 로그
   - KafkaException 발생
3. Consumer Lag 모니터링
   - Lag > 1000

#### 초동 조치 (10분 이내)

**Step 1: 상황 파악**
```bash
# Kafka 컨테이너 상태
docker ps | grep kafka

# Kafka 로그
docker logs ecommerce-kafka --tail 100

# Topic 상태 확인
docker exec ecommerce-kafka kafka-topics --bootstrap-server localhost:9092 --list
docker exec ecommerce-kafka kafka-topics --bootstrap-server localhost:9092 --describe --topic coupon.issue.requested

# Consumer Group 확인
docker exec ecommerce-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group coupon-issue-service
```

**Step 2: 임시 조치**

```bash
# Case 1: Kafka 브로커 다운 → 재시작
docker restart ecommerce-kafka

# Case 2: ZooKeeper 문제 → ZooKeeper 재시작
docker restart ecommerce-zookeeper
sleep 10
docker restart ecommerce-kafka

# Case 3: 디스크 부족 → 로그 정리
docker exec ecommerce-kafka kafka-log-dirs --bootstrap-server localhost:9092 --describe
# 필요시 오래된 로그 세그먼트 삭제

# Case 4: 파티션 리더 없음 → 리더 선출
docker exec ecommerce-kafka kafka-leader-election --bootstrap-server localhost:9092 --election-type PREFERRED --all-topic-partitions
```

**Step 3: Consumer Lag 해결**
```bash
# Consumer Group Reset (주의: 데이터 유실 가능)
# 마지막 수단으로만 사용
docker exec ecommerce-kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group coupon-issue-service \
  --reset-offsets \
  --to-latest \
  --topic coupon.issue.requested \
  --execute
```

#### 근본 원인 분석

**가능한 원인**:
1. **디스크 부족**
   - 진단: `df -h` 확인
   - 해결: 로그 retention 조정, 디스크 증설

2. **ZooKeeper 연결 끊김**
   - 진단: ZooKeeper 로그 확인
   - 해결: ZooKeeper 안정화

3. **네트워크 파티션**
   - 진단: 네트워크 연결 확인
   - 해결: 네트워크 복구

4. **설정 오류**
   - 진단: server.properties 검토
   - 해결: 설정 수정

#### 복구 절차

```bash
# 1. ZooKeeper 안정화
docker-compose restart zookeeper
sleep 30

# 2. Kafka 재시작
docker-compose restart kafka
sleep 60

# 3. Topic 상태 확인
docker exec ecommerce-kafka kafka-topics --bootstrap-server localhost:9092 --describe

# 4. 애플리케이션 재시작 (Consumer 재연결)
docker-compose restart ecommerce-app

# 5. Consumer Lag 모니터링
watch -n 5 'docker exec ecommerce-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group coupon-issue-service'
```

#### 데이터 복구

**누락된 쿠폰 발급 요청 처리**:
```sql
-- 1. Redis에는 차감되었지만 DB에 미발급된 건 확인
SELECT u.public_id, c.id
FROM users u
CROSS JOIN coupons c
WHERE c.id = 11
  AND NOT EXISTS (
    SELECT 1 FROM user_coupons uc
    WHERE uc.user_id = u.id AND uc.coupon_id = c.id
  )
  -- Redis 차감 확인 필요 (Redis 로그 분석)
LIMIT 100;

-- 2. 수동 발급 (신중하게)
-- 비즈니스 팀과 협의 후 진행
```

#### 예방 조치

1. **복제 설정**
   ```yaml
   # docker-compose.yml - 프로덕션 환경
   kafka:
     environment:
       KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 3
       KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 3
       KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 2
   ```

2. **디스크 모니터링**
   ```yaml
   # docker-compose.yml
   kafka:
     deploy:
       resources:
         limits:
           memory: 2G
     volumes:
       - kafka_data:/var/lib/kafka/data:rw
   ```

3. **로그 Retention 최적화**
   ```properties
   # server.properties
   log.retention.hours=168
   log.retention.bytes=10737418240
   log.segment.bytes=1073741824
   log.cleanup.policy=delete
   ```

4. **Producer 설정 강화**
   ```properties
   # application.properties
   spring.kafka.producer.acks=1
   spring.kafka.producer.retries=3
   spring.kafka.producer.request-timeout-ms=30000
   spring.kafka.producer.delivery-timeout-ms=120000
   ```

---

### 시나리오 3: MySQL 데이터베이스 장애

#### 증상
```
✗ 데이터베이스 연결 실패
✗ 모든 API 500 에러
✗ Connection pool exhausted
✗ 로그: "Unable to acquire JDBC Connection"
```

#### 영향도
- **비즈니스 영향**: 치명적 (서비스 전체 중단)
- **장애 등급**: P0 (Critical)

#### 감지 방법
1. Pinpoint APM
   - SQL Error rate 100%
   - DB 연결 실패
2. 애플리케이션 로그
   - SQLException 대량 발생
3. MySQL 모니터링
   - Container down
   - Too many connections

#### 초동 조치 (즉시)

**Step 1: 긴급 상황 공지**
```
[CRITICAL] MySQL 데이터베이스 장애 발생
- 시각: YYYY-MM-DD HH:MM:SS
- 증상: 전체 API 응답 불가
- 조치: 즉시 복구 진행 중
- 예상 복구: 30분 이내
```

**Step 2: 상황 파악**
```bash
# MySQL 컨테이너 상태
docker ps | grep mysql

# MySQL 로그 (최근 100줄)
docker logs ecommerce-mysql --tail 100

# MySQL 프로세스 확인
docker exec ecommerce-mysql ps aux | grep mysql

# 연결 테스트
docker exec ecommerce-mysql mysql -uecommerce_user -pecommerce123 -e "SELECT 1"
```

**Step 3: 긴급 복구**

```bash
# Case 1: 컨테이너 다운 → 재시작
docker-compose up -d mysql

# Case 2: Too many connections → Connection 정리
docker exec ecommerce-mysql mysql -uroot -prootpassword -e "SHOW PROCESSLIST;"
docker exec ecommerce-mysql mysql -uroot -prootpassword -e "KILL <connection_id>;"

# Case 3: Deadlock → 트랜잭션 롤백
docker exec ecommerce-mysql mysql -uroot -prootpassword -e "SHOW ENGINE INNODB STATUS\G"

# Case 4: 디스크 부족 → 임시 공간 확보
docker exec ecommerce-mysql df -h
# 로그 파일 정리 등
```

**Step 4: 애플리케이션 재연결**
```bash
# DB 복구 후 애플리케이션 재시작
docker-compose restart ecommerce-app

# 헬스체크 확인
curl http://localhost:8081/actuator/health
```

#### 근본 원인 분석

**가능한 원인**:
1. **커넥션 풀 고갈**
   - 진단: HikariCP 메트릭 확인
   - 해결: 풀 크기 조정, 연결 누수 수정

2. **Deadlock**
   - 진단: `SHOW ENGINE INNODB STATUS`
   - 해결: 쿼리 최적화, 트랜잭션 범위 축소

3. **슬로우 쿼리**
   - 진단: Slow Query Log 분석
   - 해결: 인덱스 추가, 쿼리 튜닝

4. **디스크 I/O 병목**
   - 진단: `iostat`, `vmstat`
   - 해결: SSD 사용, 쿼리 최적화

5. **메모리 부족**
   - 진단: MySQL 메모리 설정 확인
   - 해결: Buffer Pool 크기 조정

#### 복구 절차

```bash
# 1. 백업 확인 (최악의 경우 대비)
docker exec ecommerce-mysql ls -lh /var/lib/mysql/

# 2. MySQL 설정 최적화
# /etc/mysql/my.cnf 수정
# - max_connections 증가
# - innodb_buffer_pool_size 조정

# 3. MySQL 재시작
docker-compose restart mysql

# 4. 데이터 무결성 확인
docker exec ecommerce-mysql mysqlcheck -uecommerce_user -pecommerce123 --all-databases

# 5. 슬로우 쿼리 로그 활성화 (재발 방지)
docker exec ecommerce-mysql mysql -uroot -prootpassword -e "
SET GLOBAL slow_query_log = 'ON';
SET GLOBAL long_query_time = 1;
"
```

#### 데이터 복구 (최악의 경우)

```bash
# 1. 최신 백업에서 복원
docker run --rm -v ecommerce_mysql_data:/data -v $(pwd):/backup \
  ubuntu tar xvf /backup/mysql-backup-YYYYMMDD.tar.gz -C /data

# 2. 바이너리 로그로 Point-in-Time Recovery
docker exec ecommerce-mysql mysqlbinlog /var/lib/mysql/mysql-bin.000001 \
  | docker exec -i ecommerce-mysql mysql -uroot -prootpassword ecommerce

# 3. 데이터 검증
docker exec ecommerce-mysql mysql -uecommerce_user -pecommerce123 ecommerce -e "
SELECT COUNT(*) FROM users;
SELECT COUNT(*) FROM coupons;
SELECT COUNT(*) FROM user_coupons;
"
```

#### 예방 조치

1. **커넥션 풀 최적화**
   ```properties
   # application.properties
   spring.datasource.hikari.maximum-pool-size=20
   spring.datasource.hikari.minimum-idle=10
   spring.datasource.hikari.connection-timeout=30000
   spring.datasource.hikari.idle-timeout=600000
   spring.datasource.hikari.max-lifetime=1800000
   spring.datasource.hikari.leak-detection-threshold=60000
   ```

2. **슬로우 쿼리 모니터링**
   ```sql
   SET GLOBAL slow_query_log = 'ON';
   SET GLOBAL long_query_time = 1;
   SET GLOBAL log_queries_not_using_indexes = 'ON';
   ```

3. **자동 백업**
   ```bash
   # Crontab
   0 2 * * * docker exec ecommerce-mysql mysqldump \
     -uecommerce_user -pecommerce123 ecommerce \
     | gzip > /backup/ecommerce-$(date +\%Y\%m\%d).sql.gz
   ```

4. **Read Replica 구성** (프로덕션)
   ```yaml
   # docker-compose.yml
   mysql-replica:
     image: mysql:8.0
     environment:
       MYSQL_ROOT_PASSWORD: rootpassword
       MYSQL_MASTER_SERVICE_NAME: mysql
     command: --server-id=2 --read-only=1
   ```

---

### 시나리오 4: 애플리케이션 장애

#### 증상
```
✗ Spring Boot 애플리케이션 응답 없음
✗ 모든 API 타임아웃
✗ CPU 100% 사용
✗ OutOfMemoryError
✗ 로그: "java.lang.OutOfMemoryError: Java heap space"
```

#### 영향도
- **비즈니스 영향**: 치명적 (서비스 전체 중단)
- **장애 등급**: P0 (Critical)

#### 감지 방법
1. Pinpoint APM
   - Heap Memory 95% 이상
   - GC 시간 급증
   - Active Thread 급증
2. 헬스체크 실패
3. 모든 API 타임아웃

#### 초동 조치 (즉시)

**Step 1: 상황 파악**
```bash
# 컨테이너 상태
docker ps | grep ecommerce-app
docker stats ecommerce-app

# 애플리케이션 로그
docker logs ecommerce-app --tail 200

# Heap Dump 생성 (분석용)
docker exec ecommerce-app jmap -dump:format=b,file=/tmp/heap.hprof 1
docker cp ecommerce-app:/tmp/heap.hprof ./heap-$(date +%Y%m%d-%H%M%S).hprof
```

**Step 2: 긴급 복구**
```bash
# Case 1: OOM → 재시작
docker-compose restart ecommerce-app

# Case 2: 메모리 부족 → 리소스 증가
# docker-compose.yml 수정 후
docker-compose up -d ecommerce-app

# Case 3: Thread Deadlock → 강제 재시작
docker-compose stop ecommerce-app
docker-compose start ecommerce-app
```

**Step 3: 임시 트래픽 차단 (필요시)**
```bash
# 특정 API 비활성화 (Circuit Breaker)
# 또는 로드밸런서에서 트래픽 차단
```

#### 근본 원인 분석

**가능한 원인**:
1. **메모리 누수**
   - 진단: Heap Dump 분석 (MAT, VisualVM)
   - 해결: 코드 수정

2. **무한 루프**
   - 진단: Thread Dump 분석
   - 해결: 로직 수정

3. **과도한 부하**
   - 진단: API 요청률 확인
   - 해결: Rate Limiting, Scale Out

4. **외부 의존성 장애**
   - 진단: Redis/Kafka/MySQL 상태 확인
   - 해결: Circuit Breaker, Timeout 설정

#### 복구 절차

```bash
# 1. Heap Dump 분석
# Eclipse MAT 또는 VisualVM 사용

# 2. Thread Dump 분석
docker exec ecommerce-app jstack 1 > thread-dump.txt

# 3. 설정 최적화
# docker-compose.yml
services:
  ecommerce-app:
    environment:
      - JAVA_OPTS=-Xmx1g -Xms512m -XX:+HeapDumpOnOutOfMemoryError

# 4. 재배포
docker-compose up -d --build ecommerce-app

# 5. 모니터링
watch -n 5 'docker stats ecommerce-app --no-stream'
```

#### 예방 조치

1. **JVM 튜닝**
   ```dockerfile
   # Dockerfile
   ENTRYPOINT ["java", \
       "-Xmx1g", \
       "-Xms512m", \
       "-XX:+UseG1GC", \
       "-XX:MaxGCPauseMillis=200", \
       "-XX:+HeapDumpOnOutOfMemoryError", \
       "-XX:HeapDumpPath=/tmp", \
       "-javaagent:/pinpoint-agent/pinpoint-bootstrap-2.5.4.jar", \
       "-jar", "/app/app.jar"]
   ```

2. **Circuit Breaker 적용**
   ```java
   @CircuitBreaker(name = "redis", fallbackMethod = "fallbackMethod")
   public List<Coupon> getIssuableCoupons() {
       // ...
   }
   ```

3. **Rate Limiting**
   ```java
   @RateLimiter(name = "couponIssue", fallbackMethod = "rateLimitFallback")
   public ResponseEntity<?> issueCoupon() {
       // ...
   }
   ```

4. **리소스 제한**
   ```yaml
   # docker-compose.yml
   ecommerce-app:
     deploy:
       resources:
         limits:
           memory: 2G
           cpus: '2'
         reservations:
           memory: 1G
           cpus: '1'
   ```

---

### 시나리오 5: 네트워크 장애

#### 증상
```
✗ 컨테이너 간 통신 불가
✗ "Connection refused" 에러
✗ DNS 조회 실패
✗ 특정 서비스만 접근 불가
```

#### 영향도
- **비즈니스 영향**: 높음 ~ 치명적
- **장애 등급**: P0 ~ P1

#### 감지 방법
1. 컨테이너 로그에 연결 오류
2. Ping/Telnet 실패
3. 서비스 간 통신 타임아웃

#### 초동 조치

**Step 1: 네트워크 상태 확인**
```bash
# Docker 네트워크 목록
docker network ls

# 네트워크 상세 정보
docker network inspect ecommerce_default
docker network inspect ecommerce_pinpoint

# 컨테이너 네트워크 설정 확인
docker inspect ecommerce-app | grep -A 30 Networks
```

**Step 2: 연결 테스트**
```bash
# App → MySQL
docker exec ecommerce-app ping -c 3 mysql
docker exec ecommerce-app nc -zv mysql 3306

# App → Redis
docker exec ecommerce-app ping -c 3 redis
docker exec ecommerce-app nc -zv redis 6379

# App → Kafka
docker exec ecommerce-app ping -c 3 kafka
docker exec ecommerce-app nc -zv kafka 9092

# App → Pinpoint Collector
docker exec ecommerce-app nc -zv pinpoint-collector 9991
```

**Step 3: 네트워크 복구**
```bash
# Case 1: 컨테이너 네트워크 재연결
docker network disconnect ecommerce_default ecommerce-app
docker network connect ecommerce_default ecommerce-app

# Case 2: 네트워크 재생성
docker-compose down
docker network prune -f
docker-compose up -d

# Case 3: DNS 캐시 초기화
docker exec ecommerce-app cat /etc/resolv.conf
# 필요시 컨테이너 재시작
```

#### 복구 절차

```bash
# 1. 모든 컨테이너 중지
docker-compose down

# 2. 네트워크 정리
docker network prune -f

# 3. 재시작
docker-compose up -d

# 4. 연결 확인
./scripts/check-connectivity.sh
```

#### 예방 조치

1. **네트워크 헬스체크**
   ```yaml
   # docker-compose.yml
   ecommerce-app:
     healthcheck:
       test: ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"]
       interval: 30s
       timeout: 10s
       retries: 3
   ```

2. **Retry 메커니즘**
   ```properties
   # application.properties
   spring.datasource.hikari.connection-timeout=30000
   spring.kafka.producer.request-timeout-ms=30000
   spring.data.redis.timeout=5000ms
   ```

---

### 시나리오 6: Pinpoint APM 장애

#### 증상
```
✗ Pinpoint Web UI 접속 불가
✗ HBase 연결 실패
✗ 모니터링 데이터 누락
✗ Agent → Collector 연결 끊김
```

#### 영향도
- **비즈니스 영향**: 낮음 (모니터링만 영향, 서비스는 정상)
- **장애 등급**: P2 ~ P3

#### 감지 방법
1. Pinpoint Web UI 응답 없음
2. HBase Master 프로세스 미실행
3. Agent 연결 오류 로그

#### 초동 조치

**Step 1: 구성 요소 확인**
```bash
# 모든 Pinpoint 컨테이너 상태
docker ps | grep pinpoint

# HBase Master 프로세스
docker exec pinpoint-hbase jps -l

# Collector 상태
docker logs pinpoint-collector --tail 50

# Web UI 상태
docker logs pinpoint-web --tail 50
```

**Step 2: 순차적 재시작**
```bash
# 1. HBase (데이터 저장소)
docker restart pinpoint-hbase
sleep 60

# 2. Collector (데이터 수집)
docker restart pinpoint-collector
sleep 30

# 3. Web (UI)
docker restart pinpoint-web
sleep 20

# 4. Agent 재연결을 위해 App 재시작
docker restart ecommerce-app
```

#### 복구 절차

```bash
# HBase가 시작되지 않는 경우
docker exec pinpoint-hbase /opt/hbase/bin/start-hbase.sh

# 데이터 정합성 확인
docker exec pinpoint-hbase /opt/hbase/bin/hbase hbck

# 테이블 목록 확인
docker exec pinpoint-hbase /opt/hbase/bin/hbase shell <<< "list"
```

#### 예방 조치

**모니터링과 별개로 동작**:
- Pinpoint 장애가 애플리케이션에 영향을 주지 않음
- Agent는 연결 실패 시 자동 재시도
- 비동기 전송으로 성능 영향 최소화

---

## 모니터링 및 알림

### Pinpoint APM 알림 설정

#### 1. 임계값 기반 알림

**응답 시간 알림**:
```
조건: 95% 응답시간 > 2000ms (5분 지속)
등급: P2
알림: Slack #alerts 채널
대상: On-Call Engineer
```

**에러율 알림**:
```
조건: Error Rate > 5% (1분 지속)
등급: P1
알림: SMS + Slack
대상: On-Call Engineer + Manager
```

**JVM 메모리 알림**:
```
조건: Heap 사용률 > 85% (5분 지속)
등급: P2
알림: Slack #alerts 채널
대상: On-Call Engineer
```

#### 2. 컨테이너 모니터링

**Docker 헬스체크**:
```bash
# docker-compose.yml에 추가
healthcheck:
  test: ["CMD-SHELL", "curl -f http://localhost:8080/actuator/health || exit 1"]
  interval: 30s
  timeout: 10s
  retries: 3
  start_period: 40s
```

**리소스 사용률 모니터링**:
```bash
# 스크립트: monitor-containers.sh
#!/bin/bash
while true; do
  docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}" | \
    awk 'NR>1 && ($2+0 > 80 || $3+0 > 80) {print "[WARNING]", $0}'
  sleep 60
done
```

### 로그 모니터링

**에러 패턴 감지**:
```bash
# 스크립트: monitor-errors.sh
#!/bin/bash
docker logs -f ecommerce-app | grep -E "ERROR|FATAL|OutOfMemory" | while read line; do
  echo "[$(date)] $line" | tee -a /var/log/app-errors.log
  # 알림 전송
  curl -X POST https://hooks.slack.com/... -d "{'text':'$line'}"
done
```

### 데이터베이스 모니터링

**슬로우 쿼리 감지**:
```sql
-- 1초 이상 걸리는 쿼리 로깅
SET GLOBAL slow_query_log = 'ON';
SET GLOBAL long_query_time = 1;
SET GLOBAL log_queries_not_using_indexes = 'ON';

-- 슬로우 쿼리 로그 위치
SHOW VARIABLES LIKE 'slow_query_log_file';
```

**커넥션 풀 모니터링**:
```java
// HikariCP 메트릭 노출
@Bean
public MetricRegistry metricRegistry() {
    return new MetricRegistry();
}

// application.properties
spring.datasource.hikari.metric-registry=metricRegistry
management.endpoints.web.exposure.include=health,metrics
```

---

## 장애 대응 체크리스트

### 초동 대응 (첫 5분)

- [ ] 장애 등급 판단 (P0/P1/P2/P3)
- [ ] 관련자 알림 (Slack, 전화, SMS)
- [ ] 로그 수집 시작
  ```bash
  docker logs ecommerce-app > app-$(date +%Y%m%d-%H%M%S).log
  docker logs ecommerce-mysql > mysql-$(date +%Y%m%d-%H%M%S).log
  docker logs ecommerce-redis > redis-$(date +%Y%m%d-%H%M%S).log
  docker logs ecommerce-kafka > kafka-$(date +%Y%m%d-%H%M%S).log
  ```
- [ ] Pinpoint APM 확인
- [ ] 시스템 리소스 확인
  ```bash
  docker stats --no-stream
  df -h
  free -m
  ```

### 분석 단계 (5-15분)

- [ ] 에러 로그 분석
- [ ] 메트릭 확인 (CPU, Memory, Disk, Network)
- [ ] 최근 변경사항 확인 (배포, 설정 변경)
- [ ] 외부 요인 확인 (트래픽 급증, DDoS)
- [ ] 데이터베이스 상태 확인
  ```sql
  SHOW PROCESSLIST;
  SHOW ENGINE INNODB STATUS;
  ```

### 복구 조치 (15분~)

- [ ] 임시 조치 실행 (재시작, 롤백 등)
- [ ] 복구 확인
  ```bash
  curl http://localhost:8081/actuator/health
  ```
- [ ] 기능 테스트
  ```bash
  # API 테스트
  curl -X POST http://localhost:8081/api/coupons/11/issue-fcfs/test-user
  ```
- [ ] 모니터링 지속

### 사후 조치 (장애 해결 후)

- [ ] 상황 종료 공지
- [ ] 장애 리포트 작성
- [ ] Post-Mortem 회의 일정 수립
- [ ] 재발 방지 대책 수립
- [ ] 관련 문서 업데이트

---

## 사후 분석 (Post-Mortem)

### Post-Mortem 템플릿

```markdown
# 장애 보고서

## 요약
- **장애 일시**: YYYY-MM-DD HH:MM ~ HH:MM (지속 시간: XX분)
- **장애 등급**: P0/P1/P2/P3
- **영향 범위**:
  - 영향받은 사용자 수: XX명
  - 실패한 요청 수: XX건
  - 매출 영향: XX원
- **근본 원인**: 한 문장 요약

## 타임라인
| 시각 | 이벤트 | 담당자 |
|-----|-------|-------|
| 14:23 | 장애 감지 (Pinpoint 알림) | 모니터링 시스템 |
| 14:25 | 대응 시작 | On-Call Engineer |
| 14:30 | 원인 파악 완료 | Engineer |
| 14:45 | 임시 복구 완료 | Engineer |
| 15:00 | 근본 해결 완료 | Team |
| 15:30 | 정상화 확인 | Team |

## 상세 분석

### 발생 원인
- 직접 원인:
- 근본 원인:
- 기여 요인:

### 영향 분석
- 사용자 영향:
- 비즈니스 영향:
- 시스템 영향:

### 대응 과정
- 잘된 점:
- 개선 필요:
- 학습한 점:

## 재발 방지 대책

### 단기 (1주일 이내)
- [ ]
- [ ]

### 중기 (1개월 이내)
- [ ]
- [ ]

### 장기 (3개월 이내)
- [ ]
- [ ]

## Action Items
| 작업 | 담당자 | 기한 | 상태 |
|-----|-------|------|------|
|     |       |      |      |

## 참고 자료
- 로그 파일:
- 관련 티켓:
- 관련 문서:
```

### Post-Mortem 회의 가이드

**참석자**:
- Incident Commander
- 관련 엔지니어
- 팀 리더
- 필요시 경영진

**안건**:
1. 타임라인 리뷰 (10분)
2. 근본 원인 분석 (20분)
3. 대응 과정 리뷰 (15분)
4. 재발 방지 대책 논의 (15분)

**원칙**:
- Blameless Culture (비난하지 않기)
- 시스템 개선에 집중
- 학습 기회로 활용

---

## 연락처 및 에스컬레이션

### 긴급 연락망

#### On-Call Engineer (24/7)
- 이름: [담당자명]
- 전화: 010-XXXX-XXXX
- Slack: @engineer-oncall

#### Backend Team Lead
- 이름: [팀장명]
- 전화: 010-XXXX-XXXX
- Slack: @backend-lead

#### CTO/VP Engineering
- 이름: [임원명]
- 전화: 010-XXXX-XXXX
- Slack: @cto

### 에스컬레이션 기준

**P0 (Critical)**:
- 즉시 On-Call Engineer
- 5분 내 Team Lead
- 30분 내 CTO (미해결 시)

**P1 (High)**:
- 즉시 On-Call Engineer
- 30분 내 Team Lead (미해결 시)
- 4시간 내 CTO (미해결 시)

**P2 (Medium)**:
- On-Call Engineer
- 4시간 내 Team Lead (미해결 시)

**P3 (Low)**:
- 업무 시간 내 처리
- 에스컬레이션 불필요

### 외부 지원

**클라우드 제공자 지원**:
- AWS Support: 케이스 생성
- GCP Support: 티켓 발행

**데이터베이스 벤더**:
- MySQL Enterprise Support

**APM 벤더**:
- Pinpoint Community Forum

---

## 부록

### A. 유용한 명령어 모음

#### Docker 관련
```bash
# 모든 컨테이너 상태
docker ps -a

# 리소스 사용량
docker stats --no-stream

# 로그 실시간 조회
docker logs -f <container_name>

# 컨테이너 내부 접속
docker exec -it <container_name> /bin/bash

# 네트워크 확인
docker network ls
docker network inspect <network_name>

# 볼륨 확인
docker volume ls
docker volume inspect <volume_name>
```

#### MySQL 관련
```bash
# MySQL 접속
docker exec -it ecommerce-mysql mysql -uecommerce_user -pecommerce123 ecommerce

# 프로세스 목록
SHOW PROCESSLIST;

# InnoDB 상태
SHOW ENGINE INNODB STATUS\G

# 슬로우 쿼리
SELECT * FROM mysql.slow_log ORDER BY start_time DESC LIMIT 10;

# 테이블 크기
SELECT
  table_name,
  ROUND(((data_length + index_length) / 1024 / 1024), 2) AS "Size (MB)"
FROM information_schema.TABLES
WHERE table_schema = "ecommerce"
ORDER BY (data_length + index_length) DESC;
```

#### Redis 관련
```bash
# Redis 접속
docker exec -it ecommerce-redis redis-cli

# 메모리 정보
INFO memory

# 키 개수
DBSIZE

# 키 패턴 검색
KEYS pattern*

# 키 삭제
DEL key

# 슬로우 로그
SLOWLOG GET 10
```

#### Kafka 관련
```bash
# Topic 목록
docker exec ecommerce-kafka kafka-topics --bootstrap-server localhost:9092 --list

# Topic 상세
docker exec ecommerce-kafka kafka-topics --bootstrap-server localhost:9092 --describe --topic coupon.issue.requested

# Consumer Group 확인
docker exec ecommerce-kafka kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group coupon-issue-service

# 메시지 확인
docker exec ecommerce-kafka kafka-console-consumer --bootstrap-server localhost:9092 --topic coupon.issue.requested --from-beginning --max-messages 10
```

---

### B. 장애 시뮬레이션 스크립트

**목적**: 정기적으로 장애 시나리오를 실습하여 대응 능력 향상

#### 시뮬레이션 1: Redis 장애
```bash
#!/bin/bash
echo "=== Redis 장애 시뮬레이션 시작 ==="

# Redis 중지
docker stop ecommerce-redis
echo "[$(date)] Redis 중지됨"

# 30초 대기
sleep 30

# Redis 재시작
docker start ecommerce-redis
echo "[$(date)] Redis 재시작됨"

# 복구 확인
docker exec ecommerce-redis redis-cli ping
echo "=== 시뮬레이션 종료 ==="
```

#### 시뮬레이션 2: 네트워크 파티션
```bash
#!/bin/bash
echo "=== 네트워크 장애 시뮬레이션 시작 ==="

# App과 MySQL 간 네트워크 차단
docker network disconnect ecommerce_default ecommerce-app
echo "[$(date)] 네트워크 차단됨"

# 1분 대기
sleep 60

# 네트워크 복구
docker network connect ecommerce_default ecommerce-app
echo "[$(date)] 네트워크 복구됨"

echo "=== 시뮬레이션 종료 ==="
```

#### 시뮬레이션 3: 메모리 부족
```bash
#!/bin/bash
echo "=== 메모리 부족 시뮬레이션 시작 ==="

# 메모리 제한 설정
docker update --memory="256m" ecommerce-app
echo "[$(date)] 메모리 제한 적용 (256MB)"

# 부하 생성
cd k6-tests
k6 run scenarios/coupon-fcfs-quick.js

# 메모리 제한 해제
docker update --memory="2g" ecommerce-app
echo "[$(date)] 메모리 제한 해제"

echo "=== 시뮬레이션 종료 ==="
```

---

### C. 정기 점검 체크리스트

#### 일일 점검 (매일 오전 9시)
- [ ] 모든 컨테이너 상태 확인
- [ ] Pinpoint 대시보드 리뷰
- [ ] 에러 로그 확인 (지난 24시간)
- [ ] 디스크 사용률 확인 (>80% 경고)
- [ ] 백업 상태 확인

#### 주간 점검 (매주 월요일)
- [ ] 슬로우 쿼리 로그 분석
- [ ] Kafka Consumer Lag 확인
- [ ] Redis 메모리 사용 추세
- [ ] JVM Heap Dump 분석 (필요시)
- [ ] 보안 패치 확인

#### 월간 점검 (매월 1일)
- [ ] 장애 대응 훈련 실시
- [ ] Post-Mortem 리뷰
- [ ] 모니터링 임계값 재검토
- [ ] 백업 복구 테스트
- [ ] 용량 계획 리뷰

---

**작성일**: 2025년 12월 25일
**작성자**: DevOps Team
**버전**: 1.0
**다음 리뷰**: 2025년 3월 25일 (분기별 업데이트)
