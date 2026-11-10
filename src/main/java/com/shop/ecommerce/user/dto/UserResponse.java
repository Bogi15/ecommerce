package com.shop.ecommerce.user.dto;

import com.shop.ecommerce.user.entity.Role;
import com.shop.ecommerce.user.entity.User;

public record UserResponse(
        Long id,
        String email,
        String fullName,
        Role role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getRole());
    }
}
