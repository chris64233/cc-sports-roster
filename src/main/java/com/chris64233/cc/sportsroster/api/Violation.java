package com.chris64233.cc.sportsroster.api;

import java.util.Map;

public record Violation(
        String code,
        String message,
        Long playerId,
        Map<String, Object> details) {

    public static Violation of(String code, String message) {
        return new Violation(code, message, null, null);
    }

    public static Violation of(String code, String message, Map<String, Object> details) {
        return new Violation(code, message, null, details);
    }

    public static Violation player(String code, String message, Long playerId) {
        return new Violation(code, message, playerId, null);
    }
}
