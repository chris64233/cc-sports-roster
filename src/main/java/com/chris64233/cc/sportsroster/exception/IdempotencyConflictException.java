package com.chris64233.cc.sportsroster.exception;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException() {
        super("相同幂等键提交的内容不同");
    }

    public IdempotencyConflictException(String message) {
        super(message);
    }
}
