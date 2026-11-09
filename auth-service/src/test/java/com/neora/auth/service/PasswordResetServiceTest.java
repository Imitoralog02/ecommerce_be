package com.neora.auth.service;

import com.neora.auth.model.PasswordResetChallenge;
import com.neora.auth.model.UserAccount;
import com.neora.auth.repository.UserAccountRepository;
import com.mongodb.client.result.UpdateResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PasswordResetServiceTest {
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final UserAccountRepository users = mock(UserAccountRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final RefreshTokenService refresh = mock(RefreshTokenService.class);
    private final JavaMailSender mail = mock(JavaMailSender.class);
    private final PasswordResetService service = new PasswordResetService(mongo, users, passwords, refresh,
            mail, "a-secret-of-at-least-thirty-two-characters", Duration.ofMinutes(10),
            Duration.ofMinutes(1), "noreply@neora.local");
    private final UserAccount user = new UserAccount("user-1", "user@example.test", "old-hash", "User",
            null, "CUSTOMER", Instant.now(), Instant.now());

    @BeforeEach
    void userExists() {
        when(users.findByEmailIgnoreCase("user@example.test")).thenReturn(Optional.of(user));
    }

    @Test
    void unknownEmailGetsSameResponseWithoutSendingMail() {
        service.request("unknown@example.test");
        verifyNoInteractions(mail, mongo);
    }

    @Test
    void requestStoresOnlyHashedCodeAndSendsEightDigits() {
        service.request(" USER@EXAMPLE.TEST ");
        var update = org.mockito.ArgumentCaptor.forClass(Update.class);
        verify(mongo).findAndModify(any(Query.class), update.capture(),
                any(FindAndModifyOptions.class), eq(PasswordResetChallenge.class));
        var message = org.mockito.ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mail).send(message.capture());
        var eligibility = org.mockito.ArgumentCaptor.forClass(Query.class);
        verify(mongo).findAndModify(eligibility.capture(), any(Update.class),
                any(FindAndModifyOptions.class), eq(PasswordResetChallenge.class));
        assertThat(eligibility.getValue().getQueryObject().toString()).contains("issuedAt", "$lt");
        String body = message.getValue().getText();
        assertThat(body).matches("(?s).*\\b[0-9]{8}\\b.*");
        String code = java.util.regex.Pattern.compile("[0-9]{8}").matcher(body).results()
                .findFirst().orElseThrow().group();
        assertThat(update.getValue().getUpdateObject().toString()).contains("codeHash")
                .doesNotContain(code);
    }

    @Test
    void validCodeChangesPasswordAndRevokesRefreshSessions() {
        when(mongo.findAndModify(any(Query.class), any(Update.class), eq(PasswordResetChallenge.class)))
                .thenReturn(new PasswordResetChallenge(user.id(), "hash", Instant.now(),
                        Instant.now().plusSeconds(600), 0, null));
        when(passwords.encode("NewPassword123")).thenReturn("new-hash");
        when(mongo.updateFirst(any(Query.class), any(Update.class), eq(UserAccount.class)))
                .thenReturn(UpdateResult.acknowledged(1, 1L, null));

        service.confirm("user@example.test", "12345678", "NewPassword123");

        var challenge = org.mockito.ArgumentCaptor.forClass(Query.class);
        verify(mongo).findAndModify(challenge.capture(), any(Update.class), eq(PasswordResetChallenge.class));
        assertThat(challenge.getValue().getQueryObject().toString())
                .contains("codeHash", "expiresAt", "consumedAt", "attempts");
        verify(mongo).updateFirst(any(Query.class), argThat(update ->
                update.getUpdateObject().toString().contains("new-hash")), eq(UserAccount.class));
        verify(refresh).revokeAll(user.id());
    }

    @Test
    void invalidCodeConsumesAnAttemptWithoutChangingPassword() {
        assertThatThrownBy(() -> service.confirm("user@example.test", "00000000", "NewPassword123"))
                .isInstanceOf(IllegalArgumentException.class);
        verify(mongo).updateFirst(any(Query.class), argThat(update ->
                update.getUpdateObject().toString().contains("attempts")), eq(PasswordResetChallenge.class));
        verify(passwords, never()).encode(anyString());
        verifyNoInteractions(refresh);
    }
}
