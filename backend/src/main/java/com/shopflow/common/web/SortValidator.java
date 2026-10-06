package com.shopflow.common.web;

import com.shopflow.common.exception.BadRequestException;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Whitelists sortable fields. Without this, clients could sort by any entity property,
 * including unindexed ones (slow full scans) or internal fields.
 */
public final class SortValidator {

    private SortValidator() {
    }

    public static void requireAllowed(Pageable pageable, Set<String> allowedProperties) {
        for (Sort.Order order : pageable.getSort()) {
            if (!allowedProperties.contains(order.getProperty())) {
                throw new BadRequestException("Sorting by '" + order.getProperty()
                        + "' is not supported. Allowed: " + allowedProperties);
            }
        }
    }
}
