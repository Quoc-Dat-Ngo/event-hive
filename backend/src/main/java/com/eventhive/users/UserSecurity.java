package com.eventhive.users;

import java.util.UUID;

import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component("userSecurity")
@RequiredArgsConstructor
public class UserSecurity {
    private final UserRepository userRepository;

    public boolean isSelf(UUID userId, String principalIdString) {
        UUID principalId = UUID.fromString(principalIdString);
        var result = userRepository.findById(userId)
                .map(u -> u.getId().equals(principalId))
                .orElse(false);
        return result;
    }
}
