package com.supervision.exception;

public class BusinessException extends RuntimeException {

    private int code;

    private static final int DEFAULT_CODE = 400;

    public BusinessException(String message) {
        super(message);
        this.code = DEFAULT_CODE;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}
