package com.shop.ecommerce.user.service;

import com.shop.ecommerce.common.exception.NotFoundException;
import com.shop.ecommerce.user.dto.UserResponse;
import com.shop.ecommerce.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public UserResponse getById(Long userId) {
        return userRepository.findById(userId)
                .map(UserResponse::from)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));
    }
}
