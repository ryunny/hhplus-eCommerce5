/**
 * 주문 시스템 내구성 테스트 (Soak Test)
 *
 * 목적: 장시간 운영 시 시스템 안정성 검증
 *
 * 검증 항목:
 * - 메모리 누수 (JVM Heap 증가 추세)
 * - DB 커넥션 풀 누수
 * - Thread 증가 추세
 * - GC 빈도 및 시간 증가
 * - 응답 시간 저하 여부
 *
 * 주의사항:
 * - 이 테스트는 2시간 이상 소요됩니다
 * - Pinpoint APM으로 JVM 메트릭을 모니터링하세요
 * - 테스트 중 시스템 리소스를 지속적으로 관찰하세요
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// 실제 DB의 사용자 UUID 목록
const USER_IDS = [
    'd1d8a533-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8ab54-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8acb5-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8ad92-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8ae2a-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8aebb-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8af4d-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8afd4-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8b067-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8b0f5-da8b-11f0-bcb1-3e0ec4444181',
];

// 실제 DB의 상품 정보
const PRODUCTS = [
    { id: 1, name: '노트북', price: 1500000 },
    { id: 2, name: '무선 마우스', price: 35000 },
    { id: 3, name: '키보드', price: 120000 },
    { id: 4, name: '모니터 27인치', price: 350000 },
    { id: 5, name: '웹캠', price: 85000 },
];

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const DURATION_HOURS = parseInt(__ENV.DURATION_HOURS || '2'); // 기본 2시간

// 커스텀 메트릭
const orderSuccessRate = new Rate('order_success_rate');
const orderDuration = new Trend('order_duration');
const orderCount = new Counter('order_count');

/**
 * 내구성 테스트 시나리오
 *
 * 경고: 기본 2시간 소요
 */
export const options = {
    stages: [
        // 워밍업: 100 VUs까지 증가
        { duration: '5m', target: 100 },

        // 내구성 테스트: 100 VUs를 장시간 유지
        { duration: `${DURATION_HOURS}h`, target: 100 },

        // 쿨다운
        { duration: '5m', target: 0 },
    ],

    thresholds: {
        // 장시간 운영 시에도 성능 유지
        'http_req_duration': ['p(95)<1500'],   // 95% 요청이 1.5초 이내
        'http_req_failed': ['rate<0.01'],      // 실패율 < 1%
        'order_success_rate': ['rate>0.99'],   // 성공률 > 99%
    },
};

/**
 * 랜덤 상품 선택
 */
function getRandomProducts() {
    const itemCount = Math.floor(Math.random() * 2) + 1;
    const items = [];

    for (let i = 0; i < itemCount; i++) {
        const product = PRODUCTS[Math.floor(Math.random() * PRODUCTS.length)];
        const quantity = Math.floor(Math.random() * 2) + 1;

        items.push({
            productId: product.id,
            quantity: quantity
        });
    }

    return items;
}

/**
 * 주문 생성 요청
 */
export default function () {
    const userId = USER_IDS[Math.floor(Math.random() * USER_IDS.length)];

    const orderData = {
        items: getRandomProducts(),
        userCouponId: null,
        recipientName: '내구성테스터',
        shippingAddress: '서울시 강남구',
        shippingPhone: '010-1234-5678'
    };

    const url = `${BASE_URL}/api/orders/orchestration/${userId}`;
    const payload = JSON.stringify(orderData);

    const response = http.post(url, payload, {
        headers: { 'Content-Type': 'application/json' },
        tags: { name: 'OrderSoakTest' },
    });

    // 응답 검증
    const success = check(response, {
        '주문 성공': (r) => r.status === 200,
        '응답 시간 정상': (r) => r.timings.duration < 2000,
    });

    // 메트릭 기록
    orderCount.add(1);
    orderSuccessRate.add(success);
    orderDuration.add(response.timings.duration);

    // 일정한 간격으로 요청 (3~5초)
    sleep(Math.random() * 2 + 3);
}

/**
 * 테스트 결과 요약
 */
export function handleSummary(data) {
    const metrics = data.metrics;

    console.log('\n========================================');
    console.log('⏳ 주문 시스템 내구성 테스트 결과');
    console.log('========================================\n');

    console.log('📊 테스트 설정:');
    console.log(`   테스트 시간: ${DURATION_HOURS}시간`);
    console.log(`   동시 사용자: 100 VUs (지속)\n`);

    console.log('📊 요청 통계:');
    console.log(`   총 주문: ${metrics.order_count.values.count}건`);
    console.log(`   성공률: ${(metrics.order_success_rate.values.rate * 100).toFixed(2)}%\n`);

    console.log('⏱️  응답 시간 추세 분석:');
    console.log(`   평균: ${metrics.http_req_duration.values.avg.toFixed(2)}ms`);
    console.log(`   p50: ${metrics.http_req_duration.values['p(50)'].toFixed(2)}ms`);
    console.log(`   p95: ${metrics.http_req_duration.values['p(95)'].toFixed(2)}ms`);
    console.log(`   p99: ${metrics.http_req_duration.values['p(99)'].toFixed(2)}ms`);
    console.log(`   최대: ${metrics.http_req_duration.values.max.toFixed(2)}ms\n`);

    console.log('💡 확인 사항:');
    console.log('   [ ] Pinpoint에서 JVM Heap 메모리 증가 추세 확인');
    console.log('   [ ] GC 빈도 및 시간 증가 여부 확인');
    console.log('   [ ] Thread 수 증가 여부 확인');
    console.log('   [ ] DB 커넥션 풀 누수 확인');
    console.log('   [ ] 응답 시간 저하 여부 확인\n');

    console.log('✅ 정상 기준:');
    console.log('   - JVM Heap: 일정 수준 유지 (증가 추세 없음)');
    console.log('   - GC: 빈도/시간 증가 없음');
    console.log('   - Thread: 일정 수준 유지');
    console.log('   - 응답 시간: 초기 대비 10% 이내 변동\n');

    console.log('========================================\n');

    return {
        'stdout': JSON.stringify(data, null, 2),
    };
}
