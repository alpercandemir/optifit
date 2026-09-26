package com.optifit.exception;

public final class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final int status;
    private final String code;

    public ApiException(int status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public int status() {
        return status;
    }

    public String code() {
        return code;
    }

    public static ApiException badRequest(String message) {
        return new ApiException(400, "INVALID_INPUT", message);
    }

    public static ApiException missing() {
        return new ApiException(404, "NOT_FOUND", "This result is no longer available. Start a new analysis.");
    }
}
