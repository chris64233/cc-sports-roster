package com.chris64233.cc.sportsroster.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class JsonSupport {

    private static final JsonMapper MAPPER = JsonMapper.builder()
            .findAndAddModules()
            .build();

    public static class JsonWriteException extends RuntimeException {
        JsonWriteException(Throwable cause) {
            super(cause);
        }
    }

    private JsonSupport() {
    }

    public static String write(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JacksonException e) {
            throw new JsonWriteException(e);
        }
    }

    public static JsonNode read(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JacksonException e) {
            throw new IllegalStateException(e);
        }
    }

    public static <T> T read(String json, Class<T> type) {
        try {
            return MAPPER.readValue(json, type);
        } catch (JacksonException e) {
            throw new IllegalStateException(e);
        }
    }
}
