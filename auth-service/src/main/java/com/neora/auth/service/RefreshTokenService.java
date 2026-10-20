package com.neora.auth.service;

import com.neora.auth.model.RefreshSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class RefreshTokenService {
    private final MongoTemplate mongo;
    private final Duration ttl;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(MongoTemplate mongo,
                               @Value("${app.auth.refresh-ttl:P30D}") Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("Thời hạn refresh token phải lớn hơn 0");
        }
        this.mongo = mongo;
        this.ttl = ttl;
    }

    public IssuedToken issue(String userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = Instant.now();
        mongo.insert(new RefreshSession(hash(token), userId, now, now.plus(ttl), null, null));
        return new IssuedToken(token, ttl.toSeconds());
    }

    public RotatedToken rotate(String token) {
        String id = validHash(token);
        Instant now = Instant.now();
        Query active = new Query(Criteria.where("_id").is(id).and("expiresAt").gt(now)
                .and("consumedAt").is(null).and("revokedAt").is(null));
        RefreshSession previous = mongo.findAndModify(active, new Update().set("consumedAt", now),
                FindAndModifyOptions.options().returnNew(false), RefreshSession.class);
        if (previous == null) throw new BadCredentialsException("invalid refresh token");
        return new RotatedToken(previous.userId(), issue(previous.userId()));
    }

    public void revoke(String token) {
        String id = validHash(token);
        mongo.updateFirst(new Query(Criteria.where("_id").is(id).and("consumedAt").is(null)),
                new Update().set("revokedAt", Instant.now()), RefreshSession.class);
    }

    private String validHash(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw new BadCredentialsException("invalid refresh token");
        }
        return hash(token);
    }

    private String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 không khả dụng", ex);
        }
    }

    public record IssuedToken(String value, long expiresIn) {}
    public record RotatedToken(String userId, IssuedToken token) {}
}
