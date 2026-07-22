package com.eventhive.seats;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.eventhive.events.VenueSummaryDTO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/seats")
@RequiredArgsConstructor
public class SeatController {
    private final SeatService service;

    @GetMapping
    public List<SeatDTO> getAllSeats(
            @RequestParam(defaultValue = "1") int pageNo,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) String seatRow,
            @RequestParam(required = false) Integer number) {
        Sort sort = Sort.by(
                Sort.Order.asc("seatRow"),
                Sort.Order.asc("number"));

        Pageable pageable = PageRequest.of(pageNo - 1, pageSize, sort);

        return service.getSeats(seatRow, number, pageable);
    }

    @GetMapping("/{seatId}")
    public SeatDTO getSeat(
            @PathVariable("seatId") UUID id) {
        return service.getSeat(id);
    }

    @PostMapping
    public SeatDTO addNewSeat(@Valid @RequestBody SeatRegistrationRequest request) {
        return service.addSeat(request);
    }

    @PutMapping("/{seatId}")
    public SeatDTO updateSeat(
            @PathVariable("seatId") UUID id,
            @Valid @RequestBody SeatUpdateRequest request) {
        return service.updateSeat(id, request);
    }

    @DeleteMapping("/{seatId}")
    public void deleteSeat(
            @PathVariable("seatId") UUID id) {
        service.removeSeat(id);
    }

    @GetMapping("/{seatId}/host")
    public VenueSummaryDTO getHost(@PathVariable("seatId") UUID id) {
        return service.getHost(id);
    }

}
