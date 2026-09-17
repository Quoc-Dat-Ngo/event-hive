package com.eventhive.stripe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;

@Service
public class StripeService {
    private static final Logger logger = LoggerFactory.getLogger(StripeService.class);

    public void initiateRefund(String bookingId, Session session) {
        RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(session.getPaymentIntent())
                .setReason(RefundCreateParams.Reason.DUPLICATE)
                .putMetadata("bookingId", bookingId)
                .putMetadata("reason", "seat_lost_during_checkout_window")
                .build();

        try {
            Refund refund = Refund.create(params, RequestOptions.builder()
                    .setIdempotencyKey("idem_" + session.getPaymentIntent())
                    .build());
            logger.info("Refunded {} for booking {} (refund={})",
                    session.getPaymentIntent(), bookingId, refund.getId());

        } catch (Exception e) {
            throw new RuntimeException("Refund failed", e);
        }
    }
}
