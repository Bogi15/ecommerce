package com.shop.ecommerce.catalog.service;

import com.shop.ecommerce.catalog.dto.ProductRequest;
import com.shop.ecommerce.catalog.dto.ProductResponse;
import com.shop.ecommerce.catalog.entity.Category;
import com.shop.ecommerce.catalog.entity.Product;
import com.shop.ecommerce.catalog.repository.CategoryRepository;
import com.shop.ecommerce.catalog.repository.ProductRepository;
import com.shop.ecommerce.common.dto.PageResponse;
import com.shop.ecommerce.common.exception.ConflictException;
import com.shop.ecommerce.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Transactional
    public ProductResponse create(ProductRequest request) {
        String sku = normalizeSku(request.sku());

        if (productRepository.existsBySku(sku)) {
            throw new ConflictException("SKU already exists: " + sku);
        }

        Category category = findCategory(request.categoryId());

        Product product = new Product();
        apply(product, request, sku, category);
        product.setActive(true);

        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found: " + id));
        String sku = normalizeSku(request.sku());

        if (!sku.equals(product.getSku()) && productRepository.existsBySku(sku)) {
            throw new ConflictException("SKU already exists: " + sku);
        }

        apply(product, request, sku, findCategory(request.categoryId()));

        return ProductResponse.from(product);
    }

    @Transactional
    public void deactivate(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Product not found: " + id));
        product.setActive(false);
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        return productRepository.findById(id)
                .filter(Product::isActive)
                .map(ProductResponse::from)
                .orElseThrow(() -> new NotFoundException("Product not found: " + id));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String query, Long categoryId, Pageable pageable) {
        String term = query == null ? "" : query.trim();
        Page<Product> page = categoryId == null
                ? productRepository.findByActiveTrueAndNameContainingIgnoreCase(term, pageable)
                : productRepository.findByActiveTrueAndCategoryIdAndNameContainingIgnoreCase(
                        categoryId, term, pageable);
        return PageResponse.from(page, ProductResponse::from);
    }

    private Category findCategory(Long categoryId) {
        return categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NotFoundException("Category not found: " + categoryId));
    }

    private void apply(Product product, ProductRequest request, String sku, Category category) {
        product.setSku(sku);
        product.setName(request.name().trim());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setCurrency(request.currency().trim().toUpperCase());
        product.setCategory(category);
    }

    private String normalizeSku(String sku) {
        return sku.trim().toUpperCase();
    }
}
