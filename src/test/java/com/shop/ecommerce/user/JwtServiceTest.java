package com.shop.ecommerce.user;

import static org.assertj.core.api.Assertions.assertThat;

import com.shop.ecommerce.common.security.AuthenticatedUser;
import com.shop.ecommerce.common.security.JwtService;
import com.shop.ecommerce.user.entity.Role;
import com.shop.ecommerce.user.entity.User;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-unit-test-secret-0123456789";

    private User adminUser() {
        User user = new User();

        user.setId(7L);
        user.setEmail("admin@shop.com");
        user.setRole(Role.ADMIN);

        return user;
    }

    @Test
    void generatedToken_roundTripsToPrincipal() {
        JwtService jwtService = new JwtService(SECRET, 60);

        Optional<AuthenticatedUser> parsed = jwtService.parse(jwtService.generateToken(adminUser()));

        assertThat(parsed).contains(new AuthenticatedUser(7L, "admin@shop.com", Role.ADMIN));
    }

    @Test
    void tokenSignedWithAnotherSecret_isRejected() {
        String token = new JwtService("another-secret-another-secret-0123456789ab", 60).generateToken(adminUser());

        assertThat(new JwtService(SECRET, 60).parse(token)).isEmpty();
    }

    @Test
    void expiredToken_isRejected() {
        JwtService jwtService = new JwtService(SECRET, -1);

        assertThat(jwtService.parse(jwtService.generateToken(adminUser()))).isEmpty();
    }

    @Test
    void garbage_isRejected() {
        assertThat(new JwtService(SECRET, 60).parse("not-a-jwt")).isEmpty();
    }
}
