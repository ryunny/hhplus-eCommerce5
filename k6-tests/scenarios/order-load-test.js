/**
 * 주문 시스템 부하 테스트
 *
 * 목적: 정상 운영 부하에서 주문 시스템 성능 검증
 * 패턴: 일정한 트래픽 유지 (피크타임 포함)
 *
 * 테스트 시나리오:
 * - 워밍업: 50명 (1분)
 * - 정상 부하: 100명 (5분)
 * - 피크타임: 200명 (5분)
 * - 정상 부하: 100명 (5분)
 * - 쿨다운: 0명 (1분)
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate, Trend, Counter } from 'k6/metrics';

// 실제 DB의 사용자 UUID 목록 (50명)
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
    'd1d8b18c-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8b26b-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8b2fc-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8b388-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8b412-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8b497-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8b523-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8b5b1-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8b63e-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8b6cb-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8b75b-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8b7e7-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8b86f-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8b8fb-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8b987-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8ba14-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8ba9e-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8bb28-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8bbae-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8bc36-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8bcbd-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8bd42-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8bdca-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8be6d-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8bf45-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8c002-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8c0a1-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8c177-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8c20a-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8c29b-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8c32b-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8c3bf-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8c460-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8c50f-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8c5db-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8c686-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8c74b-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8c814-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8c8ad-da8b-11f0-bcb1-3e0ec4444181',
    'd1d8c937-da8b-11f0-bcb1-3e0ec4444181',
];

// 실제 DB의 상품 정보
const PRODUCTS = [
    { id: 1, name: '노트북', price: 1500000 },
    { id: 2, name: '무선 마우스', price: 35000 },
    { id: 3, name: '키보드', price: 120000 },
    { id: 4, name: '모니터 27인치', price: 350000 },
    { id: 5, name: '웹캠', price: 85000 },
    { id: 6, name: '헤드셋', price: 180000 },
    { id: 7, name: 'USB 허브', price: 45000 },
    { id: 8, name: '외장 SSD 1TB', price: 150000 },
    { id: 9, name: '무선 충전기', price: 55000 },
    { id: 10, name: '스마트워치', price: 280000 },
];

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

// 커스텀 메트릭
const orderSuccessRate = new Rate('order_success_rate');
const orderDuration = new Trend('order_duration');
const orderErrorRate = new Rate('order_error_rate');
const orderCount = new Counter('order_count');

/**
 * 부하 테스트 시나리오
 *
 * 총 소요 시간: 17분
 */
export const options = {
    stages: [
        // 워밍업: 시스템 준비
        { duration: '1m', target: 50 },

        // 정상 부하: 평소 트래픽
        { duration: '5m', target: 100 },

        // 피크타임: 점심시간, 저녁시간 등
        { duration: '5m', target: 200 },

        // 정상 부하: 다시 평소 수준
        { duration: '5m', target: 100 },

        // 쿨다운: 점진적 종료
        { duration: '1m', target: 0 },
    ],

    thresholds: {
        // 응답 시간 목표
        'http_req_duration': ['p(95)<1000'],  // 95% 요청이 1초 이내
        'http_req_duration': ['p(99)<2000'],  // 99% 요청이 2초 이내

        // 에러율 목표
        'http_req_failed': ['rate<0.01'],     // 실패율 < 1%
        'order_error_rate': ['rate<0.01'],    // 주문 에러율 < 1%

        // 성공률 목표
        'order_success_rate': ['rate>0.99'],  // 주문 성공률 > 99%
    },
};

/**
 * 랜덤 상품 선택 (1~3개)
 */
