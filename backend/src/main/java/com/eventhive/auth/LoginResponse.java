package com.eventhive.auth;

public record LoginResponse(
		String accessToken,
		String refreshToken) {
}
