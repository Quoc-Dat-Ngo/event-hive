package com.eventhive.auth;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class JwtTokenService {
	private final JwtEncoder jwtEncoder;

	String generateToken(
			String subject,
			String principalId,
			Collection<? extends GrantedAuthority> authorities) {
		Instant now = Instant.now();
		// var authorities = authentication.getAuthorities();
		var roles = authorities.stream()
				.map(GrantedAuthority::getAuthority)
				.filter(r -> r.startsWith("ROLE_"))
				.map(r -> r.substring("ROLE_".length()))
				.toList();

		JwtClaimsSet jwtClaimsSet = JwtClaimsSet.builder()
				.issuedAt(now)
				.expiresAt(now.plus(15, ChronoUnit.MINUTES))
				// .subject(authentication.getName())
				.subject(subject)
				// .claim("userId", ((UserPrincipal)
				// authentication.getPrincipal()).getId().toString())
				.claim("userId", principalId)
				.claim("roles", roles)
				.build();
		JwsHeader jwsHeader = JwsHeader.with(MacAlgorithm.HS256).build();
		JwtEncoderParameters jwtEncoderParameters = JwtEncoderParameters.from(jwsHeader, jwtClaimsSet);

		return jwtEncoder.encode(jwtEncoderParameters).getTokenValue();
	}
}
