package com.railscale.user.web;

import com.railscale.user.service.UserService;
import com.railscale.user.web.dto.RegisterUserRequest;
import com.railscale.user.web.dto.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterUserRequest request) {
        UserResponse response = userService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponse> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(userService.getById(id));
    }

    @GetMapping("/{id}/bookings")
    public ResponseEntity<List<Map<String, Object>>> getUserBookings(@PathVariable UUID id) {
        // In distributed architecture, bookings are owned by Booking Service.
        // User Service provides an endpoint contract that can be proxied or aggregated.
        return ResponseEntity.ok(List.of());
    }
}
