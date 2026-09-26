package com.chris64233.cc.sportsroster.exception;

public class RegistrationCommitConflictException extends RuntimeException {

    public RegistrationCommitConflictException() {
        super("球员注册在提交时发生并发冲突，请重新读取名单后重试");
    }
}
