// src/main/java/com/project/vts/service/AuthService.java
package com.project.vts.service;

import com.project.vts.dto.request.LoginRequest;
import com.project.vts.dto.request.RegisterRequest;
import com.project.vts.dto.response.AuthResponse;
import com.project.vts.dto.response.UserResponse;
import com.project.vts.exception.BadRequestException;
import com.project.vts.model.User;
import com.project.vts.repository.UserRepository;
import com.project.vts.security.JwtService;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.authenticationManager = authenticationManager;
    }

    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByUsername(req.username())) {
            throw new BadRequestException("Username đã tồn tại");
        }
        if (req.email() != null && !req.email().isBlank() && userRepository.existsByEmail(req.email())) {
            throw new BadRequestException("Email đã được sử dụng");
        }

        User user = User.builder()
                .username(req.username())
                .email(req.email())
                .password(passwordEncoder.encode(req.password()))   // băm mật khẩu
                .displayName(req.displayName())
                .roles(List.of("ROLE_USER"))
                .online(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        user = userRepository.save(user);
        String token = jwtService.generateToken(user.getUsername(), user.getId());
        return new AuthResponse(token, "Bearer", toResponse(user));
    }

    public AuthResponse login(LoginRequest req) {
        try {
            // Spring tự so khớp mật khẩu nhập với hash trong DB qua PasswordEncoder
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(req.username(), req.password()));
        } catch (AuthenticationException e) {
            throw new BadRequestException("Sai username hoặc mật khẩu");
        }

        User user = userRepository.findByUsername(req.username())
                .orElseThrow(() -> new BadRequestException("Sai username hoặc mật khẩu"));
        String token = jwtService.generateToken(user.getUsername(), user.getId());
        return new AuthResponse(token, "Bearer", toResponse(user));
    }

    private UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getUsername(), u.getDisplayName(), u.getEmail(), u.isOnline());
    }
}