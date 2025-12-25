/**
 * 주문 패턴 비교 테스트
 *
 * 목적: Orchestration vs Choreography 패턴 성능 비교
 *
 * Orchestration:
 * - UseCase가 모든 로직을 동기적으로 처리
 * - 즉시 PAID 상태로 완료
 * - 응답 시간이 길지만 일관성 보장
 *
 * Choreography:
 * - UseCase는 주문 생성 + 이벤트 발행만
 * - PENDING 상태로 반환 → 비동기 처리
 * - 응답 시간 빠르지만 최종 일관성(Eventual Consistency)
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

// Orchestration 메트릭
const orchestrationSuccessRate = new Rate('orchestration_success_rate');
const orchestrationDuration = new Trend('orchestration_duration');

// Choreography 메트릭
const choreographySuccessRate = new Rate('choreography_success_rate');
const choreographyDuration = new Trend('choreography_duration');

/**
 * 비교 테스트 시나리오
 *
 * 각 패턴을 번갈아 가며 호출하여 공정한 비교
 */
export const options = {
    stages: [
        { duration: '30s', target: 50 },   // 워밍업
        { duration: '2m', target: 100 },   // 비교 테스트
        { duration: '30s', target: 0 },    // 쿨다운
    ],

    thresholds: {
        'http_req_duration': ['p(95)<2000'],
        'http_req_failed': ['rate<0.05'],
    },
};

/**
 * 랜덤 상품 선택
 */
function getRandomProducts() {
    const itemCount = Math.floor(Math.random() * 2) + 1; // 1~2개
    const items = [];

    for (let i = 0; i < itemCount; i++) {
        const product = PRODUCTS[Math.floor(Math.random() * PRODUCTS.length)];
        const quantity = Math.floor(Math.random() * 2) + 1; // 1~2개

        items.push({
            productId: product.id,
            quantity: quantity
        });
    }

    return items;
}

/**
 * 주문 테스트 (Orchestration or Choreography)
 */
function testOrder(userId, pattern) {
    const orderData = {
        items: getRandomProducts(),
        userCouponId: null,
        recipientName: '테스터',
        shippingAddress: '서울시 강남구',
        shippingPhone: '010-1234-5678'
    };

    const url = `${BASE_URL}/api/orders/${pattern}/${userId}`;
    const payload = JSON.stringify(orderData);

    const response = http.post(url, payload, {
        headers: { 'Content-Type': 'application/json' },
        tags: {
            name: `Order_${pattern}`,
            pattern: pattern
        },
    });

    return response;
}

/**
 * 메인 테스트 함수
 *
 * Orchestration과 Choreography를 번갈아 테스트
 */
export default function () {
    const userId = USER_IDS[Math.floor(Math.random() * USER_IDS.length)];

    // VU 번호에 따라 패턴 선택 (50:50 비율)
    const pattern = (__VU % 2 === 0) ? 'orchestration' : 'choreography';

    const response = testOrder(userId, pattern);

    // 응답 검증
    const success = check(response, {
        '주문 성공': (r) => r.status === 200,
        '응답 시간 < 2초': (r) => r.timings.duration < 2000,
    });

    // 패턴별 메트릭 기록
    if (pattern === 'orchestration') {
        orchestrationSuccessRate.add(success);
        orchestrationDuration.add(response.timings.duration);
    } else {
        choreographySuccessRate.add(success);
        choreographyDuration.add(response.timings.duration);
    }

    sleep(1);
}

/**
 * 테스트 결과 비교
 */
export function handleSummary(data) {
    const metrics = data.metrics;

    console.log('\n========================================');
    console.log('🔄 주문 패턴 비교 테스트 결과');
    console.log('========================================\n');

    // Orchestration 결과
    console.log('📊 Orchestration 패턴 (동기):');
    if (metrics.orchestration_duration) {
        console.log(`   평균 응답시간: ${metrics.orchestration_duration.values.avg.toFixed(2)}ms`);
        console.log(`   p95: ${metrics.orchestration_duration.values['p(95)'].toFixed(2)}ms`);
        console.log(`   p99: ${metrics.orchestration_duration.values['p(99)'].toFixed(2)}ms`);
        console.log(`   성공률: ${(metrics.orchestration_success_rate.values.rate * 100).toFixed(2)}%\n`);
    }

    // Choreography 결과
    console.log('📊 Choreography 패턴 (비동기):');
    if (metrics.choreography_duration) {
        console.log(`   평균 응답시간: ${metrics.choreography_duration.values.avg.toFixed(2)}ms`);
        console.log(`   p95: ${metrics.choreography_duration.values['p(95)'].toFixed(2)}ms`);
        console.log(`   p99: ${metrics.choreography_duration.values['p(99)'].toFixed(2)}ms`);
        console.log(`   성공률: ${(metrics.choreography_success_rate.values.rate * 100).toFixed(2)}%\n`);
    }

    // 비교 분석
    if (metrics.orchestration_duration && metrics.choreography_duration) {
        const orchAvg = metrics.orchestration_duration.values.avg;
        const choreoAvg = metrics.choreography_duration.values.avg;
        const diff = orchAvg - choreoAvg;
        const diffPercent = ((diff / orchAvg) * 100).toFixed(2);

        console.log('📈 비교 분석:');
        console.log(`   응답 시간 차이: ${Math.abs(diff).toFixed(2)}ms`);

        if (diff > 0) {
            console.log(`   ✅ Choreography가 ${diffPercent}% 더 빠름`);
        } else {
            console.log(`   ✅ Orchestration이 ${Math.abs(diffPercent)}% 더 빠름`);
        }

        console.log('\n   💡 패턴 선택 가이드:');
        console.log('   - Orchestration: 즉시 일관성 필요, 응답 시간 덜 중요');
        console.log('   - Choreography: 빠른 응답 필요, 최종 일관성 허용\n');
    }

    console.log('========================================\n');

    return {
        'stdout': JSON.stringify(data, null, 2),
    };
}
