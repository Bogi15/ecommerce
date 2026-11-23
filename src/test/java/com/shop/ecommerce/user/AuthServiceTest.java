package com.shop.ecommerce.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shop.ecommerce.common.exception.ConflictException;
import com.shop.ecommerce.common.security.JwtService;
import com.shop.ecommerce.user.dto.AuthResponse;
import com.shop.ecommerce.user.dto.LoginRequest;
import com.shop.ecommerce.user.dto.RefreshTokenRequest;
import com.shop.ecommerce.user.dto.RegisterRequest;
import com.shop.ecommerce.user.dto.UserResponse;
import com.shop.ecommerce.user.entity.Role;
import com.shop.ecommerce.user.entity.User;
import com.shop.ecommerce.user.exception.InvalidRefreshTokenException;
import com.shop.ecommerce.user.repository.UserRepository;
import com.shop.ecommerce.user.service.AuthService;
import com.shop.ecommerce.user.service.RefreshTokenService;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    UserRepository userRepository;

    @Mock
    PasswordEncoder passwordEncoder;

    @Mock
    JwtService jwtService;

    @Mock
    RefreshTokenService refreshTokenService;

    @InjectMocks
    AuthService authService;

    @Test
    void register_savesCustomerWithNormalizedEmailAndEncodedPassword() {
        when(userRepository.existsByEmail("ann@shop.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponse response = authService.register(new RegisterRequest("  Ann@Shop.com ", "password123", "Ann"));

        assertThat(response.email()).isEqualTo("ann@shop.com");
        assertThat(response.role()).isEqualTo(Role.CUSTOMER);
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertThat(saved.getValue().getPasswordHash()).isEqualTo("hashed");
    }

    @Test
    void register_duplicateEmail_throwsConflict() {
        when(userRepository.existsByEmail("ann@shop.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(new RegisterRequest("ann@shop.com", "password123", "Ann")))
                .isInstanceOf(ConflictException.class);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void login_wrongPassword_throwsBadCredentials() {
        User user = new User();
        user.setEmail("ann@shop.com");
        user.setPasswordHash("hashed");
        when(userRepository.findByEmail("ann@shop.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("wrong", "hashed")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(new LoginRequest("ann@shop.com", "wrong")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_unknownEmail_throwsBadCredentials() {
        when(userRepository.findByEmail("nobody@shop.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest("nobody@shop.com", "whatever")))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void login_validCredentials_returnsAccessAndRefreshTokens() {
        User user = new User();
        user.setEmail("ann@shop.com");
        user.setPasswordHash("hashed");
        when(userRepository.findByEmail("ann@shop.com")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("password123", "hashed")).thenReturn(true);
        when(jwtService.generateToken(user)).thenReturn("jwt-token");
        when(jwtService.getExpirationSeconds()).thenReturn(900L);
        when(refreshTokenService.issue(user)).thenReturn("refresh-token");
        when(refreshTokenService.getExpirationSeconds()).thenReturn(604800L);

        AuthResponse response = authService.login(new LoginRequest("ann@shop.com", "password123"));

        assertThat(response.accessToken()).isEqualTo("jwt-token");
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(900L);
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.refreshExpiresInSeconds()).isEqualTo(604800L);
    }

    @Test
    void refresh_validToken_returnsNewPair() {
        User user = new User();
        when(refreshTokenService.consume("old-refresh")).thenReturn(user);
        when(jwtService.generateToken(user)).thenReturn("new-jwt");
        when(refreshTokenService.issue(user)).thenReturn("new-refresh");

        AuthResponse response = authService.refresh(new RefreshTokenRequest("old-refresh"));

        assertThat(response.accessToken()).isEqualTo("new-jwt");
        assertThat(response.refreshToken()).isEqualTo("new-refresh");
    }

    @Test
    void refresh_invalidToken_issuesNothing() {
        when(refreshTokenService.consume("bad")).thenThrow(new InvalidRefreshTokenException());

        assertThatThrownBy(() -> authService.refresh(new RefreshTokenRequest("bad")))
                .isInstanceOf(InvalidRefreshTokenException.class);
        verify(refreshTokenService, never()).issue(any(User.class));
    }

    @Test
    void logout_revokesRefreshToken() {
        authService.logout(new RefreshTokenRequest("refresh"));

        verify(refreshTokenService).revoke("refresh");
    }
}
