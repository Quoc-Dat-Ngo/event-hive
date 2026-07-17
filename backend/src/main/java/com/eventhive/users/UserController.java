package com.eventhive.users;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping
    public List<UserDTO> getUsers(@RequestParam(required = false, defaultValue = "1") int pageNo,
            @RequestParam(required = false, defaultValue = "20") int pageSize,
            @RequestParam(required = false, defaultValue = "+firstName") String sortBy,
            @RequestParam(required = false) String search) {
        Sort sort = sortBy.startsWith("-") ? Sort.by(sortBy.substring(1)).descending()
                : Sort.by(sortBy.substring(1)).ascending();
        return userService.getAllUsers(PageRequest.of(pageNo - 1, pageSize, sort), search);
    }

    @GetMapping("/{userId}")
    public UserDTO getUser(@PathVariable("userId") UUID userId) {
        return userService.getUserById(userId);
    }

    @PostMapping
    public UserDTO registerNewUser(
            @Valid @RequestBody UserRegistrationRequest request) {
        return userService.addUser(request);
    }

    @PutMapping("/{userId}")
    public UserDTO updateUser(
            @PathVariable("userId") UUID userId,
            @Valid @RequestBody UserUpdateRequest request) {
        return userService.updateUser(userId, request);
    }

    @DeleteMapping("/{userId}")
    public void deleteUser(
            @PathVariable("userId") UUID userId) {
        userService.deleteUser(userId);
    }

}
