package com.eventhive.auth.refresh;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.UUID;

import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import com.eventhive.exception.ResourceNotFoundException;
import com.eventhive.users.User;
import com.eventhive.users.UserDTOMapper;
import com.eventhive.users.UserRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {
    private final RefreshTokenRepository repo;
    private final UserRepository userRepo;
    private final UserDTOMapper mapper;
    private final TokenFamilyRevoker revoker;

    public RefreshTokenResponse generateRefreshToken(String username) {
        User user = userRepo.findUserByEmail(username)
                .orElseThrow(() -> new ResourceNotFoundException("User with email not found " + username));

        return generateRefreshToken(user);
    }

    private static String generateToken() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        return token;
    }

    private static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }

    }

    private RefreshTokenResponse generateRefreshToken(User user) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        UUID familyId = UUID.randomUUID();

        String token = generateToken();

        RefreshToken refreshToken = new RefreshToken(
                hashToken(token),
                user,
                Instant.now().plus(7, ChronoUnit.DAYS),
                null,
                familyId);

        repo.save(refreshToken);

        return new RefreshTokenResponse(token, mapper.apply(user));
    }

    @Transactional
    public RefreshTokenResponse rotateAndGetNewToken(String token) {
        RefreshToken currentRefreshToken = repo.findByToken(hashToken(token))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (currentRefreshToken.getIsRevoked()) {
            revoker.revokeFamily(currentRefreshToken.getFamilyId());
            throw new BadCredentialsException("Refresh token is revoked");
        }

        if (currentRefreshToken.getExpiresAt().isBefore(Instant.now())) {
            throw new BadCredentialsException("Refresh token is expired");
        }

        String newToken = generateToken();
        RefreshToken newRefreshToken = new RefreshToken(
                hashToken(newToken),
                currentRefreshToken.getUser(),
                Instant.now().plus(7, ChronoUnit.DAYS),
                currentRefreshToken,
                currentRefreshToken.getFamilyId());
        repo.save(newRefreshToken);

        currentRefreshToken.setReplacedByRefreshToken(newRefreshToken);
        currentRefreshToken.setIsRevoked(true);

        return new RefreshTokenResponse(newToken, mapper.apply(newRefreshToken.getUser()));
    }

    @Transactional
    public void revokeRefreshToken(String token) {
        RefreshToken currentRefreshToken = repo.findByToken(hashToken(token))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));

        if (currentRefreshToken.getIsRevoked()) {
            throw new BadCredentialsException("Refresh token is revoked");
        }

        if (currentRefreshToken.getExpiresAt().isBefore(Instant.now())) {
            throw new BadCredentialsException("Refresh token is expired");
        }

        currentRefreshToken.setIsRevoked(true);
    }

    @Transactional
    public void revokeAllRefreshToken(String token) {
        RefreshToken currentRefreshToken = repo.findByToken(hashToken(token))
                .orElseThrow(() -> new BadCredentialsException("Invalid refresh token"));
        repo.revokeTokenFamily(currentRefreshToken.getFamilyId());
    }
}
