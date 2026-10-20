package com.neora.customer.config;

import com.neora.shared.security.BearerTokenFilter;
import com.neora.shared.security.JwtProperties;
import com.neora.shared.security.ServiceSecurity;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class CustomerSecurityConfig {
    @Bean
    BearerTokenFilter bearerTokenFilter(JwtProperties properties, ObjectMapper mapper) {
        return new BearerTokenFilter(properties, mapper);
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, BearerTokenFilter filter) throws Exception {
        return ServiceSecurity.authenticated(http, filter);
    }
}
