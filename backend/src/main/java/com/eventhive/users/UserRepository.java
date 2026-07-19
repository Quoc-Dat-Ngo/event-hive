package com.eventhive.users;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {
    boolean existsUserByEmail(String email);

    boolean existsUserById(UUID id);

    Optional<User> findUserByEmail(String email);

    Page<User> findUserByFirstNameContainingIgnoreCase(String name, Pageable pageable);
}
