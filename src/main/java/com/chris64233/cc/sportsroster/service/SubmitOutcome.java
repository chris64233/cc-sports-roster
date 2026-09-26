package com.chris64233.cc.sportsroster.service;

public record SubmitOutcome(boolean replayed, int httpStatus, Object body) {
}
