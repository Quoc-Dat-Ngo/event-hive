package com.eventhive.integration;

import org.springframework.http.MediaType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.eventhive.AbstractWebIntegrationTest;
import com.eventhive.users.UserRepository;

import static org.assertj.core.api.Assertions.*;

@AutoConfigureMockMvc
public class UserIntegrationTest extends AbstractWebIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldReturnConflictStatusOnDuplicatingEmail() throws Exception {
        String newUserJson = """
                {
                    "firstName": "Kevin",
                    "lastName": "Ngo",
                    "email": "quocngo@gmail.com",
                    "password": "dat123",
                    "authProvider": "LOCAL"
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
                    "authProvider": "LOCAL"
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
    void shouldReturnConflictStatusUponViolatingDbConstraint() throws Exception {
        String userWithNullPassword = """
                {
                    "firstName": "Lucas",
                    "lastName": "Ngo",
                    "email": "quocngo@gmail.com",
                    "password": null,
                    "authProvider": "LOCAL"
                }
                """;

        mockMvc.perform(post("/api/v1/users")
                .contentType(MediaType.APPLICATION_JSON)
                .content(userWithNullPassword))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.path").value("/api/v1/users"))
                .andExpect(jsonPath("$.message").value("A data conflict occured"))
                .andExpect(jsonPath("$.statusCode").value(409))
                .andExpect(jsonPath("$.localDateTime").exists());

        String userWithPasswordAndNonLocalAuthProvider = """
                {
                    "firstName": "Lucas",
                    "lastName": "Ngo",
                    "email": "quocngo@gmail.com",
                    "password": "duc123",
                    "authProvider": "GOOGLE"
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
    void shouldPreventDuplicateEmailUnderConcurrentRequests() throws Exception {
        String userAJson = """
                {
                    "firstName": "Kevin",
                    "lastName": "Ngo",
                    "email": "quocngo@gmail.com",
                    "password": "duc123",
                    "authProvider": "LOCAL"
                }
                """;
        String userBJson = """
                {
                    "firstName": "Lucas",
                    "lastName": "Ngo",
                    "email": "quocngo@gmail.com",
                    "password": "duc123",
                    "authProvider": "LOCAL"
                }
                """;

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Callable<Integer> requestA = () -> {
            readyLatch.countDown();
            startLatch.await();
            return mockMvc.perform(post("/api/v1/users")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(userAJson)).andReturn().getResponse().getStatus();
        };

        Callable<Integer> requestB = () -> {
            readyLatch.countDown();
            startLatch.await();
            return mockMvc.perform(post("/api/v1/users")
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
}
