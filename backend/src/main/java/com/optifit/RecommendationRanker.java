package com.optifit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.optifit.Models.FaceProfile;
import com.optifit.Models.Offer;
import com.optifit.Models.Preferences;
import com.optifit.Models.Product;
import com.optifit.Models.Recommendation;
import com.optifit.Models.Shape;

@Component
class RecommendationRanker {

    private static final Logger log = LoggerFactory.getLogger(RecommendationRanker.class);

    private static final int MAX_RECOMMENDATIONS = 3;
    private static final int SHAPE_MATCH_SCORE = 30;
    private static final int SHAPE_RANK_PENALTY = 4;
    private static final int PREFERENCE_MATCH_SCORE = 8;

    List<Recommendation> rank(List<Product> products, Preferences preferences, FaceProfile profile, boolean demo) {
        List<Product> eligibleProducts = products.stream()
                .filter(product -> product.category() == preferences.category())
                .filter(product -> withinBudget(product, preferences))
                .filter(product -> !"OUT_OF_STOCK".equals(product.availability()))
                .sorted(Comparator.<Product>comparingInt(product -> score(product, preferences, profile)).reversed()
                        .thenComparing(Product::id))
                .toList();
        Map<String, List<Product>> models = new LinkedHashMap<>();
        for (Product product : eligibleProducts) {
            models.computeIfAbsent(modelIdentity(product), ignored -> new ArrayList<>()).add(product);
        }
        if (!demo) {
            long categoryMatches = products.stream().filter(p -> p.category() == preferences.category()).count();
            long budgetMatches = products.stream().filter(p -> p.category() == preferences.category())
                    .filter(p -> withinBudget(p, preferences)).count();
            log.info(
                    "Product ranking: verified={}, wrongCategory={}, budgetOrUnknownPrice={}, outOfStock={}, eligible={}, distinctModels={}, returned={}",
                    products.size(), products.size() - categoryMatches, categoryMatches - budgetMatches,
                    budgetMatches - eligibleProducts.size(), eligibleProducts.size(), models.size(),
                    Math.min(models.size(), MAX_RECOMMENDATIONS));
        }
        return models.values().stream().limit(MAX_RECOMMENDATIONS).map(offers -> recommendation(offers, profile, demo))
                .toList();
    }

    private boolean withinBudget(Product product, Preferences preferences) {
        return preferences.budget() == null || (product.price() != null && "TRY".equals(product.currency())
                && product.price().compareTo(preferences.budget()) <= 0);
    }

    private String modelIdentity(Product product) {
        return product.brand().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "") + ":"
                + product.modelCode().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private Recommendation recommendation(List<Product> products, FaceProfile profile, boolean demo) {
        Product product = products.getFirst();
        String reasonCode = reasonCode(product, profile, demo);
        return new Recommendation(product.id(), product.brand(), product.modelCode(), product.name(),
                product.category(), product.shape(), reasonCode, reason(product.shape(), reasonCode),
                product.attributes(), products.stream().map(this::offer).toList(), product.imageUrl());
    }

    private Offer offer(Product product) {
        return new Offer(product.merchantName(), product.productUrl(), product.merchantUrl(), product.price(),
                product.currency(), product.availability(), product.checkedAt());
    }

    private String reasonCode(Product product, FaceProfile profile, boolean demo) {
        if (demo) {
            return "DEMO";
        }
        return profile.preferredShapes().contains(product.shape()) ? "SHAPE_MATCH" : "STYLE_ALTERNATIVE";
    }

    private String reason(Shape shape, String code) {
        return switch (code) {
            case "DEMO" -> "Example result: explore " + label(shape).toLowerCase(Locale.ROOT)
                    + " frames. This selection is not based on photo analysis.";
            case "SHAPE_MATCH" -> label(shape)
                    + " frames are among the suggested styles for your visible facial contours. Try them on to check fit and comfort.";
            default -> "An alternative style to consider based on your preferences. Try it on to check physical fit.";
        };
    }

    private int score(Product product, Preferences preferences, FaceProfile profile) {
        int shapeIndex = profile.preferredShapes().indexOf(product.shape());
        int score = shapeIndex < 0 ? 0 : SHAPE_MATCH_SCORE - shapeIndex * SHAPE_RANK_PENALTY;
        if (ModelSuggestions.valid(profile.suggestedModels(), preferences.category()).stream()
                .anyMatch(candidate -> ModelSuggestions.matches(candidate, product))) {
            score += 20;
        }
        if (!"ANY".equals(preferences.color()) && preferences.color().equals(product.color())) {
            score += PREFERENCE_MATCH_SCORE;
        }
        if (!"ANY".equals(preferences.style()) && preferences.style().equals(product.style())) {
            score += PREFERENCE_MATCH_SCORE;
        }
        return score;
    }

    static String label(Shape shape) {
        return switch (shape) {
            case ROUND -> "Round";
            case RECTANGULAR -> "Rectangular";
            case CAT_EYE -> "Cat-eye";
            case AVIATOR -> "Aviator";
            case GEOMETRIC -> "Geometric";
            case BROWLINE -> "Browline";
            case UNKNOWN -> "Unknown";
        };
    }
}
