package com.shop.ecommerce.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shop.ecommerce.user.entity.RefreshToken;
import com.shop.ecommerce.user.entity.User;
import com.shop.ecommerce.user.exception.InvalidRefreshTokenException;
import com.shop.ecommerce.user.repository.RefreshTokenRepository;
import com.shop.ecommerce.user.service.RefreshTokenService;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    RefreshTokenRepository refreshTokenRepository;

    RefreshTokenService refreshTokenService;

    User user;

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, 7);
        user = new User();
        user.setId(7L);
    }

    private RefreshToken storedToken(Instant expiresAt, Instant revokedAt) {
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setExpiresAt(expiresAt);
        token.setRevokedAt(revokedAt);
        return token;
    }

    @Test
    void issue_storesOnlyHashOfReturnedToken() {
        String raw = refreshTokenService.issue(user);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository).save(saved.capture());
        assertThat(raw).hasSize(43);
        assertThat(saved.getValue().getTokenHash()).hasSize(64).isNotEqualTo(raw);
        assertThat(saved.getValue().getUser()).isSameAs(user);
        assertThat(saved.getValue().getExpiresAt())
                .isBetween(Instant.now().plus(Duration.ofDays(7)).minusSeconds(5), Instant.now().plus(Duration.ofDays(7)));
    }

    @Test
    void issue_returnsDifferentTokensEachTime() {
        assertThat(refreshTokenService.issue(user)).isNotEqualTo(refreshTokenService.issue(user));
    }

    @Test
    void consume_activeToken_revokesItAndReturnsUser() {
        RefreshToken token = storedToken(Instant.now().plusSeconds(60), null);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThat(refreshTokenService.consume("raw")).isSameAs(user);
        assertThat(token.isRevoked()).isTrue();
    }

    @Test
    void consume_unknownToken_throws() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> refreshTokenService.consume("raw")).isInstanceOf(InvalidRefreshTokenException.class);
    }

    @Test
    void consume_expiredToken_throws() {
        when(refreshTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(storedToken(Instant.now().minusSeconds(1), null)));

        assertThatThrownBy(() -> refreshTokenService.consume("raw")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenRepository, never()).revokeAllActiveByUserId(any(), any());
    }

    @Test
    void consume_revokedToken_revokesAllUserSessions() {
        when(refreshTokenRepository.findByTokenHash(anyString()))
                .thenReturn(Optional.of(storedToken(Instant.now().plusSeconds(60), Instant.now().minusSeconds(10))));

        assertThatThrownBy(() -> refreshTokenService.consume("raw")).isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenRepository).revokeAllActiveByUserId(eq(7L), any(Instant.class));
    }

    @Test
    void revoke_activeToken_marksRevoked_unknownTokenIgnored() {
        RefreshToken token = storedToken(Instant.now().plusSeconds(60), null);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token), Optional.empty());

        refreshTokenService.revoke("raw");
        refreshTokenService.revoke("unknown");

        assertThat(token.isRevoked()).isTrue();
    }
}
