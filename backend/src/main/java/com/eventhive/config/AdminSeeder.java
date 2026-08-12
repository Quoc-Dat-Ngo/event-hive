package com.eventhive.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.eventhive.users.AuthProvider;
import com.eventhive.users.User;
import com.eventhive.users.UserRepository;
import com.eventhive.users.UserRole;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AdminSeeder implements ApplicationRunner {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${eventhive.admin.email}")
    private String adminEmail;
    @Value("${eventhive.admin.password}")
    private String adminPassword;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!userRepository.existsUserByEmail(adminEmail)) {
            User admin = new User("Kevin", "Ngo", adminEmail, passwordEncoder.encode(adminPassword), AuthProvider.LOCAL,
                    UserRole.ADMIN);
            userRepository.save(admin);
            System.out.println("One-time administrative seed account generated successfully.");
        }
    }
}
