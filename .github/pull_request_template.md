## :pushpin: PR 제목 규칙
[STEP 15-16] 이하륜 - 선택 시나리오 (e-commerce)

---
### STEP 15 Application Event
- [x] 주문/예약 정보를 원 트랜잭션이 종료된 이후에 전송
- [x] 주문/예약 정보를 전달하는 부가 로직에 대한 관심사를 메인 서비스에서 분리

### STEP 16 Transaction Diagnosis
- [x] 도메인별로 트랜잭션이 분리되었을 때 발생 가능한 문제 파악
- [x] 트랜잭션이 분리되더라도 데이터 일관성을 보장할 수 있는 분산 트랜잭션 설계

### **중요 커밋**
- [8d4e5a9](https://github.com/ryunny/hhplus-eCommerce5/commit/8d4e5a9) - Saga 패턴 구현 (Orchestration vs Choreography)
- [104377a](https://github.com/ryunny/hhplus-eCommerce5/commit/104377a) - Outbox Pattern 적용 및 인기상품 랭킹 이벤트 추가
- [52ae660](https://github.com/ryunny/hhplus-eCommerce5/commit/52ae660) - Saga/이벤트/Outbox 통합 테스트 추가
- [a4d9e5d](https://github.com/ryunny/hhplus-eCommerce5/commit/a4d9e5d) - 날짜별 키 분리 및 TTL 기반 랭킹 관리 개선

### **간단 회고** (3줄 이내)
- **잘한 점**: Outbox Pattern과 Saga로 이벤트 기반 아키텍처 구현, @TransactionalEventListener와 비동기 처리로 트랜잭션 분리 및 데이터 일관성 보장
- **어려운 점**: 분산 트랜잭션 환경에서 실패 복구(재시도/보상), 이벤트 순서 보장, 중복 발행 방지 등 예외 상황 처리 설계
- **다음 시도**: Kafka 같은 메시지 브로커 도입으로 이벤트 스트리밍 확장성 개선 및 이벤트 소싱 패턴 적용