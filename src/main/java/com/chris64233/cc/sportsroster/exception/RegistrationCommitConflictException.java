package com.chris64233.cc.sportsroster.exception;

public class RegistrationCommitConflictException extends RuntimeException {

    public RegistrationCommitConflictException() {
        super("球员已被其他球队注册，且本次提交的事务冲突");
    }
}
