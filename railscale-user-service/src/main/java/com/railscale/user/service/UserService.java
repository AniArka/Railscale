package com.railscale.user.service;

import com.railscale.common.exception.ConflictException;
import com.railscale.common.exception.ResourceNotFoundException;
import com.railscale.user.domain.Role;
import com.railscale.user.domain.User;
import com.railscale.user.repository.UserRepository;
import com.railscale.user.web.dto.RegisterUserRequest;
import com.railscale.user.web.dto.UserResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public UserResponse register(RegisterUserRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new ConflictException("User with email " + request.email() + " already exists");
        }

        String passwordHash = hashPassword(request.password());
        Role role = request.role() != null ? request.role() : Role.USER;

        User user = new User(request.email().toLowerCase().trim(), passwordHash, request.fullName().trim(), role);
        User saved = userRepository.save(user);
        return UserResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public UserResponse getById(UUID id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return UserResponse.fromEntity(user);
    }

    @Transactional(readOnly = true)
    public User getEntityById(UUID id) {
        return userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
