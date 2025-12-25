/**
 * 선착순 쿠폰 발급 스파이크 테스트
 *
 * 실제 선착순 이벤트 시뮬레이션:
 * - 4단계 테스트: Smoke → Load → Stress → Spike
 * - 최대 500 VUs (동시 접속자)
 * - 실제 DB 사용자 UUID 사용
 */

import http from 'k6/http';
import { check, sleep } from 'k6';

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

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';
const COUPON_ID = __ENV.COUPON_ID || '11';

/**
 * 4단계 스파이크 테스트 시나리오
 *
 * Stage 1: Smoke Test (10 VUs, 10초)
 * Stage 2: Load Test (100 VUs, 30초)
 * Stage 3: Stress Test (200 VUs, 30초)
 * Stage 4: Spike Test (500 VUs, 20초) ⭐ 핵심
 */
export const options = {
    stages: [
        // Stage 1: Smoke Test - 기본 동작 확인
        { duration: '10s', target: 10 },

        // Stage 2: Load Test - 정상 부하
        { duration: '10s', target: 100 },
        { duration: '20s', target: 100 },
        { duration: '10s', target: 100 },

        // Stage 3: Stress Test - 한계 탐색
        { duration: '10s', target: 200 },
        { duration: '20s', target: 200 },

        // Stage 4: Spike Test - 급격한 트래픽 폭증 (실제 선착순 오픈 순간)
        { duration: '5s', target: 500 },   // 5초 만에 500명 급증!
        { duration: '15s', target: 500 },  // 15초 유지
        { duration: '5s', target: 0 },     // 급격히 감소
    ],
    thresholds: {
        http_req_failed: ['rate<0.10'],     // 실패율 < 10%
        http_req_duration: ['p(95)<2000'],  // 95% 요청 2초 이내
        http_req_duration: ['p(99)<3000'],  // 99% 요청 3초 이내
    },
};

let successCount = 0;
let duplicateCount = 0;
let stockOutCount = 0;
let failureCount = 0;

export default function () {
    // VU 번호를 기반으로 사용자 선택 (순환)
    const userId = USER_IDS[(__VU - 1) % USER_IDS.length];
    const url = `${BASE_URL}/api/coupons/${COUPON_ID}/issue-fcfs/${userId}`;

    const response = http.post(url, null, {
        headers: { 'Content-Type': 'application/json' },
        tags: { name: 'CouponIssueSpikeTest' },
    });

    // 응답 검증
    check(response, {
        '상태 코드 확인': (r) => [202, 400, 409].includes(r.status),
    });

    // 결과 카운팅
    if (response.status === 202) {
        successCount++;
    } else if (response.status === 409) {
        duplicateCount++;
    } else if (response.status === 400 || response.body.includes('소진')) {
        stockOutCount++;
    } else {
        failureCount++;
    }

    sleep(0.1);
}

export function handleSummary(data) {
    const metrics = data.metrics;

    console.log('\n========================================');
    console.log('🔥 스파이크 테스트 결과');
    console.log('========================================\n');

    console.log('📊 요청 통계:');
    console.log(`   총 요청: ${metrics.http_reqs.values.count}건`);
    console.log(`   ✅ 성공 (202): ${successCount}건`);
    console.log(`   ⚠️  중복 (409): ${duplicateCount}건`);
    console.log(`   ❌ 재고 소진: ${stockOutCount}건`);
    console.log(`   ❌ 기타 실패: ${failureCount}건\n`);

    console.log('⏱️  응답 시간:');
    console.log(`   평균: ${metrics.http_req_duration.values.avg.toFixed(2)}ms`);
    console.log(`   최소: ${metrics.http_req_duration.values.min.toFixed(2)}ms`);
    console.log(`   최대: ${metrics.http_req_duration.values.max.toFixed(2)}ms`);
    console.log(`   중앙값 (p50): ${metrics.http_req_duration.values['p(50)'].toFixed(2)}ms`);
    console.log(`   90% (p90): ${metrics.http_req_duration.values['p(90)'].toFixed(2)}ms`);
    console.log(`   95% (p95): ${metrics.http_req_duration.values['p(95)'].toFixed(2)}ms`);
    console.log(`   99% (p99): ${metrics.http_req_duration.values['p(99)'].toFixed(2)}ms\n`);

    console.log('🚀 처리량:');
    console.log(`   평균 RPS: ${metrics.http_reqs.values.rate.toFixed(2)} req/s\n`);

    console.log('📈 단계별 요약:');
    console.log(`   Stage 1 (Smoke):  10 VUs`);
    console.log(`   Stage 2 (Load):   100 VUs`);
    console.log(`   Stage 3 (Stress): 200 VUs`);
    console.log(`   Stage 4 (Spike):  500 VUs ⭐\n`);

    console.log('========================================\n');

    return {
        'stdout': JSON.stringify(data, null, 2),
    };
}
