package com.eventhive.users;

import java.util.List;
import java.util.UUID;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
        } else {
            usersPage = userRepository.findUserByFirstNameContainingIgnoreCase(search, pageable);
        }

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

        String passwordHash = null;
        if (request.password() != null && !request.password().trim().equals("")) {
            passwordHash = passwordEncoder.encode(request.password());
        }
        User user = new User(request.firstName(), request.lastName(), request.email(), passwordHash,
                request.authProvider(), request.role());

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            if (isConstraintViolation(e, "user_email_unique")) {
                throw new DuplicateResourceException("Email already taken");
            }
            throw e;
        }

        return userDTOMapper.apply(user);
    }

    private boolean isConstraintViolation(DataIntegrityViolationException e, String constraintName) {
        Throwable cause = e.getCause();
        return cause instanceof ConstraintViolationException cve
                && constraintName.equalsIgnoreCase(cve.getConstraintName());
    }

    @Transactional
    public UserDTO updateUser(UUID id, UserUpdateRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User with id '" + id + "' not found"));

        if (request.firstName() != null) {
            user.setFirstName(request.firstName());
        }

        if (request.lastName() != null) {
            user.setLastName(request.lastName());
        }

        return userDTOMapper.apply(user);
    }

    public void deleteUser(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User with id '" + id + "' not found"));
        userRepository.delete(user);
    }
}
