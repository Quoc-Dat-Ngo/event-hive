package com.eventhive.integration;

import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.eventhive.AbstractWebIntegrationTest;
import com.eventhive.users.UserRepository;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;

@AutoConfigureMockMvc
public class UserIntegrationTest extends AbstractWebIntegrationTest {
	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ObjectMapper objectMapper;

	@Test
	void shouldRequireAuthenticationWhenViewingUserProfile() throws Exception {
		String newUserJson = """
				{
				    "firstName": "Kevin",
				    "lastName": "Ngo",
				    "email": "quocngo@gmail.com",
				    "password": "dat123",
				    "authProvider": "LOCAL",
				    "role": "USER"
				}
				""";

		var result = mockMvc.perform(post("/api/v1/users/registration")
				.contentType(MediaType.APPLICATION_JSON)
				.content(newUserJson))
				.andExpect(status().isCreated())
				.andReturn();

		String newUserId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asString();

		mockMvc.perform(get("/api/v1/users/" + newUserId))
				.andExpect(status().isUnauthorized());
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void shouldReturnConflictStatusOnDuplicatingEmail() throws Exception {
		String newUserJson = """
				{
				    "firstName": "Kevin",
				    "lastName": "Ngo",
				    "email": "quocngo@gmail.com",
				    "password": "dat123",
				    "authProvider": "LOCAL",
				    "role": "USER"
				}
				""";

		mockMvc.perform(post("/api/v1/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content(newUserJson))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.firstName").value("Kevin"))
				.andExpect(jsonPath("$.lastName").value("Ngo"))
				.andExpect(jsonPath("$.email").value("quocngo@gmail.com"))
				.andExpect(jsonPath("$.provider").value("LOCAL"))
				.andExpect(jsonPath("$.role").value("USER"))
				.andExpect(jsonPath("$.createdAt").exists())
				.andExpect(jsonPath("$.updatedAt").exists());

		String anotherUserJson = """
				{
				    "firstName": "Lucas",
				    "lastName": "Ngo",
				    "email": "quocngo@gmail.com",
				    "password": "duc123",
				    "authProvider": "LOCAL",
				    "role": "USER"
				}
				""";

		mockMvc.perform(post("/api/v1/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content(anotherUserJson))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.path").value("/api/v1/users"))
				.andExpect(jsonPath("$.message").value("Email already taken"))
				.andExpect(jsonPath("$.statusCode").value(409))
				.andExpect(jsonPath("$.localDateTime").exists());
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void shouldReturnConflictStatusUponViolatingDbConstraint() throws Exception {
		String userWithPasswordAndNonLocalAuthProvider = """
				{
				    "firstName": "Lucas",
				    "lastName": "Ngo",
				    "email": "quocngo@gmail.com",
				    "password": "duc123",
				    "authProvider": "GOOGLE",
				    "role": "USER"
				}
				""";

		mockMvc.perform(post("/api/v1/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content(userWithPasswordAndNonLocalAuthProvider))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.path").value("/api/v1/users"))
				.andExpect(jsonPath("$.message").value("A data conflict occured"))
				.andExpect(jsonPath("$.statusCode").value(409))
				.andExpect(jsonPath("$.localDateTime").exists());
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void shouldPreventDuplicateEmailUnderConcurrentRequests() throws Exception {
		String userAJson = """
				{
				    "firstName": "Kevin",
				    "lastName": "Ngo",
				    "email": "quocngo@gmail.com",
				    "password": "duc123",
				    "authProvider": "LOCAL",
				    "role": "ADMIN"
				}
				""";
		String userBJson = """
				{
				    "firstName": "Lucas",
				    "lastName": "Ngo",
				    "email": "quocngo@gmail.com",
				    "password": "duc123",
				    "authProvider": "LOCAL",
				    "role": "ADMIN"
				}
				""";

		ExecutorService executor = Executors.newFixedThreadPool(2);
		CountDownLatch readyLatch = new CountDownLatch(2);
		CountDownLatch startLatch = new CountDownLatch(1);

		Callable<Integer> requestA = () -> {
			readyLatch.countDown();
			startLatch.await();
			return mockMvc.perform(post("/api/v1/users")
					.with(jwt()
							.authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
							.jwt(builder -> builder.subject("quocngo@gmail.com")))
					.contentType(MediaType.APPLICATION_JSON)
					.content(userAJson)).andReturn().getResponse().getStatus();
		};

		Callable<Integer> requestB = () -> {
			readyLatch.countDown();
			startLatch.await();
			return mockMvc.perform(post("/api/v1/users")
					.with(jwt()
							.authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))
							.jwt(builder -> builder.subject("quocngo@gmail.com")))
					.contentType(MediaType.APPLICATION_JSON)
					.content(userBJson)).andReturn().getResponse().getStatus();
		};

		Future<Integer> resultA = executor.submit(requestA);
		Future<Integer> resultB = executor.submit(requestB);

		readyLatch.await();
		startLatch.countDown();

		List<Integer> statuses = List.of(resultA.get(), resultB.get());

		executor.shutdown();

		assertThat(statuses).containsExactlyInAnyOrder(201, 409);
		assertThat(userRepository.countByEmail("quocngo@gmail.com")).isEqualTo(1);
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void shouldReturnForbiddenStatusUponViewOtherUserProfile() throws Exception {
		String newUserJson = """
				{
				    "firstName": "Kevin",
				    "lastName": "Ngo",
				    "email": "quocngo@gmail.com",
				    "password": "dat123",
				    "authProvider": "LOCAL",
				    "role": "USER"
				}
				""";

		var result = mockMvc.perform(post("/api/v1/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content(newUserJson))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.firstName").value("Kevin"))
				.andExpect(jsonPath("$.lastName").value("Ngo"))
				.andExpect(jsonPath("$.email").value("quocngo@gmail.com"))
				.andExpect(jsonPath("$.provider").value("LOCAL"))
				.andExpect(jsonPath("$.role").value("USER"))
				.andExpect(jsonPath("$.createdAt").exists())
				.andExpect(jsonPath("$.updatedAt").exists())
				.andReturn();

		JsonNode rootNode = objectMapper.readTree(result.getResponse().getContentAsString());
		String newUserId = rootNode.get("id").asString();

		String anotherUserJson = """
				{
				    "firstName": "Lucas",
				    "lastName": "Ngo",
				    "email": "test@gmail.com",
				    "password": "duc123",
				    "authProvider": "LOCAL",
				    "role": "USER"
				}
				""";

		result = mockMvc.perform(post("/api/v1/users")
				.contentType(MediaType.APPLICATION_JSON)
				.content(anotherUserJson))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andReturn();

		rootNode = objectMapper.readTree(result.getResponse().getContentAsString());
		String anotherUserId = rootNode.get("id").asString();

		mockMvc.perform(get("/api/v1/users/" + newUserId)
				.with(jwt().jwt(builder -> builder.subject("test@gmail.com")
						.claim("userId", anotherUserId))
						.authorities(new SimpleGrantedAuthority("ROLE_USER"))))
				.andExpect(status().isForbidden());

		mockMvc.perform(get("/api/v1/users/" + newUserId)
				.with(jwt().jwt(builder -> builder.subject("quocngo@gmail.com")
						.claim("userId", newUserId))
						.authorities(new SimpleGrantedAuthority("ROLE_USER"))))
				.andExpect(status().isOk());

	}
}
