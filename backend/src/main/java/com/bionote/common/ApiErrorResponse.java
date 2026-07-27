package com.bionote.common;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(Instant timestamp, int status, String code, String message,
                               Map<String, String> fieldErrors, String traceId) {}

