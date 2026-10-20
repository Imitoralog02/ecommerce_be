package com.neora.shared.security;

public record AuthenticatedUser(String id, String email, String role) {}
