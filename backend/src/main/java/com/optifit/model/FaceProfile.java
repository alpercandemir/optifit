package com.optifit.model;

import java.util.List;

import lombok.Builder;

@Builder(toBuilder = true)
@lombok.extern.jackson.Jacksonized
public record FaceProfile(boolean usable, int faceCount, String guidance, List<Shape> preferredShapes,
        List<ModelSuggestion> suggestedModels, List<String> suggestedKeywords) {

    public FaceProfile(boolean usable, int faceCount, String guidance, List<Shape> preferredShapes,
            List<ModelSuggestion> suggestedModels) {
        this(usable, faceCount, guidance, preferredShapes, suggestedModels, List.of());
    }

    public FaceProfile(boolean usable, int faceCount, String guidance, List<Shape> preferredShapes) {
        this(usable, faceCount, guidance, preferredShapes, List.of(), List.of());
    }
}
