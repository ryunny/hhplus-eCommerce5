package com.hhplus.ecommerce.presentation.controller;

import com.hhplus.ecommerce.application.command.ChargeBalanceCommand;
import com.hhplus.ecommerce.application.query.GetBalanceQuery;
import com.hhplus.ecommerce.application.usecase.user.ChargeBalanceUseCase;
import com.hhplus.ecommerce.application.usecase.user.GetBalanceUseCase;
import com.hhplus.ecommerce.domain.entity.User;
import com.hhplus.ecommerce.presentation.dto.ChargeBalanceRequest;
import com.hhplus.ecommerce.presentation.dto.UserBalanceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final ChargeBalanceUseCase chargeBalanceUseCase;
    private final GetBalanceUseCase getBalanceUseCase;

    @PostMapping("/{publicId}/balance/charge")
    public ResponseEntity<UserBalanceResponse> chargeBalance(
            @PathVariable String publicId,
            @RequestBody ChargeBalanceRequest request) {
        ChargeBalanceCommand command = new ChargeBalanceCommand(publicId, request.amount());
        User user = chargeBalanceUseCase.execute(command);
        return ResponseEntity.ok(new UserBalanceResponse(user.getPublicId(), user.getBalance().getAmount()));
    }

    @GetMapping("/{publicId}/balance")
    public ResponseEntity<UserBalanceResponse> getBalance(@PathVariable String publicId) {
        GetBalanceQuery query = new GetBalanceQuery(publicId);
        User user = getBalanceUseCase.execute(query);
        return ResponseEntity.ok(new UserBalanceResponse(user.getPublicId(), user.getBalance().getAmount()));
    }
}
