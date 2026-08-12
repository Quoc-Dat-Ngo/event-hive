package com.eventhive.users;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.eventhive.bookings.BookingSummaryDTO;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserDTO> getUsers(@RequestParam(required = false, defaultValue = "1") int pageNo,
            @RequestParam(required = false, defaultValue = "20") int pageSize,
            @RequestParam(required = false, defaultValue = "+firstName") String sortBy,
            @RequestParam(required = false) String search) {
        Sort sort = null;
        if (sortBy.startsWith("+")) {
            sort = Sort.by(sortBy.substring(1)).ascending();
        } else if (sortBy.startsWith("-")) {
            sort = Sort.by(sortBy.substring(1)).descending();
        } else {
            sort = Sort.by(sortBy).ascending();
        }
        return userService.getAllUsers(PageRequest.of(pageNo - 1, pageSize, sort), search);
    }

    @GetMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.id")
    public UserDTO getUser(@PathVariable("userId") UUID userId) {
        return userService.getUserById(userId);
    }

    @PostMapping
    @ResponseStatus(value = HttpStatus.CREATED)
    public UserDTO registerNewUser(
            @Valid @RequestBody UserRegistrationRequest request) {
        return userService.createStandardUser(request);
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(value = HttpStatus.CREATED)
    public UserDTO registerNewUser(
            @Valid @RequestBody AdminUserCreationRequest request) {
        return userService.createAdminUser(request);
    }

    @PostMapping

    @PutMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.id")
    public UserDTO updateUser(
            @PathVariable("userId") UUID userId,
            @Valid @RequestBody UserUpdateRequest request) {
        return userService.updateUser(userId, request);
    }

    @DeleteMapping("/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(code = HttpStatus.NO_CONTENT)
    public void deleteUser(
            @PathVariable("userId") UUID userId) {
        userService.deleteUser(userId);
    }

    @GetMapping("/{userId}/bookings")
    @PreAuthorize("hasRole('ADMIN') or #userId == authentication.principal.id")
    public List<BookingSummaryDTO> getAllBookings(@PathVariable("userId") UUID id) {
        return userService.getBookings(id);
    }
}
