package com.eventhive.payments;

import java.util.UUID;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component("paymentSecurity")
@RequiredArgsConstructor
public class PaymentSecurity {
    private final PaymentRepository paymentRepository;

    public boolean isOwner(UUID paymentId, String principalIdString) {
        UUID principalId = UUID.fromString(principalIdString);
        var result = paymentRepository.getBookingUserId(paymentId)
                .map(id -> id.equals(principalId))
                .orElse(false);
        return result;
    }
}
