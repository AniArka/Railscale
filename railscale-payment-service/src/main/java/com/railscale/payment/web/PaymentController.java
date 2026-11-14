package com.railscale.payment.web;

import com.railscale.payment.dto.PaymentResultResponse;
import com.railscale.payment.dto.ProcessPaymentRequest;
import com.railscale.payment.service.PaymentProcessorService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentProcessorService paymentProcessorService;

    public PaymentController(PaymentProcessorService paymentProcessorService) {
        this.paymentProcessorService = paymentProcessorService;
    }

    @PostMapping("/process")
    public ResponseEntity<PaymentResultResponse> processPayment(
        @Valid @RequestBody ProcessPaymentRequest request
    ) {
        PaymentResultResponse result = paymentProcessorService.process(request);
        return ResponseEntity.ok(result);
    }
}
