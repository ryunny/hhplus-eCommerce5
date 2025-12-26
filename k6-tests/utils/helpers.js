import { check } from 'k6';
import { Counter, Trend } from 'k6/metrics';

// 커스텀 메트릭
export const successCounter = new Counter('coupon_issue_success');
export const failureCounter = new Counter('coupon_issue_failure');
export const duplicateCounter = new Counter('coupon_issue_duplicate');
export const stockOutCounter = new Counter('coupon_issue_stock_out');
export const issueDuration = new Trend('coupon_issue_duration');

// HTTP 요청 헤더
export const headers = {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
};

// 응답 검증 헬퍼
export function validateCouponIssueResponse(response, expectedStatuses = [202, 400, 409]) {
    const checks = check(response, {
        '상태 코드가 예상 범위 내': (r) => expectedStatuses.includes(r.status),
        '응답 시간 < 1초': (r) => r.timings.duration < 1000,
        '응답 본문이 JSON': (r) => {
            try {
                JSON.parse(r.body);
                return true;
            } catch {
                return false;
            }
        },
    });

    // 상세 메트릭 기록
    if (response.status === 202) {
        successCounter.add(1);
        console.log(`✅ 쿠폰 발급 성공: ${response.body}`);
    } else if (response.status === 409) {
        duplicateCounter.add(1);
        console.log(`⚠️ 중복 발급: ${response.body}`);
    } else if (response.status === 400) {
        const body = JSON.parse(response.body);
        if (body.message && body.message.includes('재고')) {
            stockOutCounter.add(1);
            console.log(`❌ 재고 소진: ${response.body}`);
        } else {
            failureCounter.add(1);
            console.log(`❌ 발급 실패: ${response.body}`);
        }
    } else {
        failureCounter.add(1);
        console.log(`❌ 예상치 못한 응답: ${response.status} - ${response.body}`);
    }

    issueDuration.add(response.timings.duration);

    return checks;
}

// 응답 로깅 헬퍼
export function logResponse(response, context = '') {
    console.log(`[${context}] Status: ${response.status}, Duration: ${response.timings.duration}ms`);
    if (response.status >= 400) {
        console.log(`[${context}] Error Body: ${response.body}`);
    }
}

// 테스트 요약 출력
export function printTestSummary(data) {
    console.log('\n========== 테스트 요약 ==========');
    console.log(`총 요청 수: ${data.metrics.http_reqs.values.count}`);
    console.log(`성공 (202): ${data.metrics.coupon_issue_success ? data.metrics.coupon_issue_success.values.count : 0}`);
    console.log(`중복 (409): ${data.metrics.coupon_issue_duplicate ? data.metrics.coupon_issue_duplicate.values.count : 0}`);
    console.log(`재고 소진 (400): ${data.metrics.coupon_issue_stock_out ? data.metrics.coupon_issue_stock_out.values.count : 0}`);
    console.log(`기타 실패: ${data.metrics.coupon_issue_failure ? data.metrics.coupon_issue_failure.values.count : 0}`);
    console.log(`평균 응답 시간: ${data.metrics.http_req_duration.values.avg.toFixed(2)}ms`);
    console.log(`95% 응답 시간: ${data.metrics.http_req_duration.values['p(95)'].toFixed(2)}ms`);
    console.log('================================\n');
}