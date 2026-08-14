package com.eventhive.bookings;

import java.util.UUID;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component("bookingSecurity")
@RequiredArgsConstructor
public class BookingSecurity {
    private final BookingRepository bookingRepository;

    public boolean isOwner(UUID bookingId, String principalIdString) {
        UUID principalId = UUID.fromString(principalIdString);
        return bookingRepository.getUserId(bookingId)
                .map(id -> id.equals(principalId))
                .orElse(false);
    }
}
