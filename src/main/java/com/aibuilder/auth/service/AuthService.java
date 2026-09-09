package com.aibuilder.auth.service;

import com.aibuilder.auth.dto.AuthResponse;
import com.aibuilder.auth.dto.LoginRequest;
import com.aibuilder.auth.dto.RegisterRequest;
import com.aibuilder.auth.security.JwtService;
import com.aibuilder.user.entity.Role;
import com.aibuilder.user.entity.User;
import com.aibuilder.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthResponse register(RegisterRequest request) {

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new RuntimeException("Email already registered");
        }

        User user = User.builder()
                .name(request.getName())
                .email(request.getEmail().toLowerCase())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.USER)
                .enabled(true)
                .build();

        User savedUser = userRepository.save(user);

        String token = jwtService.generateToken(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getRole().name()
        );

        return buildResponse(savedUser, token);
    }

    public AuthResponse login(LoginRequest request) {

        User user = userRepository.findByEmail(
                request.getEmail().toLowerCase()
        ).orElseThrow(() ->
                new RuntimeException("Invalid email or password")
        );

        if (!passwordEncoder.matches(
                request.getPassword(),
                user.getPassword()
        )) {
            throw new RuntimeException("Invalid email or password");
        }

        String token = jwtService.generateToken(
                user.getId(),
                user.getEmail(),
                user.getRole().name()
        );

        return buildResponse(user, token);
    }

    private AuthResponse buildResponse(
            User user,
            String token
    ) {

        return AuthResponse.builder()
                .accessToken(token)
                .tokenType("Bearer")
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }
}