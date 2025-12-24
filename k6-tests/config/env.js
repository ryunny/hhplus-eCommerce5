// k6 테스트 환경 설정
export const config = {
    // API 서버 설정
    baseUrl: __ENV.BASE_URL || 'http://localhost:8080',

    // 테스트 데이터
    // 실제 DB에 존재하는 쿠폰 ID로 변경 필요
    couponId: __ENV.COUPON_ID || '1',

    // 테스트할 사용자 범위
    // 실제 DB에 존재하는 사용자 publicId 범위로 변경 필요
    userIdStart: parseInt(__ENV.USER_ID_START || '1'),
    userIdEnd: parseInt(__ENV.USER_ID_END || '1000'),

    // 대기열 설정 (대기열 테스트 시 사용)
    queueId: __ENV.QUEUE_ID || '1',

    // 상품 설정 (주문 테스트 시 사용)
    productId: __ENV.PRODUCT_ID || '1',

    // 타임아웃 설정
    timeout: '30s',

    // 임계값 설정
    thresholds: {
        // HTTP 요청 실패율 < 5%
        http_req_failed: ['rate<0.05'],
        // 95%의 요청이 1초 이내 응답
        http_req_duration: ['p(95)<1000'],
        // 99%의 요청이 2초 이내 응답
        'http_req_duration{expected_response:true}': ['p(99)<2000'],
    }
};

// 사용자 ID 생성 (랜덤)
export function getRandomUserId() {
    return Math.floor(Math.random() * (config.userIdEnd - config.userIdStart + 1)) + config.userIdStart;
}

// 사용자 ID 생성 (순차)
export function getSequentialUserId(index) {
    return config.userIdStart + (index % (config.userIdEnd - config.userIdStart + 1));
}