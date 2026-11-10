package com.shop.ecommerce.catalog.service;

import com.shop.ecommerce.catalog.dto.CategoryRequest;
import com.shop.ecommerce.catalog.dto.CategoryResponse;
import com.shop.ecommerce.catalog.entity.Category;
import com.shop.ecommerce.catalog.repository.CategoryRepository;
import com.shop.ecommerce.common.exception.ConflictException;
import com.shop.ecommerce.common.exception.NotFoundException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().trim();

        if (categoryRepository.existsByName(name)) {
            throw new ConflictException("Category already exists: " + name);
        }

        if (request.parentId() != null && !categoryRepository.existsById(request.parentId())) {
            throw new NotFoundException("Parent category not found: " + request.parentId());
        }

        Category category = new Category();
        category.setName(name);
        category.setParentId(request.parentId());

        return CategoryResponse.from(categoryRepository.save(category));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listAll() {
        return categoryRepository.findAll(Sort.by("name")).stream()
                .map(CategoryResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getById(Long id) {
        return categoryRepository.findById(id)
                .map(CategoryResponse::from)
                .orElseThrow(() -> new NotFoundException("Category not found: " + id));
    }
}
