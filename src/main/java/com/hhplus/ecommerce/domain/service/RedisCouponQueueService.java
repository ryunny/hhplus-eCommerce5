package com.hhplus.ecommerce.domain.service;

import com.hhplus.ecommerce.domain.entity.Coupon;
import com.hhplus.ecommerce.domain.entity.User;
import com.hhplus.ecommerce.domain.entity.UserCoupon;
import com.hhplus.ecommerce.domain.enums.CouponStatus;
import com.hhplus.ecommerce.domain.repository.CouponRepository;
import com.hhplus.ecommerce.domain.repository.UserCouponRepository;
import com.hhplus.ecommerce.infrastructure.redis.RedisKeyGenerator;
import com.hhplus.ecommerce.presentation.dto.CouponQueueResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StreamUtils;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class RedisCouponQueueService {

    private final RedisTemplate<String, String> redisTemplate;
    private final CouponRepository couponRepository;
    private final UserCouponRepository userCouponRepository;
    private final UserService userService;

    private DefaultRedisScript<List> moveToProcessingScript;
    private DefaultRedisScript<Long> removeFromProcessingScript;

    public RedisCouponQueueService(RedisTemplate<String, String> redisTemplate,
                                   CouponRepository couponRepository,
                                   UserCouponRepository userCouponRepository,
                                   UserService userService) {
        this.redisTemplate = redisTemplate;
        this.couponRepository = couponRepository;
        this.userCouponRepository = userCouponRepository;
        this.userService = userService;
    }

    @PostConstruct
    public void init() {
        try {
            String moveScript = loadScript("scripts/move_to_processing.lua");
            moveToProcessingScript = new DefaultRedisScript<>();
            moveToProcessingScript.setScriptText(moveScript);
            moveToProcessingScript.setResultType(List.class);

            String removeScript = loadScript("scripts/remove_from_processing.lua");
            removeFromProcessingScript = new DefaultRedisScript<>();
            removeFromProcessingScript.setScriptText(removeScript);
            removeFromProcessingScript.setResultType(Long.class);
        } catch (IOException e) {
            log.error("Lua 스크립트 로드 실패", e);
            throw new RuntimeException("Lua 스크립트 로드 실패", e);
        }
    }

    private String loadScript(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        return StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
    }

    public CouponQueueResponse joinQueue(Long userId, Long couponId) {
        User user = userService.getUser(userId);
        Coupon coupon = getCoupon(couponId);

        Optional<UserCoupon> existingCoupon = userCouponRepository.findByUserIdAndCouponId(userId, couponId);
        if (existingCoupon.isPresent()) {
            throw new IllegalStateException("이미 발급받은 쿠폰입니다.");
        }

        String queueKey = RedisKeyGenerator.couponQueue(couponId);
        String member = userId.toString();
        double score = System.currentTimeMillis();

        Boolean added = redisTemplate.opsForZSet().add(queueKey, member, score);

        if (added == null || !added) {
            Integer position = getQueuePosition(userId, couponId);
            return new CouponQueueResponse(couponId, position, getTotalWaiting(couponId));
        }

        redisTemplate.expire(queueKey, 24, TimeUnit.HOURS);
        Integer position = getQueuePosition(userId, couponId);

        return new CouponQueueResponse(couponId, position, getTotalWaiting(couponId));
    }

    public Integer getQueuePosition(Long userId, Long couponId) {
        String queueKey = RedisKeyGenerator.couponQueue(couponId);
        String member = userId.toString();

        Long rank = redisTemplate.opsForZSet().rank(queueKey, member);

        if (rank == null) {
            throw new IllegalStateException("대기열에 진입하지 않았습니다.");
        }

        return rank.intValue() + 1;
    }

    public CouponQueueResponse getQueueStatus(Long userId, Long couponId) {
        Integer position = getQueuePosition(userId, couponId);
        Long totalWaiting = getTotalWaiting(couponId);

        return new CouponQueueResponse(couponId, position, totalWaiting);
    }

    public Long getTotalWaiting(Long couponId) {
        String queueKey = RedisKeyGenerator.couponQueue(couponId);
        Long size = redisTemplate.opsForZSet().size(queueKey);
        return size != null ? size : 0L;
    }

    public int processBatch(Long couponId, int batchSize) {
        String queueKey = RedisKeyGenerator.couponQueue(couponId);
        String processingKey = RedisKeyGenerator.couponQueueProcessing(couponId);

        List<String> members = redisTemplate.execute(
                moveToProcessingScript,
                Arrays.asList(queueKey, processingKey),
                String.valueOf(batchSize)
        );

        if (members == null || members.isEmpty()) {
            return 0;
        }

        int successCount = 0;

        for (int i = 0; i < members.size(); i += 2) {
            String member = members.get(i);
            if (member == null) continue;

            Long userId = extractUserId(member);

            try {
                issueAndRemoveFromProcessing(userId, couponId, member);
                successCount++;
            } catch (IllegalStateException e) {
                log.warn("쿠폰 발급 실패: userId={}, couponId={}, reason={}", userId, couponId, e.getMessage());
                removeFromProcessing(processingKey, member);
            } catch (Exception e) {
                log.error("쿠폰 발급 예외: userId={}, couponId={}", userId, couponId, e);
            }
        }

        return successCount;
    }

    private void removeFromProcessing(String processingKey, String member) {
        redisTemplate.execute(
                removeFromProcessingScript,
                Arrays.asList(processingKey),
                member
        );
    }

    @Transactional
    protected void issueAndRemoveFromProcessing(Long userId, Long couponId, String member) {
        Optional<UserCoupon> existing = userCouponRepository.findByUserIdAndCouponId(userId, couponId);
        if (existing.isPresent()) {
            throw new IllegalStateException("이미 발급받은 쿠폰입니다.");
        }

        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다: " + couponId));

        if (!coupon.isIssuable()) {
            throw new IllegalStateException("쿠폰의 모든 수량이 소진되었습니다.");
        }

        User user = userService.getUser(userId);

        coupon.increaseIssuedQuantity();
        couponRepository.save(coupon);

        UserCoupon userCoupon = new UserCoupon(
                user,
                coupon,
                CouponStatus.UNUSED,
                coupon.getEndDate()
        );
        userCouponRepository.save(userCoupon);

        String processingKey = RedisKeyGenerator.couponQueueProcessing(couponId);
        removeFromProcessing(processingKey, member);
    }

    @Deprecated
    @Transactional
    protected void issueAndRemove(Long userId, Long couponId, String member) {
        Optional<UserCoupon> existing = userCouponRepository.findByUserIdAndCouponId(userId, couponId);
        if (existing.isPresent()) {
            throw new IllegalStateException("이미 발급받은 쿠폰입니다.");
        }

        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다: " + couponId));

        if (!coupon.isIssuable()) {
            throw new IllegalStateException("쿠폰의 모든 수량이 소진되었습니다.");
        }

        User user = userService.getUser(userId);

        coupon.increaseIssuedQuantity();
        couponRepository.save(coupon);

        UserCoupon userCoupon = new UserCoupon(
                user,
                coupon,
                CouponStatus.UNUSED,
                coupon.getEndDate()
        );
        userCouponRepository.save(userCoupon);

        String queueKey = RedisKeyGenerator.couponQueue(couponId);
        redisTemplate.opsForZSet().remove(queueKey, member);
    }

    public void clearQueue(Long couponId) {
        String queueKey = RedisKeyGenerator.couponQueue(couponId);
        redisTemplate.delete(queueKey);
    }

    private Long extractUserId(String member) {
        return Long.parseLong(member);
    }

    private Coupon getCoupon(Long couponId) {
        return couponRepository.findById(couponId)
                .orElseThrow(() -> new IllegalArgumentException("쿠폰을 찾을 수 없습니다: " + couponId));
    }
}