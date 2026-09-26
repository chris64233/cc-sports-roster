package com.chris64233.cc.sportsroster.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonSupport {

    public static class JsonWriteException extends RuntimeException {
        JsonWriteException(Throwable cause) {
            super(cause);
        }
    }

    private JsonSupport() {
    }

    public static String write(Object value) {
        try {
            return new ObjectMapper().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new JsonWriteException(e);
        }
    }

    public static JsonNode read(String json) {
        try {
            return new ObjectMapper().readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
