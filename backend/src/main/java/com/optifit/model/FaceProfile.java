package com.optifit.model;

import java.util.List;

import lombok.Builder;

@Builder(toBuilder = true)
public record FaceProfile(boolean usable, int faceCount, String guidance, List<Shape> preferredShapes,
        List<ModelSuggestion> suggestedModels) {

    public FaceProfile(boolean usable, int faceCount, String guidance, List<Shape> preferredShapes) {
        this(usable, faceCount, guidance, preferredShapes, List.of());
    }
}
