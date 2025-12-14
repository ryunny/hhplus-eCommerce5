package com.hhplus.ecommerce.application.usecase.user;

import com.hhplus.ecommerce.application.command.ChargeBalanceCommand;
import com.hhplus.ecommerce.domain.entity.User;
import com.hhplus.ecommerce.domain.service.UserService;
import com.hhplus.ecommerce.domain.vo.Money;
import org.springframework.stereotype.Service;

/**
 * 잔액 충전 UseCase
 *
 * User Story: "사용자가 잔액을 충전한다"
 *
 * UseCase는 여러 Service를 조합하는 계층이므로 트랜잭션은 Service에서 관리합니다.
 */
@Service
public class ChargeBalanceUseCase {

    private final UserService userService;

    public ChargeBalanceUseCase(UserService userService) {
        this.userService = userService;
    }

    public User execute(ChargeBalanceCommand command) {
        Money amount = new Money(command.amount());
        return userService.chargeBalanceByPublicId(command.publicId(), amount);
    }
}
