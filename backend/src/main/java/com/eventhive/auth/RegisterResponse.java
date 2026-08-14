package com.eventhive.auth;

import com.eventhive.users.UserDTO;

public record RegisterResponse(
		String token,
		UserDTO user) {

}
