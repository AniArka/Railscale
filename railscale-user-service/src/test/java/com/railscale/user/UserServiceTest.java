package com.railscale.user;

import com.railscale.common.exception.ConflictException;
import com.railscale.common.exception.ResourceNotFoundException;
import com.railscale.user.domain.Role;
import com.railscale.user.domain.User;
import com.railscale.user.service.UserService;
import com.railscale.user.web.dto.RegisterUserRequest;
import com.railscale.user.web.dto.UserResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = UserServiceApplication.class)
@ActiveProfiles("test")
@Transactional
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Test
    @DisplayName("Register user successfully with hashed password and role")
    void registerUserSuccessfully() {
        RegisterUserRequest request = new RegisterUserRequest(
            "passenger@railscale.com",
            "securePassword123",
            "Jane Passenger",
            Role.USER
        );

        UserResponse response = userService.register(request);

        assertThat(response.id()).isNotNull();
        assertThat(response.email()).isEqualTo("passenger@railscale.com");
        assertThat(response.fullName()).isEqualTo("Jane Passenger");
        assertThat(response.role()).isEqualTo(Role.USER);

        // Verify underlying entity password is not plain text
        User entity = userService.getEntityById(response.id());
        assertThat(entity.getPasswordHash()).isNotEqualTo("securePassword123");
        assertThat(entity.getPasswordHash()).hasSize(64); // SHA-256 hex length
    }

    @Test
    @DisplayName("Reject registration if email already exists")
    void rejectDuplicateEmail() {
        RegisterUserRequest request = new RegisterUserRequest(
            "dup@railscale.com",
            "password123",
            "User One",
            Role.USER
        );
        userService.register(request);

        assertThatThrownBy(() -> userService.register(request))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    @DisplayName("Throw ResourceNotFoundException when user does not exist")
    void throwWhenUserNotFound() {
        UUID nonExistent = UUID.randomUUID();
        assertThatThrownBy(() -> userService.getById(nonExistent))
            .isInstanceOf(ResourceNotFoundException.class);
    }
}
