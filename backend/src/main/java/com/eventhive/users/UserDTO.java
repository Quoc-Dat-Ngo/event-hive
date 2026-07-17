package com.eventhive.users;

import java.time.Instant;
import java.util.UUID;

public record UserDTO(
		UUID id,
		String firstName,
		String lastName,
		String email,
		String provider,
		String role,
		Instant createdAt,
		Instant updatedAt) {
}
