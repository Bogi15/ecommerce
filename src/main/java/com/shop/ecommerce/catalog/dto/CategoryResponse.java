package com.shop.ecommerce.catalog.dto;

import com.shop.ecommerce.catalog.entity.Category;

public record CategoryResponse(
        Long id,
        String name,
        Long parentId) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName(), category.getParentId());
    }
}
