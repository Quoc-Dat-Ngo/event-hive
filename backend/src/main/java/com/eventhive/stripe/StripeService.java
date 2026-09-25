package com.eventhive.stripe;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.eventhive.exception.PaymentProcessingException;
import com.stripe.exception.StripeException;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;

@Service
public class StripeService {
    private static final Logger logger = LoggerFactory.getLogger(StripeService.class);

    /**
     * Fully refunds a payment intent. Safe to call again for the same intent (e.g. on a
     * webhook retry): the idempotency key makes Stripe return the original refund.
     */
    public void initiateRefund(String bookingId, String paymentIntentId, RefundCreateParams.Reason reason,
            String note) {
        RefundCreateParams params = RefundCreateParams.builder()
                .setPaymentIntent(paymentIntentId)
                .setReason(reason)
                .putMetadata("bookingId", bookingId)
                .putMetadata("note", note)
                .build();

        try {
            Refund refund = Refund.create(params, RequestOptions.builder()
                    .setIdempotencyKey("refund_" + paymentIntentId)
                    .build());
            logger.info("Refunded {} for booking {} (refund={}, note={})",
                    paymentIntentId, bookingId, refund.getId(), note);
        } catch (StripeException e) {
            throw new PaymentProcessingException("Refund failed for payment " + paymentIntentId, e);
        }
    }
}
