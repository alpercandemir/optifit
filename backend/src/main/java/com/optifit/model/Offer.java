package com.optifit.model;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.Builder;

@Builder(toBuilder = true)
public record Offer(String merchantName, String productUrl, String merchantUrl, BigDecimal price, String currency,
        String availability, Instant checkedAt) {
}
