package com.eventhive.auth.refresh;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.eventhive.users.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "refresh_tokens", uniqueConstraints = {
        @UniqueConstraint(name = "refresh_tokens_token_unique", columnNames = { "token" })
})
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String token;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private Boolean isRevoked = false;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id", referencedColumnName = "id")
    private RefreshToken parentRefreshToken;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "replaced_by_id", referencedColumnName = "id")
    private RefreshToken replacedByRefreshToken;

    @Column(nullable = false)
    private UUID familyId;

    @CreationTimestamp
    @Column(updatable = false, nullable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private Instant updatedAt;

    public RefreshToken(UUID id, String token, User user, Instant expiresAt, UUID familyId) {
        this.id = id;
        this.token = token;
        this.user = user;
        this.expiresAt = expiresAt;
        this.familyId = familyId;
    }

    public RefreshToken(String token, User user, Instant expiresAt, RefreshToken parentRefreshToken, UUID familyId) {
        this.token = token;
        this.user = user;
        this.expiresAt = expiresAt;
        this.parentRefreshToken = parentRefreshToken;
        this.familyId = familyId;
    }
}
