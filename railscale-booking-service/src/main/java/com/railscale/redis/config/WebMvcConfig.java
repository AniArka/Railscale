package com.railscale.redis.config;

import com.railscale.redis.ratelimit.RateLimitInterceptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC configuration for registering application interceptors such as rate limiting and CORS.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Autowired(required = false)
    private RateLimitInterceptor rateLimitInterceptor;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
            .allowedOriginPatterns("*")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
            .allowedHeaders("*")
            .allowCredentials(true);
    }

    /**
     * Registers Spring MVC interceptors.
     *
     * @param registry the interceptor registry
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (rateLimitInterceptor != null) {
            registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/bookings/**");
        }
    }

    /**
     * Returns the configured RateLimitInterceptor.
     *
     * @return the rateLimitInterceptor instance, or null if not present
     */
    public RateLimitInterceptor getRateLimitInterceptor() {
        return rateLimitInterceptor;
    }

    /**
     * Sets the RateLimitInterceptor instance.
     *
     * @param rateLimitInterceptor the rateLimitInterceptor to set
     */
    public void setRateLimitInterceptor(RateLimitInterceptor rateLimitInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
    }
}
