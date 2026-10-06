package com.shopflow.product;

import com.shopflow.common.web.PageResponse;
import com.shopflow.common.web.SortValidator;
import com.shopflow.product.dto.ProductRequest;
import com.shopflow.product.dto.ProductResponse;
import com.shopflow.security.AuthenticatedUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.math.BigDecimal;
import java.net.URI;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Products")
public class ProductController {

    private static final Set<String> SORTABLE_FIELDS = Set.of("name", "price", "createdAt");

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @Operation(summary = "Search active products (public). Sort by name, price or createdAt.")
    public PageResponse<ProductResponse> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @ParameterObject @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
            Pageable pageable) {
        SortValidator.requireAllowed(pageable, SORTABLE_FIELDS);
        return productService.search(q, category, minPrice, maxPrice, pageable);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one active product (public)")
    public ProductResponse getById(@PathVariable Long id) {
        return productService.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    @Operation(summary = "Create a product (SELLER or ADMIN)")
    public ResponseEntity<ProductResponse> create(@Valid @RequestBody ProductRequest request,
                                                  @AuthenticationPrincipal AuthenticatedUser user) {
        ProductResponse created = productService.create(request, user);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(created.id()).toUri();
        return ResponseEntity.created(location).body(created);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    @Operation(summary = "Replace a product (owning SELLER or ADMIN)")
    public ProductResponse update(@PathVariable Long id,
                                  @Valid @RequestBody ProductRequest request,
                                  @AuthenticationPrincipal AuthenticatedUser user) {
        return productService.update(id, request, user);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
    @Operation(summary = "Deactivate (soft delete) a product (owning SELLER or ADMIN)")
    public ResponseEntity<Void> delete(@PathVariable Long id,
                                       @AuthenticationPrincipal AuthenticatedUser user) {
        productService.delete(id, user);
        return ResponseEntity.noContent().build();
    }
}
