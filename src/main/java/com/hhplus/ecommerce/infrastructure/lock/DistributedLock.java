package com.hhplus.ecommerce.infrastructure.lock;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * 분산락 AOP 어노테이션
 *
 * 메서드에 선언적으로 분산락을 적용할 수 있습니다.
 *
 * 사용 예시:
 * <pre>
 * {@code
 * @DistributedLock(
 *     key = "#couponId",
 *     lockType = LockType.REDISSON_LOCK,
 *     waitTime = 3,
 *     leaseTime = 5
 * )
 * public void issueCoupon(Long couponId, String userId) {
 *     // 비즈니스 로직
 * }
 * }
 * </pre>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {

    /**
     * 락 키 (SpEL 표현식 지원)
     * 예: "#couponId", "'coupon:' + #couponId"
     */
    String key();

    /**
     * 락 타입
     */
    LockType lockType() default LockType.REDISSON_LOCK;

    /**
     * 락 획득 대기 시간 (초)
     */
    long waitTime() default 3L;

    /**
     * 락 유지 시간 (초)
     */
    long leaseTime() default 5L;

    /**
     * 시간 단위
     */
    TimeUnit timeUnit() default TimeUnit.SECONDS;

    /**
     * 락 획득 실패 시 예외 메시지
     */
    String failureMessage() default "락 획득에 실패했습니다.";
}
