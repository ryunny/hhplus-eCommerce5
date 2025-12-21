## :pushpin: PR 제목 규칙
[STEP17-18] 이하륜 - 선택 시나리오 (e-commerce)

---
### STEP 17 카프카 기초 학습 및 활용
- [x] 카프카에 대한 기본 개념 학습 문서 작성
- [x] 실시간 주문/예약 정보를 카프카 메시지로 발행

### STEP 18 카프카를 활용하여 비즈니스 프로세스 개선
- [x] 카프카를 특징을 활용하도록 쿠폰/대기열 설계문서 작성
- [x] 설계문서대로 카프카를 활용한 기능 구현

---

## 주요 커밋

### Kafka 기반 Choreography 패턴 구현
- [3f3fffc](https://github.com/ryunny/hhplus-eCommerce5/commit/3f3fffc) - feat: Choreography 패턴 전체를 Kafka로 전환

### Kafka 기반 쿠폰 발급 & 대기열 시스템
- [dc13f94](https://github.com/ryunny/hhplus-eCommerce5/commit/dc13f94) - feat: Kafka 기반 쿠폰 발급 & 대기열 시스템 기초 구조
- [ef6f1b9](https://github.com/ryunny/hhplus-eCommerce5/commit/ef6f1b9) - feat: Kafka 기반 쿠폰 발급 & 대기열 시스템 완전 구현

### 버그 수정 및 리팩토링
- [0543daf](https://github.com/ryunny/hhplus-eCommerce5/commit/0543daf) - fix: Kafka 쿠폰 발급 시스템 의존성 및 메서드 호출 수정
- [facf457](https://github.com/ryunny/hhplus-eCommerce5/commit/facf457) - fix: KafkaTemplate 제네릭 타입 수정 (Object로 통일)
- [92781c4](https://github.com/ryunny/hhplus-eCommerce5/commit/92781c4) - refactor: 코드 정리 및 hasStock() 메서드 추가

---

## 간단 회고

### 잘한 점
- Kafka를 활용한 Choreography 패턴으로 도메인 간 결합도를 낮추고 확장성을 확보했습니다.
- Redis Sorted Set과 Lua 스크립트를 활용하여 선착순 쿠폰 발급의 동시성 문제를 효과적으로 해결했습니다.
- 멱등성 보장, DLQ 처리, Outbox 패턴 등 프로덕션 레벨의 안정성을 고려한 설계를 적용했습니다.

### 어려운 점
- Kafka Consumer의 멱등성 보장과 Redis 캐시 롤백 타이밍 조율이 복잡했습니다.
- Choreography 패턴에서 여러 핸들러에 분산된 로직의 흐름을 추적하고 디버깅하는 것이 어려웠습니다.
- 비동기 이벤트 처리 과정에서 최종 일관성(Eventual Consistency)을 보장하는 설계가 까다로웠습니다.

### 다음 시도
- Kafka Streams를 활용한 실시간 이벤트 처리 및 집계 기능을 추가해보고 싶습니다.
- Saga 패턴의 모니터링 및 추적을 위한 분산 추적(Distributed Tracing) 도입을 고려하겠습니다.
- 대용량 트래픽 환경에서의 성능 테스트 및 병목 구간 최적화를 진행하겠습니다.