package com.optifit.exception;

/**
 * Stable error contract shared by MVC exception handlers and security filters.
 */
public record ApiErrorResponse(String code, String message) {
}
