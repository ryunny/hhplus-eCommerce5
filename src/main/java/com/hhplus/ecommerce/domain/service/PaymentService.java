package com.hhplus.ecommerce.domain.service;

import com.hhplus.ecommerce.domain.entity.Order;
import com.hhplus.ecommerce.domain.entity.Payment;
import com.hhplus.ecommerce.domain.enums.DataTransmissionStatus;
import com.hhplus.ecommerce.domain.enums.PaymentStatus;
import com.hhplus.ecommerce.domain.repository.PaymentRepository;
import com.hhplus.ecommerce.domain.vo.Money;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 관련 비즈니스 로직을 처리하는 서비스
 */
@Slf4j
@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentService(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    /**
     * 결제 생성
     *
     * @param order 주문
     * @param amount 결제 금액
     * @return 생성된 결제
     */
    public Payment createPayment(Order order, Money amount) {
        Payment payment = new Payment(
                order,
                amount,
                PaymentStatus.COMPLETED,
                DataTransmissionStatus.PENDING
        );
        return paymentRepository.save(payment);
    }

    /**
     * 데이터 플랫폼 전송
     * 외부 시스템에 결제 데이터를 전송합니다.
     *
     * @param payment 결제
     */
    @Transactional
    public void sendToDataPlatform(Payment payment) {
        try {
            // 실제 환경에서는 외부 API 호출 등의 로직이 들어갑니다.
            // 현재는 시뮬레이션으로 성공 처리
            payment.updateDataTransmissionStatus(DataTransmissionStatus.SUCCESS);
            // 더티 체킹으로 자동 저장 (save() 불필요)
            log.info("데이터 플랫폼 전송 성공: 결제 ID={}", payment.getId());
        } catch (Exception e) {
            payment.updateDataTransmissionStatus(DataTransmissionStatus.FAILED);
            // 더티 체킹으로 자동 저장 (save() 불필요)
            log.error("데이터 플랫폼 전송 실패: 결제 ID={}", payment.getId(), e);
        }
    }
}
