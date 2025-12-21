package com.hhplus.ecommerce.presentation.dto;

/**
 * 대기열 진입 응답 DTO
 */
public record QueueEntryResponse(
    Long queueNumber,          // 내 대기 번호
    Long waitingCount,         // 내 앞에 대기 중인 사용자 수
    Long estimatedWaitSeconds, // 예상 대기 시간 (초)
    String message             // 안내 메시지
) {}
