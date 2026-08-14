package com.eformworks.signstage.backend.core.logging;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TraceIdProvider {
    public String getTraceId() {
        return UUID.randomUUID().toString();
    }
}
