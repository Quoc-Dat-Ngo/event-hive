package com.eventhive.users;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminUserCreationRequest(
        @NotBlank(message = "First name cannot be blank") @Size(min = 3) String firstName,
        @NotBlank(message = "Last name cannot be blank") @Size(min = 3) String lastName,
        @NotBlank(message = "Email is required") @Email String email,
        @NotBlank(message = "Password is required") @Size(min = 6) String password,
        @NotNull(message = "Auth provider is required") AuthProvider authProvider,
        @NotNull(message = "Target user role must be supplied") UserRole role) implements BaseUserRegistration {
}
