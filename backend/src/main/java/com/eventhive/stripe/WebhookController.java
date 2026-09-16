package com.eventhive.stripe;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eventhive.bookings.BookingService;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/stripe/webhooks")
@RequiredArgsConstructor
public class WebhookController {
    private static final Logger logger = LoggerFactory.getLogger(WebhookController.class);

    private final BookingService bookingService;

    @Value("${stripe.webhook.signing}")
    private String signingKey;

    @PostMapping
    public ResponseEntity<String> handleWebhookEvent(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String header) {
        logger.info("WEBHOOK ENTRY POINT HIT");

        try {
            logger.info("Payload size: {} bytes", payload.length());
            logger.info("Signing key present: {}", signingKey != null);

            Event event = Webhook.constructEvent(payload, header, signingKey);

            // Log the key fields
            logger.info("Event ID: {}", event.getId());
            logger.info("Event Type: {}", event.getType());
            logger.info("Created: {}", event.getCreated());
            logger.info("Live Mode: {}", event.getLivemode());

            // Log the data object (the actual resource)
            logger.info("Data: {}", event.getData());

            // For debugging, just log the raw payload
            logger.info("Full Payload:\n{}", payload);

            switch (event.getType()) {
                case "checkout.session.completed" -> {
                    Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);
                    if (session != null) {
                        Map<String, String> metadata = session.getMetadata();
                        String bookingId = metadata.get("bookingId");
                        bookingService.handleSuccessPayment(bookingId, session);
                    }
                }
                case "checkout.session.expired" -> {
                    Session session = (Session) event.getDataObjectDeserializer().getObject().orElse(null);

                    if (session != null) {
                        Map<String, String> metadata = session.getMetadata();
                        String bookingId = metadata.get("bookingId");
                        bookingService.handleExpiredPayment(bookingId);
                    }
                }
                default -> logger.info("Ignoring unhandled event type: {}", event.getType());
            }

        } catch (Exception e) {
            logger.error("EXCEPTION IN WEBHOOK", e);
            throw new RuntimeException("Webhook failed", e);
        }

        return ResponseEntity.ok("ok");
    }
}
