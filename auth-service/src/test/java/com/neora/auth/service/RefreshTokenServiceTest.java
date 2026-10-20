package com.neora.auth.service;

import com.neora.auth.model.RefreshSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;

class RefreshTokenServiceTest {
    private MongoTemplate mongo;
    private RefreshTokenService tokens;

    @BeforeEach
    void setUp() {
        mongo = mock(MongoTemplate.class);
        tokens = new RefreshTokenService(mongo, Duration.ofDays(30));
    }

    @Test
    void issuePersistsOnlyHashAndExpiry() throws Exception {
        RefreshTokenService.IssuedToken issued = tokens.issue("user-1");
        ArgumentCaptor<RefreshSession> session = ArgumentCaptor.forClass(RefreshSession.class);
        verify(mongo).insert(session.capture());

        assertEquals(43, issued.value().length());
        assertEquals(Duration.ofDays(30).toSeconds(), issued.expiresIn());
        assertEquals("user-1", session.getValue().userId());
        assertNotEquals(issued.value(), session.getValue().id());
        assertEquals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(issued.value().getBytes(StandardCharsets.US_ASCII))), session.getValue().id());
        assertEquals(Duration.ofDays(30), Duration.between(session.getValue().createdAt(), session.getValue().expiresAt()));
    }

    @Test
    void rotationConsumesOnceAndCreatesNewToken() {
        RefreshTokenService.IssuedToken original = tokens.issue("user-1");
        RefreshSession oldSession = new RefreshSession("hash", "user-1", Instant.now(),
                Instant.now().plus(Duration.ofDays(30)), null, null);
        when(mongo.findAndModify(any(Query.class), any(Update.class), any(FindAndModifyOptions.class),
                eq(RefreshSession.class))).thenReturn(oldSession, (RefreshSession) null);

        RefreshTokenService.RotatedToken rotated = tokens.rotate(original.value());
        assertEquals("user-1", rotated.userId());
        assertNotEquals(original.value(), rotated.token().value());
        assertThrows(BadCredentialsException.class, () -> tokens.rotate(original.value()));
        verify(mongo, times(2)).insert(any(RefreshSession.class));
    }

    @Test
    void malformedTokenIsRejectedWithoutDatabaseLookup() {
        assertThrows(BadCredentialsException.class, () -> tokens.rotate("bad"));
        assertThrows(BadCredentialsException.class, () -> tokens.revoke(null));
        verifyNoInteractions(mongo);
    }

    @Test
    void logoutRevokesRefreshToken() {
        String token = tokens.issue("user-1").value();
        tokens.revoke(token);
        verify(mongo).updateFirst(any(Query.class), any(Update.class), eq(RefreshSession.class));
    }
}
