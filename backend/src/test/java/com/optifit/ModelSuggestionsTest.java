package com.optifit;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.optifit.model.Category;
import com.optifit.model.FaceProfile;
import com.optifit.model.ModelSuggestion;
import com.optifit.model.Preferences;
import com.optifit.model.Recommendation;
import com.optifit.model.Shape;
import com.optifit.search.WebProductSearch;
import com.optifit.service.ModelSuggestions;
import com.optifit.service.RecommendationRanker;

class ModelSuggestionsTest {

    private ModelSuggestion candidate(String brand, String model) {
        return new ModelSuggestion(brand, model, Category.SUNGLASSES);
    }

    @Test
    void constrainsUntrustedCandidatesToDistinctLocalBrandModelHints() {
        var valid = ModelSuggestions.valid(
                Arrays.asList(null, candidate("Unknown", "X123"), candidate("Ray-Ban", "https://evil.example/123"),
                        candidate("Ray-Ban", "Wayfarer"), new ModelSuggestion("Ray-Ban", "RX5228", Category.OPTICAL),
                        candidate("ray ban", "rb 2140"), candidate("Ray-Ban", "RB2140"), candidate("Osse", "OS1234"),
                        candidate("Mustang", "MU1683"), candidate("Inesta", "M018")),
                Category.SUNGLASSES);
        assertThat(valid).containsExactly(candidate("Ray-Ban", "RB2140"), candidate("Osse", "OS1234"),
                candidate("Mustang", "MU1683"));
        assertThat(ModelSuggestions.valid(null, Category.SUNGLASSES)).isEmpty();
        assertThat(ModelSuggestions.valid(valid, null)).isEmpty();
    }

    @Test
    void searchesSpecificCandidatesInTurkishAndFallsBackToShapeWhenNoneAreValid() {
        var prefs = new Preferences(Category.SUNGLASSES, null, "ANY", "ANY");
        var face = new FaceProfile(true, 1, "private guidance", List.of(Shape.RECTANGULAR),
                List.of(candidate("Ray-Ban", "RB2140"), candidate("Osse", "OS1234")));
        assertThat(WebProductSearch.searchQuery(prefs, face))
                .isEqualTo("(\"Ray-Ban RB2140\" OR \"Osse OS1234\") güneş gözlüğü");
        assertThat(WebProductSearch.searchQuery(prefs, new FaceProfile(true, 1, "", List.of(Shape.RECTANGULAR))))
                .isEqualTo("sunglasses rectangular");
    }

    @Test
    void candidatesBoostOnlyVerifiedExactMatchesAndCannotBypassBudgetOrStock() {
        var fixture = new ProductVerificationTest();
        var prefs = new Preferences(Category.SUNGLASSES, new BigDecimal("3000"), "ANY", "ANY");
        var face = new FaceProfile(true, 1, "", List.of(Shape.ROUND), List.of(candidate("Ray-Ban", "RB2140"),
                candidate("Ray-Ban", "RB3025"), candidate("Ray-Ban", "RB3016")));
        var products = List.of(fixture.p("1", "RB4171", BigDecimal.TEN, "IN_STOCK", Category.SUNGLASSES),
                fixture.p("2", "RB2140", BigDecimal.TEN, "IN_STOCK", Category.SUNGLASSES),
                fixture.p("3", "RB3025", BigDecimal.TEN, "OUT_OF_STOCK", Category.SUNGLASSES),
                fixture.p("4", "RB3016", new BigDecimal("4000"), "IN_STOCK", Category.SUNGLASSES));
        assertThat(new RecommendationRanker().rank(products, prefs, face, false)).extracting(Recommendation::modelCode)
                .containsExactly("RB2140", "RB4171");
        assertThat(new RecommendationRanker().rank(List.of(), prefs, face, false)).isEmpty();
        assertThat(ModelSuggestions.matches(candidate("Ray-Ban", "RB214"), products.get(1))).isFalse();
        assertThat(ModelSuggestions.matches(candidate("Osse", "RB2140"), products.get(1))).isFalse();
    }
}