function getRandomProducts() {
    const itemCount = Math.floor(Math.random() * 3) + 1; // 1~3개
    const items = [];

    for (let i = 0; i < itemCount; i++) {
        const product = PRODUCTS[Math.floor(Math.random() * PRODUCTS.length)];
        const quantity = Math.floor(Math.random() * 3) + 1; // 1~3개

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
    // 랜덤 사용자 선택
    const userId = USER_IDS[Math.floor(Math.random() * USER_IDS.length)];

    // 주문 데이터 생성
    const orderData = {
        items: getRandomProducts(),
        userCouponId: null,  // 쿠폰 미사용
        recipientName: '테스터',
        shippingAddress: '서울시 강남구 테헤란로 123',
        shippingPhone: '010-1234-5678'
    };

    // 주문 생성 API 호출 (Orchestration 패턴)
    const url = `${BASE_URL}/api/orders/orchestration/${userId}`;
    const payload = JSON.stringify(orderData);

    const response = http.post(url, payload, {
        headers: { 'Content-Type': 'application/json' },
        tags: { name: 'OrderLoadTest' },
    });

    // 응답 검증
    const success = check(response, {
        '주문 성공 (200)': (r) => r.status === 200,
        '응답 시간 < 1초': (r) => r.timings.duration < 1000,
        '주문 번호 존재': (r) => {
            try {
                const body = JSON.parse(r.body);
                return body.orderNumber !== undefined;
            } catch (e) {
                return false;
            }
        },
    });

    // 메트릭 기록
    orderCount.add(1);
    orderSuccessRate.add(success);
    orderErrorRate.add(!success);
    orderDuration.add(response.timings.duration);

    // 실제 사용자 행동 시뮬레이션 (2~5초 대기)
    sleep(Math.random() * 3 + 2);
}

/**
 * 테스트 결과 요약
 */
export function handleSummary(data) {
    const metrics = data.metrics;

    console.log('\n========================================');
    console.log('📦 주문 시스템 부하 테스트 결과');
    console.log('========================================\n');

    console.log('📊 요청 통계:');
    console.log(`   총 요청: ${metrics.http_reqs.values.count}건`);
    console.log(`   총 주문: ${metrics.order_count.values.count}건`);
    console.log(`   성공률: ${(metrics.order_success_rate.values.rate * 100).toFixed(2)}%`);
    console.log(`   에러율: ${(metrics.order_error_rate.values.rate * 100).toFixed(2)}%\n`);

    console.log('⏱️  응답 시간:');
    console.log(`   평균: ${metrics.http_req_duration.values.avg.toFixed(2)}ms`);
    console.log(`   최소: ${metrics.http_req_duration.values.min.toFixed(2)}ms`);
    console.log(`   최대: ${metrics.http_req_duration.values.max.toFixed(2)}ms`);
    console.log(`   중앙값 (p50): ${metrics.http_req_duration.values['p(50)'].toFixed(2)}ms`);
    console.log(`   90% (p90): ${metrics.http_req_duration.values['p(90)'].toFixed(2)}ms`);
    console.log(`   95% (p95): ${metrics.http_req_duration.values['p(95)'].toFixed(2)}ms`);
    console.log(`   99% (p99): ${metrics.http_req_duration.values['p(99)'].toFixed(2)}ms\n`);

    console.log('🚀 처리량:');
    console.log(`   평균 RPS: ${metrics.http_reqs.values.rate.toFixed(2)} req/s`);
    console.log(`   평균 주문/초: ${metrics.order_count.values.rate.toFixed(2)} orders/s\n`);

    console.log('📈 테스트 단계:');
    console.log(`   워밍업:   50 VUs  (1분)`);
    console.log(`   정상 부하: 100 VUs (5분)`);
    console.log(`   피크타임: 200 VUs (5분) ⭐`);
    console.log(`   정상 부하: 100 VUs (5분)`);
    console.log(`   쿨다운:    0 VUs  (1분)\n`);

    // Threshold 체크 결과
    console.log('✅ Threshold 검증:');
    const thresholds = data.root_group.checks;
    if (thresholds) {
        thresholds.forEach(check => {
            const symbol = check.passes === check.passes ? '✅' : '❌';
            console.log(`   ${symbol} ${check.name}: ${check.passes}/${check.fails + check.passes}`);
        });
    }

    console.log('\n========================================\n');

    return {
        'stdout': JSON.stringify(data, null, 2),
    };
}
