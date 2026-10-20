package com.neora.notification.config;
import com.neora.shared.security.*; import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*; import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain; import tools.jackson.databind.ObjectMapper;
@Configuration @EnableConfigurationProperties(JwtProperties.class)
public class NotificationSecurityConfig {
    @Bean BearerTokenFilter filter(JwtProperties p,ObjectMapper m){return new BearerTokenFilter(p,m);}
    @Bean SecurityFilterChain chain(HttpSecurity h,BearerTokenFilter f)throws Exception{return ServiceSecurity.authenticated(h,f);}
}
