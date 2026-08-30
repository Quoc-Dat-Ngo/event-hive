package com.eventhive.auth;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.eventhive.auth.refresh.RefreshTokenService;
import com.eventhive.security.UserPrincipal;
import com.eventhive.users.UserDTO;
import com.eventhive.users.UserRegistrationRequest;
import com.eventhive.users.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
	private static final String REFRESH_TOKEN = "refresh_token";

	private final JwtTokenService jwtTokenService;
	private final AuthenticationManager authenticationManager;
	private final UserService userService;
	private final RefreshTokenService refreshTokenService;

	@PostMapping("/login")
	public ResponseEntity<LoginResponse> login(
			@Valid @RequestBody LoginRequest request) {
		UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
				request.username(), request.password());
		Authentication authentication = authenticationManager.authenticate(usernamePasswordAuthenticationToken);
		String accessToken = jwtTokenService.generateToken(
				authentication.getName(),
				((UserPrincipal) authentication.getPrincipal()).getId().toString(),
				authentication.getAuthorities());

		var refreshToken = refreshTokenService.generateRefreshToken(request.username());
		ResponseCookie cookie = buildRefreshCookie(refreshToken.token(), Duration.ofDays(7));
		System.out.println(cookie);
		LoginResponse loginResponse = new LoginResponse(accessToken, null);

		return ResponseEntity.ok()
				.headers(header -> header.add("Set-Cookie", cookie.toString()))
				.body(loginResponse);
	}

	@PostMapping("/register")
	public ResponseEntity<RegisterResponse> register(
			@Valid @RequestBody UserRegistrationRequest request) {
		UserDTO newUser = userService.createStandardUser(request);
		UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
				request.email(), request.password());
		Authentication authentication = authenticationManager.authenticate(usernamePasswordAuthenticationToken);
		String accessToken = jwtTokenService.generateToken(
				authentication.getName(),
				((UserPrincipal) authentication.getPrincipal()).getId().toString(),
				authentication.getAuthorities());

		var refreshToken = refreshTokenService.generateRefreshToken(request.email());
		ResponseCookie cookie = buildRefreshCookie(refreshToken.token(), Duration.ofDays(7));

		System.out.println(cookie);

		RegisterResponse registerResponse = new RegisterResponse(accessToken, newUser);
		return ResponseEntity.status(HttpStatus.CREATED)
				.headers(header -> header.add("Set-Cookie", cookie.toString()))
				.body(registerResponse);
	}

	@PostMapping("/refresh-token")
	public ResponseEntity<LoginResponse> refreshToken(
			@CookieValue(value = REFRESH_TOKEN) String currentRefreshToken) {
		var newRefreshTokenResponse = refreshTokenService.rotateAndGetNewToken(currentRefreshToken);
		UserDTO user = newRefreshTokenResponse.user();

		Set<GrantedAuthority> grantedAuthorities = new HashSet<>();
		grantedAuthorities.add(new SimpleGrantedAuthority("ROLE_" + user.role()));

		var accessToken = jwtTokenService.generateToken(user.email(), user.id().toString(), grantedAuthorities);

		LoginResponse loginResponse = new LoginResponse(
				accessToken,
				null);

		ResponseCookie cookie = buildRefreshCookie(newRefreshTokenResponse.token(), Duration.ofDays(7));

		System.out.println(cookie);

		return ResponseEntity.ok()
				.headers(header -> header.add("Set-Cookie", cookie.toString()))
				.body(loginResponse);
	}

	@PostMapping("/logout")
	public ResponseEntity<Void> logOut(
			@CookieValue(value = REFRESH_TOKEN) String currentRefreshToken) {
		refreshTokenService.revokeRefreshToken(currentRefreshToken);
		ResponseCookie cookie = buildRefreshCookie("", Duration.ZERO);

		return ResponseEntity.ok()
				.headers(header -> header.add("Set-Cookie", cookie.toString()))
				.build();
	}

	@PostMapping("/logout-all")
	public ResponseEntity<Void> logOutAll(
			@CookieValue(value = REFRESH_TOKEN) String currentRefreshToken) {
		refreshTokenService.revokeAllRefreshToken(currentRefreshToken);
		ResponseCookie cookie = buildRefreshCookie("", Duration.ZERO);

		return ResponseEntity.ok()
				.headers(header -> header.add("Set-Cookie", cookie.toString()))
				.build();
	}

	private ResponseCookie buildRefreshCookie(String token, Duration maxAge) {
		return ResponseCookie.from(
				REFRESH_TOKEN,
				token)
				.maxAge(maxAge)
				.httpOnly(true)
				.secure(true)
				.path("/api/v1/auth")
				.sameSite("Strict")
				.build();
	}
}
