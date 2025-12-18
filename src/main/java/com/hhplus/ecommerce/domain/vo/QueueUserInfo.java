package com.hhplus.ecommerce.domain.vo;

import com.hhplus.ecommerce.domain.enums.QueueStatus;

import java.time.Instant;

/**
 * 대기열 사용자 정보 VO
 *
 * Redis에 저장되는 사용자의 대기열 정보
 */
public record QueueUserInfo(
    Long userId,           // 사용자 ID
    Long queueNumber,      // 대기 번호
    QueueStatus status,    // 대기 상태
    Instant enteredAt      // 진입 시각
) {}
