package com.optifit.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import lombok.Builder;

@Builder(toBuilder = true)
public record Product(String id, String brand, String modelCode, String name, Category category, Shape shape,
        String color, String style, Map<String, String> attributes, String merchantName, String productUrl,
        String merchantUrl, BigDecimal price, String currency, String availability, Instant checkedAt,
        String imageUrl) {
}
