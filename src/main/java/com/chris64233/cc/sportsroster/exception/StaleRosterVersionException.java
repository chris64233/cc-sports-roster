package com.chris64233.cc.sportsroster.exception;

public class StaleRosterVersionException extends RuntimeException {

    private final long currentVersion;

    public StaleRosterVersionException(long currentVersion) {
        super("名单版本已过期，当前版本为 " + currentVersion);
        this.currentVersion = currentVersion;
    }

    public StaleRosterVersionException(String message) {
        super(message);
        this.currentVersion = -1;
    }

    public long getCurrentVersion() {
        return currentVersion;
    }
}
