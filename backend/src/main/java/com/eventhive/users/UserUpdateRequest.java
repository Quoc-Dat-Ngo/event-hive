package com.eventhive.users;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserUpdateRequest(
        @NotBlank(message = "First name cannot be blank") @Size(max = 100) String firstName,

        @NotBlank(message = "Last name cannot be blank") @Size(max = 100) String lastName) {
}
