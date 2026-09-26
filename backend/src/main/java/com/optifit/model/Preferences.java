package com.optifit.model;

import java.math.BigDecimal;
import java.util.List;

import com.optifit.exception.ApiException;

import lombok.Builder;

@Builder(toBuilder = true)
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
