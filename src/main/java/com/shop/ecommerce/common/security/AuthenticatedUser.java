package com.shop.ecommerce.common.security;

import com.shop.ecommerce.user.entity.Role;

public record AuthenticatedUser(
        Long id,
        String email,
        Role role) {
}
