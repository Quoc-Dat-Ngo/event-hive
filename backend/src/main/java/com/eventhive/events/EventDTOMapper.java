package com.eventhive.events;

import java.util.function.Function;

public class EventDTOMapper implements Function<Event, EventDTO> {

    @Override
    public EventDTO apply(Event e) {
        return new EventDTO(
                e.getId(),
                e.getTitle(),
                e.getPurpose(),
                e.getStartsAt(),
                e.getEndsAt(),
                e.getPerformer(),
                e.getStatus().name(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }

}
