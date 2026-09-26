package com.optifit;

/**
 * Stable error contract shared by MVC exception handlers and security filters.
 */
record ApiErrorResponse(String code, String message) {
}
