/**
 * 선착순 쿠폰 발급 간단 테스트
 *
 * 빠르게 테스트해보기 위한 간단한 버전
 * 100명의 사용자가 동시에 쿠폰 발급 요청
 */

import http from 'k6/http';
import { check, sleep } from 'k6';
import { config, getSequentialUserId } from '../config/env.js';
import { headers, successCounter, failureCounter, duplicateCounter, stockOutCounter } from '../utils/helpers.js';

export const options = {
    vus: 100,               // 100명의 동시 사용자
    duration: '10s',        // 10초 동안 실행
    thresholds: {
        http_req_failed: ['rate<0.10'],  // 실패율 < 10%
        http_req_duration: ['p(95)<2000'], // 95% 요청 2초 이내
    },
};

export default function () {
    // VU 번호를 기반으로 순차적 사용자 ID 생성 (중복 방지)
    const userId = getSequentialUserId(__VU);
    const url = `${config.baseUrl}/api/coupons/${config.couponId}/issue-fcfs/${userId}`;

    const response = http.post(url, null, {
        headers: headers,
        tags: { name: 'CouponIssueFCFS_Simple' },
    });

    // 응답 검증
    const isSuccess = check(response, {
        '상태 코드 확인': (r) => [202, 400, 409].includes(r.status),
    });

    // 결과 카운팅
    if (response.status === 202) {
        successCounter.add(1);
    } else if (response.status === 409) {
        duplicateCounter.add(1);
    } else if (response.status === 400) {
        stockOutCounter.add(1);
    } else {
        failureCounter.add(1);
    }

    sleep(0.5);
}

export function handleSummary(data) {
    const successCount = data.metrics.coupon_issue_success?.values.count || 0;
    const duplicateCount = data.metrics.coupon_issue_duplicate?.values.count || 0;
    const stockOutCount = data.metrics.coupon_issue_stock_out?.values.count || 0;
    const failureCount = data.metrics.coupon_issue_failure?.values.count || 0;

    console.log('\n========== 간단 테스트 결과 ==========');
    console.log(`✅ 성공: ${successCount}건`);
    console.log(`⚠️  중복: ${duplicateCount}건`);
    console.log(`❌ 재고 소진: ${stockOutCount}건`);
    console.log(`❌ 기타 실패: ${failureCount}건`);
    console.log(`평균 응답시간: ${data.metrics.http_req_duration.values.avg.toFixed(2)}ms`);
    console.log('====================================\n');

    return {
        'stdout': JSON.stringify(data, null, 2),
    };
}