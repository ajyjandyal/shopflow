package com.shopflow.common.exception;

import org.springframework.http.HttpStatus;

/** A downstream service could not be reached or failed. Maps to 503 so clients know to retry later. */
public class ServiceUnavailableException extends ApiException {

    public ServiceUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, message);
    }
}
