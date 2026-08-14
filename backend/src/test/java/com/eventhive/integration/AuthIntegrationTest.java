package com.eventhive.integration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.eventhive.AbstractWebIntegrationTest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.Test;

@AutoConfigureMockMvc
public class AuthIntegrationTest extends AbstractWebIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtDecoder jwtDecoder;

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
                .andExpect(jsonPath("$.token").exists())
                .andReturn();

        JsonNode root = objectMapper.readTree(result.getResponse().getContentAsString());
        String token = root.get("token").asString();

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
                .andExpect(jsonPath("$.token").exists())
                .andReturn();

        root = objectMapper.readTree(result.getResponse().getContentAsString());
        String token = root.get("token").asString();

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
}
