package com.eventhive.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.eventhive.AbstractWebIntegrationTest;

import jakarta.servlet.http.Cookie;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;

@AutoConfigureMockMvc
public class AuthIntegrationTest extends AbstractWebIntegrationTest {
	private static final String REFRESH_TOKEN = "refresh_token";

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JwtDecoder jwtDecoder;

	// Log in + Register
	@Test
	void shouldRegisterThenLoginAndReceiveTokenWithUserRoleClaim() throws Exception {
		String registerJson = """
				{
				    "firstName": "Kevin",
				    "lastName": "Ngo",
				    "email": "kevin@example.com",
				    "password": "dat123",
				    "authProvider": "LOCAL"
				}
				""";
		mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerJson))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.token").exists())
				.andExpect(jsonPath("$.user").exists());

		String loginJson = """
				{
				    "username": "kevin@example.com",
				    "password": "dat123"
				}
				""";

		MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginJson))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").exists())
				.andReturn();

		JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
		String token = root.get("accessToken").asString();

		Jwt jwt = jwtDecoder.decode(token);
		assertThat(jwt.getSubject()).isEqualTo("kevin@example.com");
		assertThat(jwt.getClaimAsStringList("roles")).containsExactly("USER");
	}

	@Test
	void shouldReturnUnauthorizedForWrongPassword() throws Exception {
		String registerJson = """
				{
				    "firstName": "Kevin",
				    "lastName": "Ngo",
				    "email": "kevin@example.com",
				    "password": "dat123",
				    "authProvider": "LOCAL"
				}
				""";
		mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerJson))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.token").exists())
				.andExpect(jsonPath("$.user").exists());

		String loginJson = """
				{
				    "username": "kevin@example.com",
				    "password": "dat1234"
				}
				""";

		mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginJson))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.path").exists())
				.andExpect(jsonPath("$.message").exists())
				.andExpect(jsonPath("$.statusCode").value(401));
	}

	@Test
	void shouldReturnUnauthorizedForNonexistentUser() throws Exception {
		String loginJson = """
				{
				    "username": "kevin@example.com",
				    "password": "dat1234"
				}
				""";

		mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginJson))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.path").exists())
				.andExpect(jsonPath("$.message").exists())
				.andExpect(jsonPath("$.statusCode").value(401));
	}

	@Test
	void shouldAccessProtectedEndpointWithRealIssuedToken() throws Exception {
		String registerJson = """
				{
				    "firstName": "Kevin",
				    "lastName": "Ngo",
				    "email": "kevin@example.com",
				    "password": "dat123",
				    "authProvider": "LOCAL"
				}
				""";
		MvcResult registerResult = mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerJson))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.token").exists())
				.andExpect(jsonPath("$.user").exists())
				.andReturn();
		JsonNode root = objectMapper.readTree(registerResult.getResponse().getContentAsString());
		String userId = root.get("user").get("id").asString();

		String loginJson = """
				{
				    "username": "kevin@example.com",
				    "password": "dat123"
				}
				""";

		MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginJson))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").exists())
				.andReturn();

		root = objectMapper.readTree(result.getResponse().getContentAsString());
		String token = root.get("accessToken").asString();

		System.out.println(token);

		mockMvc.perform(get("/api/v1/users/" + userId)
				.header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());
	}

	@Test
	void shouldReturnConflictWhenRegisteringDuplicateEmail() throws Exception {
		String registerJson = """
				{
				    "firstName": "Kevin",
				    "lastName": "Ngo",
				    "email": "kevin@example.com",
				    "password": "dat123",
				    "authProvider": "LOCAL"
				}
				""";

		mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerJson))
				.andExpect(status().isCreated());

		mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerJson))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.message").value("Email already taken"));
	}

	@Test
	void shouldReturnBadRequestForInvalidRegistrationFields() throws Exception {
		String invalidJson = """
				{
				    "firstName": "K",
				    "lastName": "Ngo",
				    "email": "not-an-email",
				    "password": "dat123",
				    "authProvider": "LOCAL"
				}
				""";

		mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(invalidJson))
				.andExpect(status().isBadRequest());
	}

	@Test
	void shouldReturnBadRequestForMissingLoginFields() throws Exception {
		String blankJson = """
				{
				    "username": "",
				    "password": ""
				}
				""";

		mockMvc.perform(post("/api/v1/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(blankJson))
				.andExpect(status().isBadRequest());
	}

	@Test
	void shouldReturnUnauthorizedWhenAccessingProtectedEndpointWithoutToken() throws Exception {
		mockMvc.perform(get("/api/v1/users/" + java.util.UUID.randomUUID()))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void shouldReturnUnauthorizedForTamperedToken() throws Exception {
		// any structurally valid-looking JWT with an invalid signature
		String tamperedToken = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiJoYWNrZXIifQ.invalidsignature";

		mockMvc.perform(get("/api/v1/users/" + java.util.UUID.randomUUID())
				.header("Authorization", "Bearer " + tamperedToken))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void shouldGiveIdenticalErrorForWrongPasswordAndNonexistentUser() throws Exception {
		// Proves no email-enumeration leak: DaoAuthenticationProvider's
		// hideUserNotFoundExceptions should make both cases indistinguishable
		String registerJson = """
				{
				    "firstName": "Kevin",
				    "lastName": "Ngo",
				    "email": "kevin@example.com",
				    "password": "dat123",
				    "authProvider": "LOCAL"
				}
				""";
		mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerJson))
				.andExpect(status().isCreated());

		String wrongPasswordJson = """
				{"username": "kevin@example.com", "password": "wrongpass"}
				""";
		String nonexistentUserJson = """
				{"username": "nobody@example.com", "password": "wrongpass"}
				""";

		String wrongPasswordMessage = objectMapper.readTree(
				mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(wrongPasswordJson))
						.andExpect(status().isUnauthorized())
						.andReturn().getResponse().getContentAsString())
				.get("message").asString();

		String nonexistentUserMessage = objectMapper.readTree(
				mockMvc.perform(post("/api/v1/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(nonexistentUserJson))
						.andExpect(status().isUnauthorized())
						.andReturn().getResponse().getContentAsString())
				.get("message").asString();

		assertThat(wrongPasswordMessage).isEqualTo(nonexistentUserMessage);
	}

	// Refresh Token
	@Test
	void shouldReturnBadRequestUponMissingCookie() throws Exception {
		mockMvc.perform(post("/api/v1/auth/refresh-token"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void shouldReturnUnauthorisedUponInvalidCookieToken() throws Exception {
		Cookie mockCookie = new Cookie(REFRESH_TOKEN, "mock-encrypted-refresh-token-string-xyz");
		mockCookie.setHttpOnly(true);
		mockCookie.setSecure(true);
		mockCookie.setPath("/api/v1/auth");

		mockMvc.perform(post("/api/v1/auth/refresh-token")
				.cookie(mockCookie))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void shouldReturnHttpOnlyRefreshTokenCookie() throws Exception {
		String registerJson = """
				{
				    "firstName": "Kevin",
				    "lastName": "Ngo",
				    "email": "kevin@example.com",
				    "password": "dat123",
				    "authProvider": "LOCAL"
				}
				""";
		MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerJson))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.token").exists())
				.andExpect(jsonPath("$.user").exists())
				.andExpect(cookie().httpOnly(REFRESH_TOKEN, true))
				.andExpect(cookie().exists(REFRESH_TOKEN))
				.andReturn();

		Cookie refreshTokenCookie = result.getResponse().getCookie(REFRESH_TOKEN);
		assertThat(refreshTokenCookie).isNotNull();

		// To extract cookie value
		// String cookieValue = refreshTokenCookie.getValue();

		mockMvc.perform(post("/api/v1/auth/refresh-token")
				.cookie(refreshTokenCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").exists());
	}

	@Test
	void shouldRevokeTheWholeFamilyOfTokenUponUsingRevokedToken() throws Exception {
		String registerJson = """
				{
				    "firstName": "Kevin",
				    "lastName": "Ngo",
				    "email": "kevin@example.com",
				    "password": "dat123",
				    "authProvider": "LOCAL"
				}
				""";
		MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerJson))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.token").exists())
				.andExpect(jsonPath("$.user").exists())
				.andReturn();

		Cookie oldRefreshTokenCookie = result.getResponse().getCookie(REFRESH_TOKEN);
		assertThat(oldRefreshTokenCookie).isNotNull();

		// To extract cookie value
		// String cookieValue = oldRefreshTokenCookie.getValue();

		MvcResult newResult = mockMvc.perform(post("/api/v1/auth/refresh-token")
				.cookie(oldRefreshTokenCookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.accessToken").exists())
				.andReturn();
		Cookie newRefreshCookie = newResult.getResponse().getCookie(REFRESH_TOKEN);

		mockMvc.perform(post("/api/v1/auth/refresh-token")
				.cookie(oldRefreshTokenCookie))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Refresh token is revoked"));

		mockMvc.perform(post("/api/v1/auth/refresh-token")
				.cookie(newRefreshCookie))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.message").value("Refresh token is revoked"));
	}

	// Log out + Log out all
	@Test
	void shouldReturnUnauthorisedUponUnauthenticatedUser() throws Exception {
		mockMvc.perform(post("/api/v1/auth/logout"))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void shouldReturnUnauthorisedUponMissingRequestCookie() throws Exception {
		String registerJson = """
				{
				    "firstName": "Kevin",
				    "lastName": "Ngo",
				    "email": "kevin@example.com",
				    "password": "dat123",
				    "authProvider": "LOCAL"
				}
				""";
		String result = mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerJson))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.token").exists())
				.andExpect(jsonPath("$.user").exists())
				.andReturn().getResponse().getContentAsString();

		String accessToken = objectMapper.readTree(result).get("token").asString();

		mockMvc.perform(post("/api/v1/auth/logout")
				.header("Authorization", accessToken))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void shouldReturnOkUponSuccessfulLogOut() throws Exception {
		String registerJson = """
				{
				    "firstName": "Kevin",
				    "lastName": "Ngo",
				    "email": "kevin@example.com",
				    "password": "dat123",
				    "authProvider": "LOCAL"
				}
				""";
		MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerJson))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.token").exists())
				.andExpect(jsonPath("$.user").exists())
				.andReturn();

		String accessToken = objectMapper.readTree(result.getResponse().getContentAsString()).get("token").asString();
		Cookie refreshCookie = result.getResponse().getCookie(REFRESH_TOKEN);

		mockMvc.perform(post("/api/v1/auth/logout")
				.header("Authorization", "Bearer " + accessToken)
				.cookie(refreshCookie))
				.andExpect(status().isOk())
				.andExpect(cookie().exists(REFRESH_TOKEN))
				.andExpect(cookie().maxAge(REFRESH_TOKEN, 0));

		// Refresh cookie will be revoked, cannot use anymore
		mockMvc.perform(post("/api/v1/auth/refresh-token")
				.cookie(refreshCookie))
				.andExpect(status().isUnauthorized());
	}

	@Test
	void shouldRevokeEntireTokenFamilyWhenCompromisedTokenIsUsed() throws Exception {
		// 1. Establish initial session (Generates Token-1)
		String registerJson = """
				{
				    "firstName": "Kevin",
				    "lastName": "Ngo",
				    "email": "compromised@example.com",
				    "password": "pass123",
				    "authProvider": "LOCAL"
				}
				""";
		MvcResult initResult = mockMvc.perform(post("/api/v1/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(registerJson))
				.andExpect(status().isCreated())
				.andReturn();

		String accessToken = objectMapper.readTree(initResult.getResponse().getContentAsString()).get("token")
				.asString();
		Cookie token1Cookie = initResult.getResponse().getCookie(REFRESH_TOKEN);

		MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh-token")
				.cookie(token1Cookie))
				.andExpect(status().isOk())
				.andReturn();

		// Making sure token 1 is revoked
		mockMvc.perform(post("/api/v1/auth/refresh-token")
				.cookie(token1Cookie))
				.andExpect(status().isUnauthorized());

		// Token 1 now became old (revoked) token
		mockMvc.perform(post("/api/v1/auth/logout-all")
				.header("Authorization", "Bearer " + accessToken)
				.cookie(token1Cookie)) // Submitting the old token
				.andExpect(status().isOk())
				.andExpect(cookie().maxAge(REFRESH_TOKEN, 0));

		Cookie token2Cookie = result.getResponse().getCookie(REFRESH_TOKEN);
		mockMvc.perform(post("/api/v1/auth/refresh-token")
				.cookie(token2Cookie))
				.andExpect(status().isUnauthorized());
	}
}
