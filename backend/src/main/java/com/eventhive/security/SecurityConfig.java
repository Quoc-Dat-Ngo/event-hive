package com.eventhive.security;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

import com.eventhive.exception.ApiError;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import lombok.RequiredArgsConstructor;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableMethodSecurity
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
	private final ObjectMapper objectMapper;

	@Value("${jwt.secret}")
	private String jwtSecret;

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		return http.authorizeHttpRequests(auth -> auth
				// Auth endpoints
				.requestMatchers(
						"/api/v*/auth/login",
						"/api/v*/auth/register",
						"/api/v*/auth/refresh-token")
				.permitAll()
				.requestMatchers(HttpMethod.POST, "/api/v*/users/registration")
				.permitAll()
				.requestMatchers("/api/v*/auth/logout").authenticated()

				// ROLE_USER
				// Readable by any authenticated user
				.requestMatchers(
						HttpMethod.GET,
						"/api/v*/events/**",
						"/api/v*/venues/**",
						"/api/v*/bookings/**",
						"/api/v*/payments/**",
						"/api/v*/users/**",
						"/api/v*/seats/**")
				.authenticated()

				// User-initiated writes (self-scoped — ownership enforced via @PreAuthorize
				// later)
				.requestMatchers(
						HttpMethod.POST,
						"/api/v*/bookings/**",
						"/api/v*/payments/**")
				.authenticated()
				.requestMatchers(HttpMethod.PUT, "/api/v*/users/**").authenticated()

				// ROLE_EVENT_ORGANISER + ROLE_ADMIN
				// Organizer/Admin-owned writes
				.requestMatchers("/api/v*/events/**").hasAnyRole("EVENT_ORGANISER", "ADMIN")

				// ADMIN only
				.requestMatchers("/api/v*/venues/**", "/api/v*/seats/**").hasRole("ADMIN")
				.requestMatchers("/api/v*/users/**").hasRole("ADMIN")

				.anyRequest().authenticated())
				.oauth2ResourceServer(
						oauth2 -> oauth2.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
				.csrf(AbstractHttpConfigurer::disable)
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.headers(headers -> headers
						.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'self'"))
						.xssProtection(xss -> xss.disable())
						.frameOptions(frame -> frame.deny())
						.referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)))
				.exceptionHandling(
						exception -> exception.accessDeniedHandler(customAccessDeniedHandler())
								.authenticationEntryPoint(customAuthenticationEntryPoint()))
				.build();
	}

	@Bean
	JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
		jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(jwt -> {
			List<GrantedAuthority> authorities = new ArrayList<>();
			var roles = jwt.getClaimAsStringList("roles");
			if (roles != null) {
				roles.forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
			}
			return authorities;
		});
		return jwtAuthenticationConverter;
	}

	@Bean
	SecretKey secretKey() {
		return new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
	}

	@Bean
	JwtEncoder jwtEncoder(SecretKey secretKey) {
		return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
	}

	@Bean
	JwtDecoder jwtDecoder(SecretKey secretKey) {
		return NimbusJwtDecoder
				.withSecretKey(secretKey)
				.macAlgorithm(MacAlgorithm.HS256)
				.build();
	}

	@Bean
	PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

	@Bean
	AuthenticationProvider authenticationProvider(
			PasswordEncoder passwordEncoder,
			AppUserDetailsService appUserDetailsService) {
		DaoAuthenticationProvider provider = new DaoAuthenticationProvider(appUserDetailsService);
		provider.setPasswordEncoder(passwordEncoder);
		return provider;
	}

	@Bean
	AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) {
		return configuration.getAuthenticationManager();
	}

	private AuthenticationEntryPoint customAuthenticationEntryPoint() {
		return (request, response, authException) -> {
			response.setContentType("application/json");
			response.setStatus(401);

			ApiError apiError = new ApiError(
					request.getRequestURI(),
					authException.getMessage(),
					401,
					LocalDateTime.now());

			response.getWriter().write(objectMapper.writeValueAsString(apiError));
		};
	}

	private AccessDeniedHandler customAccessDeniedHandler() {
		return (request, response, accessDeniedException) -> {
			response.setContentType("application/json");
			response.setStatus(403);

			ApiError apiError = new ApiError(
					request.getRequestURI(),
					accessDeniedException.getMessage(),
					403,
					LocalDateTime.now());

			response.getWriter().write(objectMapper.writeValueAsString(apiError));
		};
	}
}
