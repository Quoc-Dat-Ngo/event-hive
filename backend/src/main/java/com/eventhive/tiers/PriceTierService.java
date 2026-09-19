package com.eventhive.tiers;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.eventhive.bookings.BookingRepository;
import com.eventhive.bookings.BookingStatus;
import com.eventhive.events.Event;
import com.eventhive.events.EventRepository;
import com.eventhive.exception.DuplicateResourceException;
import com.eventhive.exception.IllegalStateTransitionException;
import com.eventhive.exception.RequestValidationException;
import com.eventhive.exception.ResourceNotFoundException;
import com.eventhive.seats.Seat;
import com.eventhive.seats.SeatRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PriceTierService {
    private final PriceTierRepository tierRepo;
    private final PriceTierSeatRepository tierSeatRepo;
    private final EventRepository eventRepo;
    private final SeatRepository seatRepo;
    private final BookingRepository bookingRepo;
    private final PriceTierDTOMapper mapper;

    private static final Set<BookingStatus> ACTIVE_STATUSES = Set.of(BookingStatus.PENDING, BookingStatus.CONFIRMED);

    // Rows run A..Z then AA..ZZ, so shorter rows sort first
    static final Comparator<String> ROW_ORDER = Comparator.comparingInt(String::length)
            .thenComparing(Comparator.naturalOrder());

    private static final Comparator<Seat> SEAT_ORDER = Comparator
            .comparing((Seat s) -> s.getSeatRow().toUpperCase(), ROW_ORDER)
            .thenComparing(Seat::getNumber);

    private Event findEvent(UUID eventId) {
        return eventRepo.findById(eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Event not found " + eventId));
    }

    private PriceTier findTier(UUID eventId, UUID tierId) {
        return tierRepo.findByIdAndEventId(tierId, eventId)
                .orElseThrow(() -> new ResourceNotFoundException("Price tier not found " + tierId));
    }

    public List<PriceTierDTO> getTiers(UUID eventId) {
        findEvent(eventId);
        return tierRepo.findByEventIdOrderByPriceCentsDesc(eventId).stream().map(mapper).toList();
    }

    @Transactional
    public PriceTierDTO addTier(UUID eventId, PriceTierRegistrationRequest rq) {
        Event event = findEvent(eventId);
        String name = rq.name().trim();
        if (tierRepo.existsByEventIdAndNameIgnoreCase(eventId, name)) {
            throw new DuplicateResourceException("A price tier named '" + name + "' already exists for this event");
        }

        List<Seat> venueSeats = seatRepo.findByVenueId(event.getVenue().getId());
        List<Seat> selected = venueSeats.stream()
                .filter(seat -> rq.seatRanges().stream().anyMatch(range -> contains(range, seat)))
                .sorted(SEAT_ORDER)
                .toList();
        if (selected.isEmpty()) {
            throw new RequestValidationException("No seats in this event's venue match the given ranges");
        }

        Set<UUID> alreadyPriced = tierSeatRepo.findAssignmentsByEventId(eventId).stream()
                .map(TierSeatAssignment::seatId)
                .collect(Collectors.toSet());
        List<String> conflicts = selected.stream()
                .filter(seat -> alreadyPriced.contains(seat.getId()))
                .map(seat -> seat.getSeatRow() + seat.getNumber())
                .toList();
        if (!conflicts.isEmpty()) {
            throw new DuplicateResourceException("Seats already belong to another tier: " + String.join(", ", conflicts));
        }

        PriceTier tier = tierRepo.save(new PriceTier(name, rq.priceCents(), event));
        tierSeatRepo.saveAll(selected.stream().map(seat -> new PriceTierSeat(tier, seat)).toList());
        return mapper.apply(tier);
    }

    private static boolean contains(SeatRange range, Seat seat) {
        String from = range.rowFrom().toUpperCase();
        String to = range.rowTo() == null ? from : range.rowTo().toUpperCase();
        if (ROW_ORDER.compare(from, to) > 0) {
            throw new RequestValidationException("Row range " + from + "-" + to + " is reversed");
        }
        int numberFrom = range.numberFrom() == null ? 1 : range.numberFrom();
        int numberTo = range.numberTo() == null ? Integer.MAX_VALUE : range.numberTo();
        if (numberFrom > numberTo) {
            throw new RequestValidationException("Seat number range " + numberFrom + "-" + numberTo + " is reversed");
        }

        String row = seat.getSeatRow().toUpperCase();
        return ROW_ORDER.compare(row, from) >= 0 && ROW_ORDER.compare(row, to) <= 0
                && seat.getNumber() >= numberFrom && seat.getNumber() <= numberTo;
    }

    @Transactional
    public PriceTierDTO updateTier(UUID eventId, UUID tierId, PriceTierUpdateRequest rq) {
        PriceTier tier = findTier(eventId, tierId);

        if (rq.name() != null && !rq.name().trim().equalsIgnoreCase(tier.getName())) {
            String name = rq.name().trim();
            if (tierRepo.existsByEventIdAndNameIgnoreCase(eventId, name)) {
                throw new DuplicateResourceException("A price tier named '" + name + "' already exists for this event");
            }
            tier.setName(name);
        }

        // Existing bookings keep the price they were created with
        if (rq.priceCents() != null) {
            tier.setPriceCents(rq.priceCents());
        }

        return mapper.apply(tier);
    }

    @Transactional
    public void deleteTier(UUID eventId, UUID tierId) {
        PriceTier tier = findTier(eventId, tierId);
        List<UUID> seatIds = tierSeatRepo.findSeatIdsByTierId(tierId);
        if (!seatIds.isEmpty() && bookingRepo.existsByEventIdAndSeatIdInAndStatusIn(eventId, seatIds, ACTIVE_STATUSES)) {
            throw new IllegalStateTransitionException(
                    "Cannot delete tier '" + tier.getName() + "' while it has pending or confirmed bookings");
        }
        tierSeatRepo.deleteAllByTierId(tierId);
        tierRepo.delete(tier);
    }

    public List<EventSeatDTO> getSeatMap(UUID eventId) {
        Event event = findEvent(eventId);
        Map<UUID, PriceTier> tiersById = tierRepo.findByEventIdOrderByPriceCentsDesc(eventId).stream()
                .collect(Collectors.toMap(PriceTier::getId, t -> t));
        Map<UUID, PriceTier> tierBySeat = tierSeatRepo.findAssignmentsByEventId(eventId).stream()
                .collect(Collectors.toMap(TierSeatAssignment::seatId, a -> tiersById.get(a.tierId())));
        Set<UUID> taken = Set.copyOf(bookingRepo.findSeatIdsByEventIdAndStatusIn(eventId, ACTIVE_STATUSES));

        return seatRepo.findByVenueId(event.getVenue().getId()).stream()
                .sorted(SEAT_ORDER)
                .map(seat -> {
                    PriceTier tier = tierBySeat.get(seat.getId());
                    return new EventSeatDTO(
                            seat.getId(),
                            seat.getSeatRow(),
                            seat.getNumber(),
                            tier == null ? null : tier.getId(),
                            tier == null ? null : tier.getName(),
                            tier == null ? null : tier.getPriceCents(),
                            // Seats without a tier are not on sale
                            tier != null && !taken.contains(seat.getId()));
                })
                .toList();
    }
}
