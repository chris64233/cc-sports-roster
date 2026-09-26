package com.chris64233.cc.sportsroster.exception;

public class IdempotencyConflictException extends RuntimeException {

    public IdempotencyConflictException() {
        super("相同幂等键提交的名单内容不同");
    }
}
