package com.eventhive.users;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.eventhive.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserDTOMapper userDTOMapper;

    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream().map(userDTOMapper).toList();
    }

    public UserDTO getUserById(UUID id) {
        return userRepository.findById(id).map(userDTOMapper)
                .orElseThrow(() -> new ResourceNotFoundException("User with id '" + id + "' not found"));
    }
}
