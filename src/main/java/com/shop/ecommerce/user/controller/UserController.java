package com.shop.ecommerce.user.controller;

import com.shop.ecommerce.common.security.AuthenticatedUser;
import com.shop.ecommerce.user.dto.UserResponse;
import com.shop.ecommerce.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Users")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "Get the logged-in user's profile")
    @GetMapping("/me")
    public UserResponse getCurrentUser(@AuthenticationPrincipal AuthenticatedUser principal) {
        return userService.getById(principal.id());
    }
}
