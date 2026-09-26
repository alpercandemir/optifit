package com.optifit.model;

import java.util.List;

import lombok.Builder;

@Builder(toBuilder = true)
public record Result(List<Recommendation> recommendations, List<String> warnings, boolean demo) {
}
