-- k6 성능 테스트를 위한 테스트 데이터 준비 스크립트
-- MySQL 데이터베이스에 접속하여 실행

-- ============================================
-- 1. 테스트용 쿠폰 생성
-- ============================================

-- 선착순 테스트용 쿠폰 (재고 100개)
INSERT INTO coupon (
    name,
    description,
    discount_type,
    discount_value,
    minimum_order_amount,
    maximum_discount_amount,
    quantity,
    issued_count,
    start_date,
    end_date,
    status,
    created_at,
    updated_at
)
VALUES (
    'k6 테스트 선착순 쿠폰 100개',
    'k6 성능 테스트용 쿠폰 - 재고 100개',
    'FIXED',
    5000,
    10000,
    5000,
    100,
    0,
    NOW(),
    DATE_ADD(NOW(), INTERVAL 30 DAY),
    'ACTIVE',
    NOW(),
    NOW()
);

-- 대규모 테스트용 쿠폰 (재고 1000개)
INSERT INTO coupon (
    name,
    description,
    discount_type,
    discount_value,
    minimum_order_amount,
    maximum_discount_amount,
    quantity,
    issued_count,
    start_date,
    end_date,
    status,
    created_at,
    updated_at
)
VALUES (
    'k6 테스트 선착순 쿠폰 1000개',
    'k6 성능 테스트용 쿠폰 - 재고 1000개',
    'FIXED',
    3000,
    5000,
    3000,
    1000,
    0,
    NOW(),
    DATE_ADD(NOW(), INTERVAL 30 DAY),
    'ACTIVE',
    NOW(),
    NOW()
);

-- 생성된 쿠폰 확인
SELECT id, name, quantity, issued_count, status
FROM coupon
WHERE name LIKE 'k6 테스트%'
ORDER BY id DESC;

-- ============================================
-- 2. 테스트용 사용자 생성 (1~1000번)
-- ============================================

-- 참고: 실제 테이블 스키마에 맞게 컬럼명 수정 필요
-- 예시: users 테이블이 (id, public_id, name, balance, created_at, updated_at) 구조라고 가정

-- 테스트용 사용자 1000명 생성
INSERT INTO users (public_id, name, balance, created_at, updated_at)
SELECT
    n AS public_id,
    CONCAT('k6_test_user_', n) AS name,
    1000000 AS balance,  -- 100만원 잔액
    NOW() AS created_at,
    NOW() AS updated_at
FROM (
    SELECT (@row := @row + 1) AS n
    FROM
        (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
         UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t1,
        (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
         UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t2,
        (SELECT 0 UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
         UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9) t3,
        (SELECT @row := 0) init
    LIMIT 1000
) numbers;

-- 생성된 사용자 확인
SELECT COUNT(*) AS total_users
FROM users
WHERE name LIKE 'k6_test_user_%';

-- 사용자 샘플 확인
SELECT id, public_id, name, balance
FROM users
WHERE name LIKE 'k6_test_user_%'
ORDER BY public_id
LIMIT 10;

-- ============================================
-- 3. 기존 테스트 데이터 정리 (재테스트 시)
-- ============================================

-- 기존 테스트 쿠폰 발급 기록 삭제
DELETE FROM user_coupon
WHERE coupon_id IN (
    SELECT id FROM coupon WHERE name LIKE 'k6 테스트%'
);

-- 쿠폰 발급 카운트 초기화
UPDATE coupon
SET issued_count = 0,
    updated_at = NOW()
WHERE name LIKE 'k6 테스트%';

-- 확인
SELECT id, name, quantity, issued_count
FROM coupon
WHERE name LIKE 'k6 테스트%';

-- ============================================
-- 4. Redis 명령어 (별도 실행 필요)
-- ============================================

-- Redis CLI에서 실행할 명령어들:
/*

# Redis 컨테이너 접속
docker exec -it <redis_container_name> redis-cli

# 쿠폰 재고 설정 (쿠폰 ID 확인 후 설정)
# 예시: 쿠폰 ID가 1인 경우
SET coupon:stock:1 100
GET coupon:stock:1

# 쿠폰 ID가 2인 경우
SET coupon:stock:2 1000
GET coupon:stock:2

# 기존 발급 기록 삭제 (재테스트 시)
KEYS "coupon:issued:1:*"
# 패턴 매칭된 키들을 확인한 후 삭제
# 방법 1: 하나씩 삭제
DEL coupon:issued:1:1
DEL coupon:issued:1:2
...

# 방법 2: 스크립트로 일괄 삭제 (Bash/PowerShell)
# Bash:
redis-cli KEYS "coupon:issued:1:*" | xargs redis-cli DEL

# PowerShell:
docker exec <redis_container> redis-cli KEYS "coupon:issued:1:*" | ForEach-Object { docker exec <redis_container> redis-cli DEL $_ }

# 멱등성 키 삭제 (재테스트 시)
KEYS "coupon:idempotency:*"
# 위와 동일한 방법으로 삭제

# 모든 관련 키 확인
KEYS "coupon:*"

*/

-- ============================================
-- 5. 테스트 후 검증 쿼리
-- ============================================

-- 쿠폰 발급 결과 확인
SELECT
    c.id AS coupon_id,
    c.name,
    c.quantity AS total_quantity,
    c.issued_count AS db_issued_count,
    COUNT(uc.id) AS actual_issued_count,
    (c.quantity - COUNT(uc.id)) AS remaining
FROM coupon c
LEFT JOIN user_coupon uc ON c.id = uc.coupon_id
WHERE c.name LIKE 'k6 테스트%'
GROUP BY c.id, c.name, c.quantity, c.issued_count;

-- 중복 발급 확인 (있으면 안 됨!)
SELECT
    user_id,
    coupon_id,
    COUNT(*) AS duplicate_count
FROM user_coupon
WHERE coupon_id IN (SELECT id FROM coupon WHERE name LIKE 'k6 테스트%')
GROUP BY user_id, coupon_id
HAVING COUNT(*) > 1;

-- 사용자별 발급 내역
SELECT
    u.public_id,
    u.name,
    c.name AS coupon_name,
    uc.issued_at
FROM user_coupon uc
JOIN users u ON uc.user_id = u.id
JOIN coupon c ON uc.coupon_id = c.id
WHERE c.name LIKE 'k6 테스트%'
ORDER BY uc.issued_at
LIMIT 20;

-- ============================================
-- 6. 정리 (테스트 완료 후)
-- ============================================

-- 테스트 데이터 완전 삭제 (주의!)
/*
DELETE FROM user_coupon
WHERE coupon_id IN (SELECT id FROM coupon WHERE name LIKE 'k6 테스트%');

DELETE FROM coupon
WHERE name LIKE 'k6 테스트%';

DELETE FROM users
WHERE name LIKE 'k6_test_user_%';
*/