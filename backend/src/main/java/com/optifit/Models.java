package com.optifit;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class Models {

    private Models() {
    }

    public enum Category {

        OPTICAL, SUNGLASSES
    }

    public enum Shape {

        RECTANGULAR, ROUND, CAT_EYE, AVIATOR, GEOMETRIC, BROWLINE, UNKNOWN
    }

    public record Preferences(Category category, BigDecimal budget, String style, String color) {

        public Preferences {
            if (category == null) {
                throw ApiException.badRequest("Select an eyewear category.");
            }
            if (budget != null && (budget.signum() <= 0 || budget.compareTo(new BigDecimal("1000000")) > 0)) {
                throw ApiException.badRequest("Enter a valid budget between 1 and 1,000,000 TRY.");
            }
            if (style == null || !List.of("ANY", "CLASSIC", "MODERN", "BOLD").contains(style)) {
                throw ApiException.badRequest("Invalid style.");
            }
            if (color == null || !List.of("ANY", "BLACK", "BROWN", "GOLD", "CLEAR").contains(color)) {
                throw ApiException.badRequest("Invalid color.");
            }
        }
    }

    public record ModelSuggestion(String brand, String modelCode, Category category) {
    }

    public record FaceProfile(boolean usable, int faceCount, String guidance, List<Shape> preferredShapes,
            List<ModelSuggestion> suggestedModels) {
        public FaceProfile(boolean usable, int faceCount, String guidance, List<Shape> preferredShapes) {
            this(usable, faceCount, guidance, preferredShapes, List.of());
        }
    }

    public record Product(String id, String brand, String modelCode, String name, Category category, Shape shape,
            String color, String style, Map<String, String> attributes, String merchantName, String productUrl,
            String merchantUrl, BigDecimal price, String currency, String availability, Instant checkedAt,
            String imageUrl) {
    }

    public record Recommendation(String productId, String brand, String modelCode, String name, Category category,
            Shape shape, String reasonCode, String reason, Map<String, String> attributes, List<Offer> offers,
            String imageUrl) {
    }

    public record Offer(String merchantName, String productUrl, String merchantUrl, BigDecimal price, String currency,
            String availability, Instant checkedAt) {
    }

    public record Result(List<Recommendation> recommendations, List<String> warnings, boolean demo) {
    }

    public record JobView(String jobId, String status, Instant expiresAt, List<Recommendation> recommendations,
            List<String> warnings, boolean demo, String errorCode, String message) {
    }
}
