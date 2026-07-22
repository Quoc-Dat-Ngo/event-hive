package com.eventhive.seats;

import java.util.function.Function;

import org.springframework.stereotype.Service;

@Service
public class SeatDTOMapper implements Function<Seat, SeatDTO> {

    @Override
    public SeatDTO apply(Seat s) {
        return new SeatDTO(s.getId(), s.getSeatRow(), s.getNumber(), s.getCreatedAt(), s.getUpdatedAt(),
                s.getVenue().getId());
    }

}
