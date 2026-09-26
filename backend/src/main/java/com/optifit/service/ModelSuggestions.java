package com.optifit.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;

import com.optifit.model.Category;
import com.optifit.model.ModelSuggestion;
import com.optifit.model.Product;
import com.optifit.util.TextNormalizer;

/**
 * AI candidates guide discovery; only independently verified Products may be
 * offered.
 */
public final class ModelSuggestions {

    // Observed in Atasun's Turkish catalogue; this is not a stock/price guarantee.
    public static final List<String> BRANDS = List.of("Ray-Ban", "Osse", "Inesta", "Mustang");

    private ModelSuggestions() {
    }

    public static List<ModelSuggestion> valid(List<ModelSuggestion> candidates, Category category) {
        if (candidates == null || category == null) {
            return List.of();
        }
        var valid = new LinkedHashMap<String, ModelSuggestion>();
        for (var candidate : candidates) {
            if (candidate == null || candidate.category() != category || candidate.brand() == null
                    || candidate.modelCode() == null || candidate.brand().length() > 60
                    || candidate.modelCode().length() > 30) {
                continue;
            }
            String brand = BRANDS.stream().filter(b -> identity(b).equals(identity(candidate.brand()))).findFirst()
                    .orElse(null);
            String code = candidate.modelCode().replace(" ", "").toUpperCase(Locale.ROOT);
            if (brand == null || !code.matches("[A-Z0-9][A-Z0-9/-]{1,24}") || !code.matches(".*[0-9].*")) {
                continue;
            }
            valid.putIfAbsent(identity(brand) + ":" + identity(code), new ModelSuggestion(brand, code, category));
            if (valid.size() == 3) {
                break;
            }
        }
        return List.copyOf(valid.values());
    }

    public static boolean matches(ModelSuggestion candidate, Product product) {
        return candidate.category() == product.category()
                && identity(candidate.brand()).equals(identity(product.brand()))
                && identity(candidate.modelCode()).equals(identity(product.modelCode()));
    }

    private static String identity(String value) {
        return TextNormalizer.normalize(value).replaceAll("[^a-z0-9]", "");
    }
}
