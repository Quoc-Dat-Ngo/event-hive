package com.eventhive.seats;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eventhive.events.VenueSummaryDTO;
import com.eventhive.exception.ResourceNotFoundException;
import com.eventhive.venues.Venue;
import com.eventhive.venues.VenueRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SeatService {
    private final SeatRepository repo;
    private final VenueRepository venueRepo;
    private final SeatDTOMapper mapper;

    public List<SeatDTO> getSeats(String seatRow, Integer number, Pageable pageable) {
        Specification<Seat> spec = Specification.where(SeatSpecifications.hasRow(seatRow))
                .and(SeatSpecifications.hasNumber(number));

        return repo.findAll(spec, pageable).stream().map(mapper).toList();
    }

    public SeatDTO getSeat(UUID id) {
        return repo.findById(id).map(mapper).orElseThrow(() -> new ResourceNotFoundException("Seat not found " + id));
    }

    public SeatDTO addSeat(SeatRegistrationRequest request) {
        Venue venue = venueRepo.findById(request.venueId()).orElseThrow(
                () -> new ResourceNotFoundException("Venue associated with this seat not found " + request.venueId()));

        Seat seat = new Seat(request.seatRow().toUpperCase(), request.number(), venue);

        repo.save(seat);

        return mapper.apply(seat);
    }

    @Transactional
    public SeatDTO updateSeat(UUID id, SeatUpdateRequest rq) {
        Seat seat = repo.findById(id).orElseThrow(() -> new ResourceNotFoundException("Seat not found " + id));

        if (rq.seatRow() != null) {
            seat.setSeatRow(rq.seatRow());
        }

        if (rq.number() != null) {
            seat.setNumber(rq.number());
        }

        return mapper.apply(seat);
    }

    public void removeSeat(UUID id) {
        repo.deleteById(id);
    }

    public VenueSummaryDTO getHost(UUID id) {
        return repo.findHostVenueById(id).orElseThrow(() -> new ResourceNotFoundException("Seat not found " + id));
    }
}
