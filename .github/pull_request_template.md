## :pushpin: PR 제목 규칙
[STEP13,14] 이하륜 - 선택 시나리오 (e-commerce)

---
### **핵심 체크리스트** :white_check_mark:

#### one: ranking design
- [ ] 적절한 설계를 기반으로 랭킹기능이 개발되었는가?
- [ ] 적절한 자료구조를 선택하였는가?


#### two: Asynchronous Design
- [ ] 적절한 설계를 기반으로 쿠폰 발급 or 대기열 기능이 개발되었는가?
- [ ] 적절한 자료구조를 선택하였는가?


#### three: 통합 테스트
- [ ] redis 테스트 컨테이너를 통해 적절하게 통합 테스트가 작성되었는가?(독립적 테스트 환경을 보장하는가?)
- [ ] 핵심 기능에 대한 흐름이 테스트에서 검증되었는가?

---
### STEP 13 Ranking Design
- **이커머스 시나리오**
- [] 가장 많이 주문한 상품 랭킹을 Redis 기반으로 설계
- [] 설계를 기반으로 개발 및 구현


### STEP 14 Asynchronous Design
- **이커머스 시나리오**
- [] 선착순 쿠폰발급 기능에 대해 Redis 기반의 설계
- [] 적절하게 동작할 수 있도록 쿠폰 발급 로직을 개선해 제출
- [] 시스템 ( 랭킹, 비동기 ) 디자인 설계 및 개발 후 회고 내용을 담은 보고서 제출


### **간단 회고**
- **잘한 점**: Redis Sorted Set 선택으로 O(log N) 성능과 자동 정렬 확보, Lua 스크립트로 원자성 보장, 비동기 이벤트 처리로 시스템 간 느슨한 결합 구현
- **어려운 점**: 대기열 중복 발급 방지 및 선착순 보장, Redis → DB Fallback 패턴 설계, API 중복 제거 및 단일 진입점 구조로 리팩토링
- **다음 시도**: Redis Sentinel 적용(HA), 동적 배치 크기 조절, 캐시 Warm-up 전략 구현

---

### **중요 커밋**
**핵심 기능 구현**
- `b4cf915` - Redis Sorted Set 기반 선착순 대기열 및 인기 상품 랭킹 구현
- `43c5d79` - 주문 완료 후 비동기 랭킹 업데이트로 전환
- `768e53b` - 대기열 통합 테스트 추가 및 Redis TestContainer 적용

**동시성 제어**
- `7ac1f36` - DB 비관적 락을 Redis Pub/Sub Lock으로 전환
- `62ad02d` - CouponService 동시성 제어를 위한 분산락 추가
- `ab49560` - 낙관적 락(@Version) 추가로 다층 동시성 제어 구현

**성능 최적화**
- `6c84114` - Redis Cache 적용으로 조회 성능 개선
- `0179ea2` - Redis Cache 최적화 및 성능 개선
- `4e82b3e` - Lua 스크립트 적용하여 대기열 처리 원자성 및 성능 개선
- `97b6b7a` - N+1 쿼리 문제 해결
- `4dbca0b` - findAll() + stream().filter() 패턴을 DB 쿼리로 개선

**안정성 향상**
- `9b4a73a` - Redis 장애 대응 - DB Lock & Cache Fallback 구현
- `a473e06` - 쿠폰 대기열 API를 Fallback 패턴으로 통합
- `7d5f735` - issueCoupon API에 Redis Fallback 통합하여 중복 제거
- `10fc2b2` - 주문 생성 시 데드락 방지를 위한 락 순서 보장

**리팩토링**
- `97f8ca5` - Redis 키 구조를 계층적 네임스페이스로 리팩토링
- `a6d13c0` - Redis Lock 키를 타입 기반 네임스페이스로 개선
- `5bbe41f` - 캐시 키 관리를 중앙 집중화 (CacheKeyGenerator 도입)
- `8dcccb6` - 중복 UseCase 및 deprecated API 제거 (850줄 제거)

**문서화**
- `8ebc8f9` - Redis 기반 대규모 트래픽 처리 시스템 설계 및 구현 보고서 추가
- `0d62159` - Redis 시스템 설계 보고서 정리 및 최신 개선사항 반영