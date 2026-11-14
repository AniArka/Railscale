package com.railscale.payment;

import com.railscale.payment.dto.PaymentResultResponse;
import com.railscale.payment.dto.ProcessPaymentRequest;
import com.railscale.payment.service.PaymentProcessorService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PaymentServiceApplication.class)
@ActiveProfiles("test")
class PaymentServiceTest {

    @Autowired
    private PaymentProcessorService paymentProcessorService;

    @Test
    @DisplayName("Should successfully process payment when failure not simulated")
    void testSuccessfulPayment() {
        ProcessPaymentRequest request = new ProcessPaymentRequest(
            UUID.randomUUID(), new BigDecimal("250.00"), false
        );

        PaymentResultResponse result = paymentProcessorService.process(request);

        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(result.amount()).isEqualTo(new BigDecimal("250.00"));
        assertThat(result.paymentId()).isNotNull();
    }

    @Test
    @DisplayName("Should inject failure when simulated")
    void testFailedPayment() {
        ProcessPaymentRequest request = new ProcessPaymentRequest(
            UUID.randomUUID(), new BigDecimal("100.00"), true
        );

        PaymentResultResponse result = paymentProcessorService.process(request);

        assertThat(result.status()).isEqualTo("FAILED");
    }
}
