package com.neora.auth.config;

import com.neora.auth.model.UserAccount;
import com.neora.auth.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Locale;

/** Opt-in local operator account; never exposed through public registration. */
@Component
public class AdminBootstrap implements CommandLineRunner {
    private final UserAccountRepository users;
    private final PasswordEncoder passwords;
    private final String email;
    private final String password;

    public AdminBootstrap(UserAccountRepository users, PasswordEncoder passwords,
                          @Value("${admin.bootstrap.email:}") String email,
                          @Value("${admin.bootstrap.password:}") String password) {
        this.users = users;
        this.passwords = passwords;
        this.email = email;
        this.password = password;
    }

    @Override
    public void run(String... args) {
        if (email.isBlank() && password.isBlank()) return;
        if (email.isBlank() || password.length() < 8 || password.length() > 72) {
            throw new IllegalStateException("Admin bootstrap requires an email and an 8-72 character password");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        var existing = users.findByEmailIgnoreCase(normalized);
        if (existing.isPresent()) {
            if (!"ADMIN".equals(existing.get().role())) {
                throw new IllegalStateException("Admin bootstrap email already belongs to a non-admin user");
            }
            return;
        }
        Instant now = Instant.now();
        users.insert(new UserAccount(null, normalized, passwords.encode(password),
                "NÉORA Admin", null, "ADMIN", now, now));
    }
}
