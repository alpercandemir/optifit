package com.optifit.model;

import java.util.List;
import java.util.Map;

import lombok.Builder;

@Builder(toBuilder = true)
public record Recommendation(String productId, String brand, String modelCode, String name, Category category,
        Shape shape, String reasonCode, String reason, Map<String, String> attributes, List<Offer> offers,
        String imageUrl) {
}
