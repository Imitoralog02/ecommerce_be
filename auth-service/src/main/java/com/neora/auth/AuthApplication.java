package com.neora.auth;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
@SpringBootApplication(scanBasePackages = {"com.neora.auth", "com.neora.shared"})
public class AuthApplication { public static void main(String[] args) { SpringApplication.run(AuthApplication.class, args); } }
