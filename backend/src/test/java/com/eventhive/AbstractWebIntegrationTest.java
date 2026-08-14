package com.eventhive;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import com.eventhive.helpers.TestUtility;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestPropertySource(properties = {
        "jwt.secret=test-secret-key-not-for-production-use-only-testing-32bytes+",
        "eventhive.admin.email=admin@gmail.com",
        "eventhive.admin.password=adminpassword"
})
public class AbstractWebIntegrationTest extends TestContainerInitialiser {
    @Autowired
    JdbcTemplate jdbcTemplate;

    @AfterEach
    void cleanUp() {
        TestUtility.clearDatabase(jdbcTemplate);
    }

}
