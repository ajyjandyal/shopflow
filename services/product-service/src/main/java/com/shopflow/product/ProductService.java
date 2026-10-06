package com.shopflow.product;

import com.shopflow.common.exception.BadRequestException;
import com.shopflow.common.exception.ForbiddenException;
import com.shopflow.common.exception.ResourceNotFoundException;
import com.shopflow.common.web.PageResponse;
import com.shopflow.product.dto.ProductRequest;
import com.shopflow.product.dto.ProductResponse;
import com.shopflow.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String text, String category,
                                                BigDecimal minPrice, BigDecimal maxPrice,
                                                Pageable pageable) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new BadRequestException("minPrice must not be greater than maxPrice");
        }

        Specification<Product> spec = ProductSpecifications.isActive();
        if (StringUtils.hasText(text)) {
            spec = spec.and(ProductSpecifications.nameContains(text.trim()));
        }
        if (StringUtils.hasText(category)) {
            spec = spec.and(ProductSpecifications.categoryEquals(category.trim()));
        }
        if (minPrice != null) {
            spec = spec.and(ProductSpecifications.priceAtLeast(minPrice));
        }
        if (maxPrice != null) {
            spec = spec.and(ProductSpecifications.priceAtMost(maxPrice));
        }

        return PageResponse.from(productRepository.findAll(spec, pageable), ProductResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        return ProductResponse.from(loadActive(id));
    }

    @Transactional
    public ProductResponse create(ProductRequest request, AuthenticatedUser seller) {
        Product product = new Product(seller.id(), request.name(), request.description(),
                request.category(), request.price(), request.stockQuantity());
        Product saved = productRepository.save(product);
        log.info("Product id={} created by user id={}", saved.getId(), seller.id());
        return ProductResponse.from(saved);
    }

    @Transactional
    public ProductResponse update(Long id, ProductRequest request, AuthenticatedUser user) {
        Product product = loadActive(id);
        requireOwnerOrAdmin(product, user);
        product.updateDetails(request.name(), request.description(), request.category(),
                request.price(), request.stockQuantity());
        // No save() call needed: the entity is managed, so Hibernate's dirty checking
        // writes the changes at commit.
        log.info("Product id={} updated by user id={}", id, user.id());
        return ProductResponse.from(product);
    }

    @Transactional
    public void delete(Long id, AuthenticatedUser user) {
        Product product = loadActive(id);
        requireOwnerOrAdmin(product, user);
        product.deactivate();
        log.info("Product id={} deactivated by user id={}", id, user.id());
    }

    private Product loadActive(Long id) {
        return productRepository.findByIdAndActiveTrue(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Product", id));
    }

    /** Role check alone is not enough: a SELLER may only modify their OWN products. */
    private void requireOwnerOrAdmin(Product product, AuthenticatedUser user) {
        if (!user.isAdmin() && !product.isOwnedBy(user.id())) {
            throw new ForbiddenException("You can only modify your own products");
        }
    }
}
