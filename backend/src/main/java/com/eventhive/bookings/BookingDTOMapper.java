package com.eventhive.bookings;

import java.util.function.Function;

import org.springframework.stereotype.Service;

@Service
public class BookingDTOMapper implements Function<Booking, BookingDTO> {

    @Override
    public BookingDTO apply(Booking b) {
        return new BookingDTO(
                b.getId(),
                b.getPriceCents(),
                b.getStatus().name(),
                b.getCreatedAt(),
                b.getUpdatedAt(),
                b.getUser().getId(),
                b.getEvent().getId(),
                b.getSeat().getId());
    }

}
