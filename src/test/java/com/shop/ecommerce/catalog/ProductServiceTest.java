package com.shop.ecommerce.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.shop.ecommerce.catalog.dto.ProductRequest;
import com.shop.ecommerce.catalog.dto.ProductResponse;
import com.shop.ecommerce.catalog.entity.Category;
import com.shop.ecommerce.catalog.entity.Product;
import com.shop.ecommerce.catalog.repository.CategoryRepository;
import com.shop.ecommerce.catalog.repository.ProductRepository;
import com.shop.ecommerce.catalog.service.ProductService;
import com.shop.ecommerce.common.exception.ConflictException;
import com.shop.ecommerce.common.exception.NotFoundException;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    ProductRepository productRepository;

    @Mock
    CategoryRepository categoryRepository;

    @InjectMocks
    ProductService productService;

    private ProductRequest request() {
        return new ProductRequest("sku-1", "Laptop", "A laptop", new BigDecimal("999.99"), "usd", 1L);
    }

    private Category category() {
        Category category = new Category();
        category.setId(1L);
        category.setName("Computers");
        return category;
    }

    @Test
    void create_normalizesSkuAndCurrency_andActivatesProduct() {
        when(productRepository.existsBySku("SKU-1")).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(category()));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = productService.create(request());

        assertThat(response.sku()).isEqualTo("SKU-1");
        assertThat(response.currency()).isEqualTo("USD");
        assertThat(response.active()).isTrue();
        assertThat(response.categoryId()).isEqualTo(1L);
    }

    @Test
    void create_duplicateSku_throwsConflict() {
        when(productRepository.existsBySku("SKU-1")).thenReturn(true);

        assertThatThrownBy(() -> productService.create(request())).isInstanceOf(ConflictException.class);
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void create_unknownCategory_throwsNotFound() {
        when(productRepository.existsBySku("SKU-1")).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.create(request())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void getById_inactiveProduct_isNotFound() {
        Product product = new Product();
        product.setId(5L);
        product.setActive(false);
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.getById(5L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deactivate_marksProductInactive() {
        Product product = new Product();
        product.setId(5L);
        when(productRepository.findById(5L)).thenReturn(Optional.of(product));

        productService.deactivate(5L);

        assertThat(product.isActive()).isFalse();
    }
}
