package com.eventhive.payments;

import java.util.function.Function;

import org.springframework.stereotype.Service;

@Service
public class PaymentDTOMapper implements Function<Payment, PaymentDTO> {

    @Override
    public PaymentDTO apply(Payment p) {
        return new PaymentDTO(
                p.getId(),
                p.getStripePaymentIntentId(),
                p.getAmountCents(),
                p.getCurrency(),
                p.getStatus().name(),
                p.getPurchasedAt(),
                p.getRefundedAt(),
                p.getCreatedAt(),
                p.getUpdatedAt(),
                p.getBooking().getId());
    }

}
