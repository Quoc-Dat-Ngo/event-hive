package com.eventhive.security;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.eventhive.users.User;
import com.eventhive.users.UserPrincipal;
import com.eventhive.users.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AppUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        User user = userRepository.findUserByEmail(email)
                .filter(u -> u.getPasswordHash() != null)
                .orElseThrow(() -> new UsernameNotFoundException("User not found " + email));
        return new UserPrincipal(user);
    }
}
