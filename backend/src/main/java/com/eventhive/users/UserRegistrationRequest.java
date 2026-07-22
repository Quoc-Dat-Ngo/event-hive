package com.eventhive.users;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UserRegistrationRequest(
		@NotBlank(message = "First name cannot be blank") @Size(min = 3) String firstName,
		@NotBlank(message = "Last name cannot be blank") @Size(min = 3) String lastName,
		@Email String email,
		String password,
		@NotNull(message = "Auth provider is required") AuthProvider authProvider,
		@NotNull(message = "Role is required") UserRole role) {

}
