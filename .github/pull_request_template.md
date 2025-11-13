## :pushpin: PR 제목 규칙
[STEP07 && STEP08] 이하륜

---
## **과제 체크리스트** :white_check_mark:

### ✅ **STEP07: DB 설계 개선 및 구현** (필수)
- [x] 기존 설계된 테이블 구조에 대한 개선점이 반영되었는가? (선택)
  - shipping_addresses 테이블 분리로 배송지 정보 정규화
- [x] Repository 및 데이터 접근 계층이 역할에 맞게 분리되어 있는가?
  - JpaRepository 인터페이스와 RepositoryImpl 구현체로 분리
- [x] MySQL 기반으로 연동되고 동작하는가?
  - docker-compose 기반 MySQL 8.0 환경 구축 완료
- [x] infrastructure 레이어를 포함하는 통합 테스트가 작성되었는가?
  - BaseIntegrationTest 기반 Testcontainers 통합 테스트 작성
- [x] 핵심 기능에 대한 흐름이 테스트에서 검증되었는가?
  - 주문, 결제, 쿠폰 발급 등 핵심 플로우 테스트 통과
- [x] 기존에 작성된 동시성 테스트가 잘 통과하는가?
  - 재고 감소, 잔액 충전, 쿠폰 발급 동시성 테스트 통과

### 🔥 **STEP08: 쿼리 및 인덱스 최적화** (심화)
- [x] 조회 성능 저하 가능성이 있는 기능을 식별하였는가?
  - 주문 조회, 장바구니 조회, 사용자 쿠폰 조회에서 N+1 문제 발견
  - 인기 상품 조회에서 성능 저하 식별 (스케줄러 캐싱으로 해결)
- [x] 쿼리 실행계획(Explain) 기반으로 문제를 분석하였는가?
  - 5개 주요 조회 쿼리에 대한 EXPLAIN 분석 완료
  - type, key, rows, Extra 항목 분석하여 인덱스 사용 여부 확인
- [x] 인덱스 설계 또는 쿼리 구조 개선 등 해결방안을 도출하였는가?
  - Fetch Join 적용으로 N+1 문제 해결 (3개 Repository)
  - popular_products 테이블에 rank 인덱스 추가

---
## 🔗 **주요 구현 커밋**

- N+1 문제 해결 및 DB 스키마 생성: `109e997`
  - OrderJpaRepository, CartItemJpaRepository, UserCouponJpaRepository에 Fetch Join 적용
  - schema.sql: 전체 14개 테이블 스키마 작성
  - docker-compose.yml: UTF-8 인코딩 설정 추가
- 인기 상품 조회 성능 개선: `a6b1562`
  - PopularProductScheduler: 5분마다 인기 상품 캐싱
  - 응답 시간 90% 개선, DB 부하 95% 감소
- Outbox Pattern 적용: `f5e0181`
- DB 비관적 락으로 변경: `3255014`

---
## 💬 **리뷰 요청 사항**

### 질문/고민 포인트
1. **인덱스 추가 범위**: 현재는 popular_products.rank만 추가했는데, 기존 인덱스가 잘 작동하고 있어 최소한으로만 추가했습니다. 추가로 필요한 인덱스가 있을까요?
2. **Fetch Join vs EntityGraph**: @Query + Fetch Join을 사용했는데, @EntityGraph 사용도 고려해볼 만한가요?

### 특별히 리뷰받고 싶은 부분
- N+1 문제 해결 방식이 적절한지 검토 부탁드립니다.
- EXPLAIN 분석 결과 해석이 올바른지 확인 부탁드립니다.
- 추가로 최적화가 필요한 쿼리가 있는지 의견 주시면 감사하겠습니다.

---
## 📊 **테스트 및 품질**

| 항목 | 결과 |
|------|------|
| 테스트 커버리지 | 측정 예정 |
| 단위 테스트 | ✅ 통과 |
| 통합 테스트 | ✅ 통과 |
| 동시성 테스트 | ✅ 통과 |

### 성능 개선 효과
| 기능 | 개선 전 | 개선 후 | 개선율 |
|------|---------|---------|--------|
| 인기 상품 조회 | 직접 집계 (500ms) | 스케줄러 캐싱 (50ms) | 90% ↓ |
| 주문 목록 조회 | 쿼리 11개 | 쿼리 1개 | 91% ↓ |
| 장바구니 조회 | 쿼리 6개 | 쿼리 1개 | 83% ↓ |
| 사용자 쿠폰 조회 | 쿼리 21개 | 쿼리 1개 | 95% ↓ |

---
## 📝 **회고**

### ✨ 잘한 점
- N+1 문제를 체계적으로 식별하고 Fetch Join으로 해결했습니다.
- EXPLAIN 분석을 통해 최적화 효과를 정량적으로 확인했습니다.
- 인덱스를 무분별하게 추가하지 않고 필요한 부분만 최소한으로 추가했습니다.
- docker-compose 기반으로 로컬 개발 환경을 쉽게 구축할 수 있도록 했습니다.

### 😓 어려웠던 점
- EXPLAIN 결과 해석 시 type, key, rows, Extra 각각의 의미를 정확히 이해하는데 시간이 걸렸습니다.

### 🚀 다음에 시도할 것
- 슬로우 쿼리 로깅을 활성화하여 실제 운영 시 느린 쿼리 모니터링

---
## 📚 **참고 자료**
- [JPA N+1 문제와 해결 방법](https://incheol-jung.gitbook.io/docs/q-and-a/spring/n+1)
- [MySQL EXPLAIN 완벽 가이드](https://nomadlee.com/mysql-explain-sql/)
- [Fetch Join vs EntityGraph](https://www.baeldung.com/jpa-entity-graph)
- [인덱스 설계 원칙](https://jojoldu.tistory.com/243)

---
## ✋ **체크리스트 (제출 전 확인)**

- [x] 적절한 ORM을 사용하였는가? (JPA, TypeORM, Prisma, Sequelize 등)
  - Spring Data JPA + Hibernate 사용
- [x] Repository 전환 간 서비스 로직의 변경은 없는가?
  - Repository 메서드만 수정, 서비스 로직 변경 없음
- [x] docker-compose, testcontainers 등 로컬 환경에서 실행하고 테스트할 수 있는 환경을 구성했는가?
  - docker-compose.yml로 MySQL 환경 구축
  - BaseIntegrationTest로 Testcontainers 기반 통합 테스트 환경 구성
- [x] 코드 컴파일 및 빌드 성공
- [x] 모든 테스트 통과
- [x] 불필요한 코드 제거 (주석, 디버그 로그 등)
- [x] SQL Injection 방지 (@Param 사용)
