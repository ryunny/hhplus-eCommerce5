package com.hhplus.ecommerce.presentation.dto;

/**
 * 대기열 상태 조회 응답 DTO
 */
public record QueueStatusResponse(
    Long queueNumber,          // 내 대기 번호
    Long waitingCount,         // 내 앞에 대기 중인 사용자 수
    String status,             // 대기 상태 (WAITING, PROCESSING, COMPLETED, FAILED)
    Long estimatedWaitSeconds  // 예상 대기 시간 (초)
) {}
