package com.railscale.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.railscale.common.event.EventEnvelope;
import com.railscale.common.event.KafkaTopics;
import com.railscale.payment.dto.PaymentResultResponse;
import com.railscale.payment.dto.ProcessPaymentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Simulated Payment Processor with failure injection (Architecture Section 32).
 * Emits PaymentCompleted or PaymentFailed events to Kafka.
 */
@Service
public class PaymentProcessorService {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessorService.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Random random = new Random();

    @Value("${railscale.payment.failure-rate:0.0}")
    private double failureRate;

    public PaymentProcessorService(
        @Autowired(required = false) KafkaTemplate<String, String> kafkaTemplate,
        ObjectMapper objectMapper
    ) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public PaymentResultResponse process(ProcessPaymentRequest request) {
        UUID paymentId = UUID.randomUUID();
        boolean failed = request.simulateFailure() || (failureRate > 0 && random.nextDouble() < failureRate);

        String status = failed ? "FAILED" : "SUCCESS";
        String message = failed ? "Payment declined by simulated bank" : "Payment approved successfully";

        log.info("Processing payment {}: bookingId={}, amount={}, status={}",
            paymentId, request.bookingId(), request.amount(), status);

        // Publish to Kafka if KafkaTemplate is available
        publishPaymentEvent(paymentId, request.bookingId(), request.amount(), status);

        return new PaymentResultResponse(
            paymentId,
            request.bookingId(),
            request.amount(),
            status,
            message,
            Instant.now()
        );
    }

    private void publishPaymentEvent(UUID paymentId, UUID bookingId, java.math.BigDecimal amount, String status) {
        if (kafkaTemplate == null) {
            log.debug("KafkaTemplate not available, skipping Kafka publish for payment {}", paymentId);
            return;
        }

        try {
            String eventType = "SUCCESS".equals(status) ? "PaymentCompleted" : "PaymentFailed";
            Map<String, Object> payloadMap = Map.of(
                "paymentId", paymentId.toString(),
                "bookingId", bookingId.toString(),
                "amount", amount.toString(),
                "status", status
            );

            String payloadJson = objectMapper.writeValueAsString(payloadMap);
            EventEnvelope envelope = new EventEnvelope(
                UUID.randomUUID(),
                eventType,
                bookingId,
                "Payment",
                Instant.now(),
                payloadJson
            );

            String envelopeJson = objectMapper.writeValueAsString(envelope);
            kafkaTemplate.send(KafkaTopics.PAYMENT_EVENTS, bookingId.toString(), envelopeJson);
            log.info("Published {} event for booking {}", eventType, bookingId);
        } catch (Exception e) {
            log.error("Failed to publish payment event to Kafka: {}", e.getMessage(), e);
        }
    }
}
