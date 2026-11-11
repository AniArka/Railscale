package com.railscale.gateway;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = GatewayApplication.class)
@ActiveProfiles("test")
class GatewayApplicationTest {

    @Autowired
    private RouteLocator routeLocator;

    @Test
    @DisplayName("Should load gateway routes successfully")
    void testRoutesLoaded() {
        assertThat(routeLocator).isNotNull();
        var routes = routeLocator.getRoutes().collectList().block();
        assertThat(routes).isNotEmpty();
    }
}
