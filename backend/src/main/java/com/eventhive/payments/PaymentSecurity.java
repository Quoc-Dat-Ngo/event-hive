package com.eventhive.payments;

import java.util.UUID;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component("paymentSecurity")
@RequiredArgsConstructor
public class PaymentSecurity {
    private final PaymentRepository paymentRepository;

    public boolean isOwner(UUID paymentId, UUID principalId) {
        return paymentRepository.getBookingUserId(paymentId)
                .map(id -> id.equals(principalId))
                .orElse(false);
    }
}
