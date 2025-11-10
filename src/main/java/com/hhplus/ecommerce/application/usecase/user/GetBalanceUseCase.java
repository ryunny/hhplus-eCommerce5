package com.hhplus.ecommerce.application.usecase.user;

import com.hhplus.ecommerce.application.query.GetBalanceQuery;
import com.hhplus.ecommerce.domain.entity.User;
import com.hhplus.ecommerce.domain.service.UserService;
import org.springframework.stereotype.Service;

/**
 * 잔액 조회 UseCase
 *
 * User Story: "사용자가 잔액을 조회한다"
 */
@Service
public class GetBalanceUseCase {

    private final UserService userService;

    public GetBalanceUseCase(UserService userService) {
        this.userService = userService;
    }

    public User execute(GetBalanceQuery query) {
        return userService.getUserByPublicId(query.publicId());
    }
}
