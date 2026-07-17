package com.eventhive.users;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.eventhive.exception.DuplicateResourceException;
import com.eventhive.exception.RequestValidationException;
import com.eventhive.exception.ResourceNotFoundException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserDTOMapper userDTOMapper;
    private final PasswordEncoder passwordEncoder;

    public List<UserDTO> getAllUsers(Pageable pageable, String search) {
        Page<User> usersPage;
        if (search == null) {
            usersPage = userRepository.findAll(pageable);
        }

        usersPage = userRepository.findUserByFirstNameContainingIgnoreCase(search, pageable);

        return usersPage.stream().map(userDTOMapper).toList();
    }

    public UserDTO getUserById(UUID id) {
        return userRepository.findById(id).map(userDTOMapper)
                .orElseThrow(() -> new ResourceNotFoundException("User with id '" + id + "' not found"));
    }

    public UserDTO addUser(UserRegistrationRequest request) {
        if (userRepository.existsUserByEmail(request.email())) {
            throw new DuplicateResourceException("Email already taken");
        }

        String passwordHash = request.passwordHash().isPresent() ? passwordEncoder.encode(request.passwordHash().get())
                : null;
        User user = new User(request.firstName(), request.lastName(), request.email(), passwordHash,
                request.authProvider(), request.role());

        userRepository.save(user);

        return userDTOMapper.apply(user);
    }

    public UserDTO updateUser(UUID id, UserUpdateRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User with id '" + id + "' not found"));

        boolean changes = false;

        if (request.firstName() != null && !request.firstName().equals(user.getFirstName())) {
            user.setFirstName(request.firstName());
            changes = true;
        }

        if (request.lastName() != null && !request.lastName().equals(user.getLastName())) {
            user.setLastName(request.lastName());
            changes = true;
        }

        if (!changes) {
            throw new RequestValidationException("No data changes found");
        }

        userRepository.save(user);
        return userDTOMapper.apply(user);
    }

    public void deleteUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User with id '" + id + "' not found"));
        userRepository.delete(user);
    }
}
