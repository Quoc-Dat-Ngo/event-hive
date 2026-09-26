package com.eventhive.tiers;

import java.util.function.Function;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class PriceTierDTOMapper implements Function<PriceTier, PriceTierDTO> {
    private final PriceTierSeatRepository tierSeatRepo;

    @Override
    public PriceTierDTO apply(PriceTier t) {
        return new PriceTierDTO(
                t.getId(),
                t.getName(),
                t.getPriceCents(),
                tierSeatRepo.countByTierId(t.getId()),
                t.getEvent().getId());
    }
}
