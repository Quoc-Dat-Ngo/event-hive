package com.eventhive;

import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class AbstractWebIntegrationTest extends TestContainerInitialiser {

}
