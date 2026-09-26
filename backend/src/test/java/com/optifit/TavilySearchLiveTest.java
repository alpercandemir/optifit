package com.optifit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.optifit.Models.Category;
import com.optifit.Models.FaceProfile;
import com.optifit.Models.Preferences;
import com.optifit.Models.Shape;

/**
 * Explicit opt-in: consumes one basic Tavily search credit per test; never
 * calls Gemini.
 */
@EnabledIfSystemProperty(named = "optifit.live-search", matches = "true")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"optifit.mode=live",
        "server.address=127.0.0.1"})
class TavilySearchLiveTest {
    @Autowired
    WebProductSearch search;

    @Test
    void rectangularSunglassesReturnsVerifiedProducts() {
        var products = search.search(new Preferences(Category.SUNGLASSES, null, "ANY", "ANY"),
                new FaceProfile(true, 1, "", List.of(Shape.RECTANGULAR)));
        System.out.println("Live Tavily verified products: " + products.size());
        products.forEach(product -> System.out.println(product.modelCode() + " " + product.productUrl()));
        assertThat(products).hasSizeGreaterThanOrEqualTo(3);
        assertThat(products).allSatisfy(product -> {
            assertThat(product.category()).isEqualTo(Category.SUNGLASSES);
            assertThat(product.checkedAt()).isNotNull();
        });
    }
    @Test
    void specificModelCandidatesReturnRealProductDetails() {
        var preferences = new Preferences(Category.SUNGLASSES, null, "ANY", "ANY");
        var face = new FaceProfile(true, 1, "", List.of(Shape.RECTANGULAR),
                List.of(new com.optifit.Models.ModelSuggestion("Ray-Ban", "RB2140", Category.SUNGLASSES),
                        new com.optifit.Models.ModelSuggestion("Ray-Ban", "RB2132", Category.SUNGLASSES),
                        new com.optifit.Models.ModelSuggestion("Ray-Ban", "RB4340", Category.SUNGLASSES)));
        var products = search.search(preferences, face);
        System.out.println("Targeted Tavily verified products: " + products.size());
        products.forEach(product -> System.out.println(product.modelCode() + " " + product.productUrl()));
        assertThat(products).isNotEmpty();
        assertThat(products).anyMatch(product -> face.suggestedModels().stream()
                .anyMatch(candidate -> ModelSuggestions.matches(candidate, product)));
        assertThat(products).allSatisfy(product -> {
            assertThat(product.productUrl()).isNotEqualTo(product.merchantUrl());
            assertThat(product.checkedAt()).isNotNull();
        });
    }

}
