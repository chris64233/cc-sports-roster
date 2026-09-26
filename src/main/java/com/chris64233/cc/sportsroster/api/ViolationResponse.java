package com.chris64233.cc.sportsroster.api;

import java.util.List;

public record ViolationResponse(boolean valid, List<Violation> violations) {

    public static ViolationResponse invalid(List<Violation> violations) {
        return new ViolationResponse(false, violations);
    }
}
