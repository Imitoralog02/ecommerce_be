package com.neora.auth.service;

import com.neora.auth.model.PasswordResetChallenge;
import com.neora.auth.repository.UserAccountRepository;
import com.neora.shared.error.ConflictException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class PasswordResetService {
    private static final int MAX_ATTEMPTS = 5;
    private final MongoTemplate mongo;
    private final UserAccountRepository users;
    private final PasswordEncoder passwords;
    private final RefreshTokenService refreshTokens;
    private final JavaMailSender mail;
    private final byte[] secret;
    private final Duration ttl;
    private final Duration cooldown;
    private final String sender;
    private final SecureRandom random = new SecureRandom();

    public PasswordResetService(MongoTemplate mongo, UserAccountRepository users, PasswordEncoder passwords,
                                RefreshTokenService refreshTokens, JavaMailSender mail,
                                @Value("${app.auth.reset-secret}") String secret,
                                @Value("${app.auth.reset-ttl:PT10M}") Duration ttl,
                                @Value("${app.auth.reset-cooldown:PT1M}") Duration cooldown,
                                @Value("${app.auth.reset-from:noreply@neora.local}") String sender) {
        if (secret == null || secret.length() < 32 || ttl == null || ttl.isNegative() || ttl.isZero()
                || cooldown == null || cooldown.isNegative() || cooldown.isZero()) {
            throw new IllegalArgumentException("Cấu hình khôi phục mật khẩu không hợp lệ");
        }
        this.mongo = mongo;
        this.users = users;
        this.passwords = passwords;
        this.refreshTokens = refreshTokens;
        this.mail = mail;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttl = ttl;
        this.cooldown = cooldown;
        this.sender = sender;
    }

    public void request(String email) {
        var user = users.findByEmailIgnoreCase(normalize(email)).orElse(null);
        if (user == null) return;
        Instant now = Instant.now();
        String code = String.format(Locale.ROOT, "%08d", random.nextInt(100_000_000));
        String codeHash = hash(user.id(), code);
        Query eligible = Query.query(Criteria.where("_id").is(user.id())
                .and("issuedAt").lt(now.minus(cooldown)));
        Update replace = new Update().set("codeHash", codeHash).set("issuedAt", now)
                .set("expiresAt", now.plus(ttl)).set("attempts", 0).unset("consumedAt");
        try {
            mongo.findAndModify(eligible, replace,
                    FindAndModifyOptions.options().upsert(true).returnNew(true), PasswordResetChallenge.class);
        } catch (DuplicateKeyException recentRequest) {
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(sender);
        message.setTo(user.email());
        message.setSubject("Mã đặt lại mật khẩu NÉORA");
        message.setText("Mã đặt lại mật khẩu của bạn: " + code + "\nMã có hiệu lực "
                + ttl.toMinutes() + " phút và chỉ dùng một lần. Nếu bạn không yêu cầu, hãy bỏ qua email này.");
        try {
            mail.send(message);
        } catch (MailException failed) {
            mongo.remove(Query.query(Criteria.where("_id").is(user.id()).and("codeHash").is(codeHash)),
                    PasswordResetChallenge.class);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chưa thể gửi email khôi phục");
        }
    }

    public void confirm(String email, String code, String newPassword) {
        var user = users.findByEmailIgnoreCase(normalize(email))
                .orElseThrow(() -> new IllegalArgumentException("Mã khôi phục không hợp lệ hoặc đã hết hạn"));
        Instant now = Instant.now();
        PasswordResetChallenge accepted = mongo.findAndModify(Query.query(Criteria.where("_id").is(user.id())
                        .and("codeHash").is(hash(user.id(), code))
                        .and("expiresAt").gt(now).and("consumedAt").is(null)
                        .and("attempts").lt(MAX_ATTEMPTS)),
                new Update().set("consumedAt", now), PasswordResetChallenge.class);
        if (accepted == null) {
            mongo.updateFirst(Query.query(Criteria.where("_id").is(user.id())
                            .and("expiresAt").gt(now).and("consumedAt").is(null)
                            .and("attempts").lt(MAX_ATTEMPTS)),
                    new Update().inc("attempts", 1), PasswordResetChallenge.class);
            throw new IllegalArgumentException("Mã khôi phục không hợp lệ hoặc đã hết hạn");
        }
        var updated = mongo.updateFirst(Query.query(Criteria.where("_id").is(user.id())),
                new Update().set("passwordHash", passwords.encode(newPassword)).set("updatedAt", now),
                com.neora.auth.model.UserAccount.class);
        if (updated.getModifiedCount() == 0) throw new ConflictException("Chưa thể đổi mật khẩu, vui lòng yêu cầu mã mới");
        refreshTokens.revokeAll(user.id());
    }

    private String hash(String userId, String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((userId + ":" + code)
                    .getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("HMAC-SHA256 không khả dụng", ex);
        }
    }

    private String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
