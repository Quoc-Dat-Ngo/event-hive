package com.eventhive.auth.refresh;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TokenFamilyRevoker {
    private final RefreshTokenRepository repo;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeFamily(UUID familyId) {
        return repo.revokeTokenFamily(familyId);
    }
}
