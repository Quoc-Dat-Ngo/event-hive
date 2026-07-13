package com.eventhive.users;

import java.util.function.Function;

import org.springframework.stereotype.Service;

@Service
public class UserDTOMapper implements Function<User, UserDTO> {
    @Override
    public UserDTO apply(User user) {
        return new UserDTO(
                user.getId(),
                user.getFirstName(),
                user.getLastName(),
                user.getEmail(),
                user.getAuthProvider().name(),
                user.getRole().name(),
                user.getCreatedAt(),
                user.getUpdatedAt());
    }
}
