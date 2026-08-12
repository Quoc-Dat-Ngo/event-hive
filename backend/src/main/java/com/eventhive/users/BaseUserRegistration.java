package com.eventhive.users;

public interface BaseUserRegistration {
    String firstName();

    String lastName();

    String email();

    String password();

    AuthProvider authProvider();

    UserRole role();
}
