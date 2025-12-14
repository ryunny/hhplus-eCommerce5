package com.hhplus.ecommerce.infrastructure.lock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

/**
 * 분산락 AOP
 *
 * @DistributedLock 어노테이션이 선언된 메서드에 자동으로 분산락을 적용합니다.
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class DistributedLockAspect {

    private final RedissonClient redissonClient;
    private final ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(distributedLock)")
    public Object around(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        String lockKey = generateLockKey(joinPoint, distributedLock.key());
        RLock lock = getLock(lockKey, distributedLock.lockType());

        boolean isLocked = false;
        try {
            isLocked = lock.tryLock(
                    distributedLock.waitTime(),
                    distributedLock.leaseTime(),
                    distributedLock.timeUnit()
            );

            if (!isLocked) {
                log.warn("락 획득 실패: key={}", lockKey);
                throw new IllegalStateException(distributedLock.failureMessage());
            }

            log.debug("락 획득 성공: key={}", lockKey);
            return joinPoint.proceed();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("락 대기 중 인터럽트 발생", e);
        } finally {
            if (isLocked && lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("락 해제: key={}", lockKey);
            }
        }
    }

    private String generateLockKey(ProceedingJoinPoint joinPoint, String keyExpression) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] parameterNames = signature.getParameterNames();
        Object[] args = joinPoint.getArgs();

        StandardEvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < parameterNames.length; i++) {
            context.setVariable(parameterNames[i], args[i]);
        }

        return parser.parseExpression(keyExpression).getValue(context, String.class);
    }

    private RLock getLock(String lockKey, LockType lockType) {
        return switch (lockType) {
            case REDISSON_LOCK -> redissonClient.getLock(lockKey);
            case REDISSON_SPIN_LOCK -> redissonClient.getSpinLock(lockKey);
            case REDIS_PUB_SUB_LOCK -> redissonClient.getLock(lockKey);
        };
    }
}
