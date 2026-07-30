package com.bionote.domain.record;

public record Decision(boolean allowed, String errorCode, String message) {
    public static Decision allow() {
        return new Decision(true, null, null);
    }

    public static Decision deny(String errorCode, String message) {
        return new Decision(false, errorCode, message);
    }
}
