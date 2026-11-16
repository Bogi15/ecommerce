package com.shop.ecommerce.user.exception;

import com.shop.ecommerce.common.exception.UnauthorizedException;

public class InvalidRefreshTokenException extends UnauthorizedException {

    public InvalidRefreshTokenException() {
        super("Refresh token is invalid or expired");
    }
}
