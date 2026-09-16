package com.eventhive.payments;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.eventhive.bookings.Booking;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "payments", uniqueConstraints = {
        @UniqueConstraint(name = "unique_stripe_payment_intent_id", columnNames = "stripe_payment_intent_id")
})
@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String stripePaymentIntentId;

    @Column(nullable = false)
    private Long amountCents;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(value = EnumType.STRING)
    @Column(nullable = false, length = 50)
    private PaymentStatus status;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant purchasedAt;

    private Instant refundedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    private Instant updatedAt;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booking_id", nullable = false, unique = true)
    private Booking booking;

    public Payment(String stripePaymentIntentId, Long amountCents, String currency, PaymentStatus status,
            Booking booking) {
        this.stripePaymentIntentId = stripePaymentIntentId;
        this.amountCents = amountCents;
        this.currency = currency;
        this.status = status;
        this.booking = booking;
    }
}
