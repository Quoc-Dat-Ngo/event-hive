package com.eventhive;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

import com.eventhive.users.User;

@SpringBootApplication
public class EventhiveApplication {

	public static void main(String[] args) {
		SpringApplication.run(EventhiveApplication.class, args);
	}

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
				.csrf((csrf) -> csrf.disable());
		return http.build();
	}

	// @Bean
	// CommandLineRunner runner() {
	// return args -> {
	// createRandomUser();
	// };
	// }

	// private static void createRandomUser() {
	// User user = new User
	// }

}
