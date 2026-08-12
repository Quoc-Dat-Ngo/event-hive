package com.eventhive.auth;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.eventhive.users.UserRegistrationRequest;
import com.eventhive.users.UserService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {
    private final JwtTokenService jwtTokenService;
    private final AuthenticationManager authenticationManager;
    private final UserService userService;

    @PostMapping("/login")
    public LoginResponse login(
            @Valid @RequestBody LoginRequest request) {
        UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
                request.username(), request.password());
        Authentication authentication = authenticationManager.authenticate(usernamePasswordAuthenticationToken);
        String token = jwtTokenService.generateToken(authentication);
        return new LoginResponse(token);
    }

    @PostMapping("/register")
    @ResponseStatus(code = HttpStatus.CREATED)
    public RegisterResponse register(
            @Valid @RequestBody UserRegistrationRequest request) {
        userService.createStandardUser(request);
        UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
                request.email(), request.password());
        Authentication authentication = authenticationManager.authenticate(usernamePasswordAuthenticationToken);
        String token = jwtTokenService.generateToken(authentication);
        return new RegisterResponse(token);
    }
}
