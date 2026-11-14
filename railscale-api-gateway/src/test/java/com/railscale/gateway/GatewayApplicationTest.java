package com.railscale.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = GatewayApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class GatewayApplicationTest {

    @Autowired
    private RouteLocator routeLocator;

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("Should load gateway routes with resilience filters successfully")
    void testRoutesLoaded() {
        assertThat(routeLocator).isNotNull();
        var routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).hasSize(3);
        assertThat(routes).allMatch(r -> !r.getFilters().isEmpty());
    }

    @Test
    @DisplayName("Should invoke /fallback/users and return 503 Service Unavailable")
    void testUserFallback() {
        webTestClient.get().uri("/fallback/users")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.service").isEqualTo("user-service")
                .jsonPath("$.message").isNotEmpty();
    }

    @Test
    @DisplayName("Should invoke /fallback/bookings and return 503 Service Unavailable")
    void testBookingFallback() {
        webTestClient.post().uri("/fallback/bookings")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.service").isEqualTo("booking-service");
    }

    @Test
    @DisplayName("Should invoke /fallback/search and return 503 Service Unavailable")
    void testSearchFallback() {
        webTestClient.get().uri("/fallback/search")
                .exchange()
                .expectStatus().isEqualTo(HttpStatus.SERVICE_UNAVAILABLE)
                .expectBody()
                .jsonPath("$.status").isEqualTo(503)
                .jsonPath("$.service").isEqualTo("search-service");
    }
}
