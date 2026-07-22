package com.eventhive.seats;

import org.springframework.data.jpa.domain.Specification;

public class SeatSpecifications {
    public static Specification<Seat> hasRow(String seatRow) {
        return (root, query, cb) -> seatRow == null ? null : cb.equal(root.get("seatRow"), seatRow);
    }

    public static Specification<Seat> hasNumber(Integer number) {
        return (root, query, cb) -> number == null ? null : cb.equal(root.get("number"), number);
    }
}
