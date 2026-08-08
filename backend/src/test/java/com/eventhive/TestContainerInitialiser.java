package com.eventhive;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.postgresql.PostgreSQLContainer;

public abstract class TestContainerInitialiser {
	@ServiceConnection
	public static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16.0");

	static {
		// This forces the container to start ONCE when the first test runs
		// It stays alive until the JVM terminates (all tests finish)
		postgres.start();
	}
}
