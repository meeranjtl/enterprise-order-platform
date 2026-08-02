package com.enterprise.order.payment.controller;

import com.enterprise.order.payment.dto.CreatePaymentRequest;
import com.enterprise.order.payment.dto.PaymentDTO;
import com.enterprise.order.payment.service.PaymentService;
import com.enterprise.order.shared.dto.BaseResponse;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @Operation(summary = "Initiate a payment")
    public ResponseEntity<BaseResponse<PaymentDTO>> create(
            @Valid @RequestBody CreatePaymentRequest request) {
        PaymentDTO payment = paymentService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(BaseResponse.success(payment, "Payment processed"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get payment status")
    public BaseResponse<PaymentDTO> get(@PathVariable Long id) {
        return BaseResponse.success(paymentService.get(id), "Payment retrieved successfully");
    }

    @PostMapping("/{id}/retry")
    @Operation(summary = "Retry a failed payment")
    public BaseResponse<PaymentDTO> retry(@PathVariable Long id) {
        return BaseResponse.success(paymentService.retry(id), "Payment retry processed");
    }

    @PostMapping("/{id}/refund")
    @Operation(summary = "Refund a completed payment")
    public BaseResponse<PaymentDTO> refund(@PathVariable Long id) {
        return BaseResponse.success(paymentService.refund(id), "Payment refunded");
    }
}
