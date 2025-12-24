/**
 * 선착순 쿠폰 발급 동시성 테스트
 *
 * 테스트 목표:
 * 1. Redis + Kafka 기반 선착순 쿠폰 발급의 동시성 처리 능력 검증
 * 2. 재고 초과 발급 방지 확인
 * 3. 중복 발급 방지 확인
 * 4. 응답 시간 및 처리량 측정
 *
 * 실행 전 준비사항:
 * 1. MySQL, Redis, Kafka 서버 실행
 * 2. 애플리케이션 서버 실행 (포트 8080)
 * 3. DB에 테스트용 쿠폰 데이터 준비 (재고 100개 권장)
 * 4. DB에 테스트용 사용자 데이터 준비 (1~1000번)
 */

import http from 'k6/http';
import { sleep } from 'k6';
import { config, getRandomUserId } from '../config/env.js';
import { headers, validateCouponIssueResponse, printTestSummary } from '../utils/helpers.js';

/**
 * 테스트 시나리오 설정
 *
 * 4가지 시나리오를 순차적으로 실행:
 * 1. Smoke Test: 소수 사용자로 기본 동작 확인
 * 2. Load Test: 정상 부하 상황 테스트
 * 3. Stress Test: 시스템 한계 찾기
 * 4. Spike Test: 급격한 트래픽 증가 대응
 */
export const options = {
    scenarios: {
        // 1. Smoke Test: 소수 사용자로 기본 동작 확인
        smoke_test: {
            executor: 'constant-vus',
            vus: 10,                // 10명의 동시 사용자
            duration: '30s',        // 30초 동안
            startTime: '0s',
            tags: { test_type: 'smoke' },
        },

        // 2. Load Test: 정상 부하 상황 (쿠폰 재고 100개 가정)
        load_test: {
            executor: 'ramping-vus',
            startTime: '35s',       // smoke test 종료 5초 후 시작
            stages: [
                { duration: '30s', target: 50 },   // 30초 동안 50명까지 증가
                { duration: '1m', target: 100 },   // 1분 동안 100명 유지
                { duration: '30s', target: 0 },    // 30초 동안 0명으로 감소
            ],
            tags: { test_type: 'load' },
        },

        // 3. Stress Test: 시스템 한계 테스트
        stress_test: {
            executor: 'ramping-vus',
            startTime: '3m',        // 이전 테스트 종료 후 시작
            stages: [
                { duration: '1m', target: 100 },   // 1분 동안 100명까지
                { duration: '2m', target: 200 },   // 2분 동안 200명까지
                { duration: '1m', target: 300 },   // 1분 동안 300명까지
                { duration: '1m', target: 0 },     // 1분 동안 0명으로
            ],
            tags: { test_type: 'stress' },
        },

        // 4. Spike Test: 급격한 트래픽 증가 (실제 선착순 상황 시뮬레이션)
        spike_test: {
            executor: 'ramping-vus',
            startTime: '8m',
            stages: [
                { duration: '10s', target: 500 },  // 10초 만에 500명 급증
                { duration: '30s', target: 500 },  // 30초 동안 유지
                { duration: '10s', target: 0 },    // 10초 만에 0명으로
            ],
            tags: { test_type: 'spike' },
        },
    },

    thresholds: config.thresholds,
};

/**
 * 선착순 쿠폰 발급 API 호출
 */
export default function () {
    // 랜덤 사용자 ID 생성
    const userId = getRandomUserId();
    const url = `${config.baseUrl}/api/coupons/${config.couponId}/issue-fcfs/${userId}`;

    // POST 요청 (요청 바디 없음)
    const response = http.post(url, null, {
        headers: headers,
        timeout: config.timeout,
        tags: { name: 'CouponIssueFCFS' },
    });

    // 응답 검증
    validateCouponIssueResponse(response);

    // 짧은 대기 (다음 요청 전)
    sleep(0.1);
}

/**
 * 테스트 종료 시 요약 출력
 */
export function handleSummary(data) {
    printTestSummary(data);

    return {
        'stdout': JSON.stringify(data, null, 2),
        'summary.json': JSON.stringify(data, null, 2),
    };
}