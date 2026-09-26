package com.optifit.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record ModelSuggestion(String brand, String modelCode, Category category) {
}
