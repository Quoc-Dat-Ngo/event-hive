package com.eventhive.auth.refresh;

import com.eventhive.users.UserDTO;

public record RefreshTokenResponse(
        String token,
        UserDTO user) {
}
