package com.shop.ecommerce.user.service;

import com.shop.ecommerce.user.entity.RefreshToken;
import com.shop.ecommerce.user.entity.User;
import com.shop.ecommerce.user.exception.InvalidRefreshTokenException;
import com.shop.ecommerce.user.repository.RefreshTokenRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 32;

    private final SecureRandom secureRandom = new SecureRandom();
    private final RefreshTokenRepository refreshTokenRepository;
    private final Duration ttl;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            @Value("${app.jwt.refresh-token-expiration-days}") long expirationDays) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.ttl = Duration.ofDays(expirationDays);
    }

    @Transactional
    public String issue(User user) {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);

        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(hash(rawToken));
        token.setExpiresAt(Instant.now().plus(ttl));
        refreshTokenRepository.save(token);

        return rawToken;
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public User consume(String rawToken) {
        Instant now = Instant.now();
        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (token.isRevoked()) {
            Long userId = token.getUser().getId();
            int revoked = refreshTokenRepository.revokeAllActiveByUserId(userId, now);
            log.warn("Reuse of revoked refresh token for user {}, revoked {} active session(s)", userId, revoked);
            throw new InvalidRefreshTokenException();
        }

        if (token.isExpired(now)) {
            throw new InvalidRefreshTokenException();
        }

        token.setRevokedAt(now);

        return token.getUser();
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> token.setRevokedAt(Instant.now()));
    }

    public long getExpirationSeconds() {
        return ttl.toSeconds();
    }

    private static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
