package com.optifit.model;

import java.time.Instant;
import java.util.List;

import lombok.Builder;

@Builder(toBuilder = true)
public record JobView(String jobId, String status, Instant expiresAt, List<Recommendation> recommendations,
        List<String> warnings, boolean demo, String errorCode, String message) {
}
