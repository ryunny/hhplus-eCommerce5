package com.hhplus.ecommerce.application.command;

/**
 * 잔액 충전 Command
 */
public record ChargeBalanceCommand(
        String publicId,
        Long amount
) {
}
