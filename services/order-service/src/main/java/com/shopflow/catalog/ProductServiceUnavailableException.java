package com.shopflow.catalog;

import com.shopflow.common.exception.ServiceUnavailableException;

/**
 * product-service did not give a clear answer: connection refused, timeout, or a 5xx.
 * IMPORTANT: for a write (reserve), we can't know whether it happened or not. The
 * caller must treat the outcome as UNKNOWN, which is why reserve/release are idempotent.
 */
public class ProductServiceUnavailableException extends ServiceUnavailableException {

    public ProductServiceUnavailableException(Throwable cause) {
        super("Product service is temporarily unavailable. Please try again.");
        initCause(cause);
    }
}
